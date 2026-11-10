package com.example.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.model.AudioQuality
import com.example.model.MediaFormat
import com.example.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Errore con un messaggio già pronto da mostrare all'utente. */
open class DownloadException(message: String) : Exception(message)

/** YouTube ha risposto con il controllo anti-bot (LOGIN_REQUIRED). */
class LoginRequiredException(message: String) : DownloadException(message)

/** Il CDN dei flussi ha rifiutato il trasferimento (tipicamente 403). */
class HttpDownloadException(
    val code: Int,
    val detail: String?,
    message: String
) : DownloadException(message)

/** Famiglia di indirizzi usata per il trasferimento. */
private enum class IpFamily { ANY, IPV4, IPV6 }

/**
 * YouTube lega l'URL di un flusso all'indirizzo IP che l'ha richiesto: se il download esce con
 * una famiglia diversa da quella usata per la richiesta al player, il CDN risponde 403.
 * Questo resolver permette di ripetere il trasferimento forzando IPv4 o IPv6.
 */
private class IpFamilyDns(private val family: IpFamily) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        val filtered = when (family) {
            IpFamily.ANY -> addresses
            IpFamily.IPV4 -> addresses.filterIsInstance<Inet4Address>()
            IpFamily.IPV6 -> addresses.filterIsInstance<Inet6Address>()
        }
        return filtered.ifEmpty { addresses }
    }
}

data class GeneratedMediaResult(
    val pathOrUri: String,
    val fileSize: Long,
    /** Etichetta reale del file prodotto, es. "720p" o "128 kbps". */
    val qualityLabel: String
)

internal data class StreamInfo(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val height: Int,
    val bitrate: Int,
    val contentLength: Long,
    val progressive: Boolean,
    /** User-Agent da usare per scaricare questo flusso. */
    val userAgent: String
)

/** Flussi ottenuti da un client specifico: serve per cambiare client se il CDN blocca. */
internal data class ProfileStreams(
    val profileName: String,
    val streams: List<StreamInfo>
)

/**
 * Scarica i flussi reali restituiti da YouTube e li salva sul dispositivo.
 *
 * L'audio viene salvato in M4A (AAC): è l'unico audio che YouTube fornisce e si riproduce
 * su qualsiasi player Android. I video vengono scaricati come traccia video + traccia audio
 * e uniti senza ricodifica tramite [MediaMuxer].
 */
object MediaFileGenerator {

    private fun buildDownloadClient(family: IpFamily): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .dns(IpFamilyDns(family))
        .build()

    private val anyFamilyClient = buildDownloadClient(IpFamily.ANY)
    private val ipv4Client = buildDownloadClient(IpFamily.IPV4)
    private val ipv6Client = buildDownloadClient(IpFamily.IPV6)

    suspend fun generateMedia(
        context: Context,
        videoId: String,
        title: String,
        artist: String,
        format: MediaFormat,
        audioQuality: AudioQuality,
        videoQuality: VideoQuality,
        customFolderUriString: String?,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ): GeneratedMediaResult = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "tubeconvert_$videoId").apply { mkdirs() }
        try {
            val usedProfiles = mutableSetOf<String>()
            var lastError: Throwable? = null

            // YouTube blocca le URL per client: se il CDN risponde 403 si ripete l'estrazione
            // con il client successivo, invece di riprovare le stesse URL.
            while (true) {
                val fetched = try {
                    YouTubeStreamService.fetchStreamsWithProfile(
                        videoId = videoId,
                        refreshSession = usedProfiles.isNotEmpty(),
                        skipProfiles = usedProfiles.toSet()
                    )
                } catch (e: DownloadException) {
                    lastError = lastError ?: e
                    break
                }

                usedProfiles += fetched.profileName
                try {
                    return@withContext produce(
                        context, fetched.streams, videoId, title, format, audioQuality,
                        videoQuality, customFolderUriString, workDir, onProgress
                    )
                } catch (blocked: HttpDownloadException) {
                    if (blocked.code != 403) throw blocked
                    lastError = blocked
                    clearWorkDir(workDir)
                    delay(1500)
                }
            }

            throw lastError ?: DownloadException(
                "YouTube ha bloccato il download. Riprova tra qualche minuto o cambia rete."
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun clearWorkDir(workDir: File) {
        workDir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun produce(
        context: Context,
        streams: List<StreamInfo>,
        videoId: String,
        title: String,
        format: MediaFormat,
        audioQuality: AudioQuality,
        videoQuality: VideoQuality,
        customFolderUriString: String?,
        workDir: File,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ): GeneratedMediaResult {
        if (streams.isEmpty()) {
            throw DownloadException("YouTube non ha restituito nessun flusso scaricabile per questo video.")
        }
        return when (format) {
            MediaFormat.M4A -> downloadAudio(
                context, streams, videoId, title, audioQuality, customFolderUriString, workDir, onProgress
            )
            MediaFormat.MP4 -> downloadVideo(
                context, streams, videoId, title, videoQuality, customFolderUriString, workDir, onProgress
            )
        }
    }

    private suspend fun downloadAudio(
        context: Context,
        streams: List<StreamInfo>,
        videoId: String,
        title: String,
        audioQuality: AudioQuality,
        customFolderUriString: String?,
        workDir: File,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ): GeneratedMediaResult {
        val directAudio = bestAudio(streams, compact = audioQuality.compact)
        val temporary = File(workDir, "audio.m4a")
        val qualityLabel: String

        if (directAudio != null) {
            downloadStream(directAudio, temporary, 0.02f, 0.9f, onProgress)
            qualityLabel = "${directAudio.bitrate / 1000} kbps"
        } else {
            // Alcuni client offrono solo il flusso progressivo (video e audio insieme):
            // ne estraiamo la traccia audio senza ricodifica.
            val progressive = bestProgressive(streams, Int.MAX_VALUE)
                ?: throw DownloadException("Nessuna traccia audio disponibile per questo video.")
            val source = File(workDir, "sorgente.mp4")
            downloadStream(progressive, source, 0.02f, 0.85f, onProgress)
            onProgress(0.87f, source.length(), source.length(), "Estrazione audio…")
            try {
                extractAudioTrack(source, temporary)
            } catch (e: Exception) {
                throw DownloadException("Impossibile estrarre la traccia audio da questo video.")
            }
            qualityLabel = "${progressive.height}p"
        }

        val saved = saveToDestination(
            context = context,
            source = temporary,
            fileName = buildFileName(title, videoId, MediaFormat.M4A.extension),
            mimeType = MediaFormat.M4A.mimeType,
            customFolderUriString = customFolderUriString,
            progressStart = 0.9f,
            progressEnd = 1f,
            onProgress = onProgress
        )
        return GeneratedMediaResult(saved.first, saved.second, qualityLabel)
    }

    private suspend fun downloadVideo(
        context: Context,
        streams: List<StreamInfo>,
        videoId: String,
        title: String,
        videoQuality: VideoQuality,
        customFolderUriString: String?,
        workDir: File,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ): GeneratedMediaResult {
        val video = bestVideo(streams, videoQuality.height)
        val audio = bestAudio(streams, compact = false)

        if (video != null && audio != null) {
            val videoTrack = File(workDir, "video.mp4")
            val audioTrack = File(workDir, "audio.m4a")
            downloadStream(video, videoTrack, 0.02f, 0.6f, onProgress)
            downloadStream(audio, audioTrack, 0.6f, 0.72f, onProgress)

            val muxed = File(workDir, "unito.mp4")
            val muxedOk = try {
                onProgress(0.75f, videoTrack.length() + audioTrack.length(), 0L, "Unione audio e video…")
                muxTracks(videoTrack, audioTrack, muxed)
                muxed.length() > 0
            } catch (_: Exception) {
                false
            }

            if (muxedOk) {
                val saved = saveToDestination(
                    context = context,
                    source = muxed,
                    fileName = buildFileName(title, videoId, MediaFormat.MP4.extension),
                    mimeType = MediaFormat.MP4.mimeType,
                    customFolderUriString = customFolderUriString,
                    progressStart = 0.8f,
                    progressEnd = 1f,
                    onProgress = onProgress
                )
                return GeneratedMediaResult(saved.first, saved.second, "${video.height}p")
            }
        }

        // Ripiego: flusso progressivo (video e audio già uniti da YouTube, in genere 360p).
        val progressive = bestProgressive(streams, videoQuality.height)
            ?: throw DownloadException("Nessun flusso video compatibile disponibile per questo video.")

        val temporary = File(workDir, "video.mp4")
        downloadStream(progressive, temporary, 0.02f, 0.9f, onProgress)

        val saved = saveToDestination(
            context = context,
            source = temporary,
            fileName = buildFileName(title, videoId, MediaFormat.MP4.extension),
            mimeType = MediaFormat.MP4.mimeType,
            customFolderUriString = customFolderUriString,
            progressStart = 0.9f,
            progressEnd = 1f,
            onProgress = onProgress
        )
        return GeneratedMediaResult(saved.first, saved.second, "${progressive.height}p")
    }

    private fun bestAudio(streams: List<StreamInfo>, compact: Boolean): StreamInfo? {
        val audioTracks = streams.filter { it.mimeType == "audio/mp4" }
        return if (compact) {
            audioTracks.minByOrNull { it.bitrate }
        } else {
            audioTracks.maxByOrNull { it.bitrate }
        }
    }

    private fun bestVideo(streams: List<StreamInfo>, targetHeight: Int): StreamInfo? =
        streams
            .filter { !it.progressive && it.mimeType == "video/mp4" && it.height > 0 && it.height <= targetHeight }
            .maxWithOrNull(compareBy({ it.height }, { it.bitrate }))

    private fun bestProgressive(streams: List<StreamInfo>, targetHeight: Int): StreamInfo? {
        val candidates = streams.filter { it.progressive && it.mimeType == "video/mp4" && it.height > 0 }
        return candidates.filter { it.height <= targetHeight }
            .maxWithOrNull(compareBy({ it.height }, { it.bitrate }))
            ?: candidates.maxByOrNull { it.height }
    }

    private suspend fun downloadStream(
        stream: StreamInfo,
        target: File,
        progressStart: Float,
        progressEnd: Float,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ) {
        // Un 403 su googlevideo dipende quasi sempre dal legame URL <-> IP: si ritenta
        // forzando prima IPv4 poi IPv6, così la famiglia usata corrisponde a quella richiesta.
        var lastError: HttpDownloadException? = null
        for (client in listOf(anyFamilyClient, ipv4Client, ipv6Client)) {
            try {
                downloadOnce(client, stream, target, progressStart, progressEnd, onProgress)
                return
            } catch (e: HttpDownloadException) {
                if (e.code != 403) throw e
                target.delete()
                lastError = e
            }
        }
        throw lastError ?: DownloadException("YouTube ha rifiutato il download.")
    }

    private suspend fun downloadOnce(
        client: OkHttpClient,
        stream: StreamInfo,
        target: File,
        progressStart: Float,
        progressEnd: Float,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ) {
        val request = Request.Builder()
            .url(stream.url)
            .header("User-Agent", stream.userAgent)
            .header("Accept", "*/*")
            .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = responseSnippet(response)
                val base = errorMessageFor(response.code)
                throw HttpDownloadException(
                    code = response.code,
                    detail = snippet,
                    message = if (snippet.isNullOrBlank()) base else "$base Dettaglio: $snippet"
                )
            }
            val body = response.body ?: throw DownloadException("Risposta di YouTube vuota.")
            val total = body.contentLength().takeIf { it > 0 } ?: stream.contentLength
            var written = 0L
            val startedAt = System.currentTimeMillis()
            var lastUpdate = 0L

            FileOutputStream(target).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 250) {
                            lastUpdate = now
                            val fraction = if (total > 0) (written.toFloat() / total).coerceAtMost(1f) else 0f
                            onProgress(
                                progressStart + fraction * (progressEnd - progressStart),
                                written,
                                total,
                                transferSummary(written, now - startedAt)
                            )
                        }
                    }
                }
            }

            onProgress(
                progressEnd,
                written,
                if (total > 0) total else written,
                transferSummary(written, System.currentTimeMillis() - startedAt)
            )
        }
    }

    private fun responseSnippet(response: okhttp3.Response): String? = runCatching {
        response.body?.string()?.replace(Regex("\\s+"), " ")?.trim()?.take(120)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun errorMessageFor(code: Int): String = when (code) {
        403 -> "YouTube ha bloccato il download (403). Se usi una VPN, disattivala e riprova."
        404, 410 -> "Il flusso del video è scaduto ($code). Riavvia il download."
        else -> "YouTube ha rifiutato il download ($code)."
    }

    private fun transferSummary(bytes: Long, elapsedMs: Long): String {
        val megabytes = bytes / 1048576.0
        if (elapsedMs < 500) return String.format(Locale.getDefault(), "%.1f MB", megabytes)
        val kbPerSecond = bytes / 1024.0 / (elapsedMs / 1000.0)
        return String.format(Locale.getDefault(), "%.1f MB • %.0f KB/s", megabytes, kbPerSecond)
    }

    /**
     * Unisce la traccia video e la traccia audio in un solo MP4 senza ricodificarle.
     * Entrambi i file devono essere MP4 (H.264 + AAC), come quelli scelti sopra.
     */
    private fun muxTracks(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val videoTrack = firstTrackWithMime(videoExtractor, "video/")
            val audioTrack = firstTrackWithMime(audioExtractor, "audio/")
            if (videoTrack < 0 || audioTrack < 0) {
                throw DownloadException("Tracce audio/video non valide.")
            }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoOut = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val audioOut = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()
            started = true

            copySamples(videoExtractor, muxer, videoOut)
            copySamples(audioExtractor, muxer, audioOut)
        } finally {
            if (muxer != null) {
                if (started) {
                    runCatching { muxer.stop() }
                }
                runCatching { muxer.release() }
            }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun firstTrackWithMime(extractor: MediaExtractor, mimePrefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) return index
        }
        return -1
    }

    /** Copia la sola traccia audio di un MP4 (es. flusso progressivo) in un file M4A. */
    private fun extractAudioTrack(inputFile: File, outputFile: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(inputFile.absolutePath)
            val audioTrack = firstTrackWithMime(extractor, "audio/")
            if (audioTrack < 0) throw DownloadException("Traccia audio assente nel video.")
            extractor.selectTrack(audioTrack)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val out = muxer.addTrack(extractor.getTrackFormat(audioTrack))
            muxer.start()
            started = true
            copySamples(extractor, muxer, out)
        } finally {
            if (muxer != null) {
                if (started) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
            runCatching { extractor.release() }
        }
    }

    private fun copySamples(extractor: MediaExtractor, muxer: MediaMuxer, trackIndex: Int) {
        val buffer = ByteBuffer.allocateDirect(1 shl 20)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(trackIndex, buffer, info)
            if (!extractor.advance()) break
        }
    }

    private suspend fun saveToDestination(
        context: Context,
        source: File,
        fileName: String,
        mimeType: String,
        customFolderUriString: String?,
        progressStart: Float,
        progressEnd: Float,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ): Pair<String, Long> {
        val total = source.length()

        if (!customFolderUriString.isNullOrBlank()) {
            val directory = DocumentFile.fromTreeUri(context, Uri.parse(customFolderUriString))
                ?: throw DownloadException("La cartella scelta non è più disponibile: selezionane un'altra.")
            val document = directory.createFile(mimeType, fileName)
                ?: throw DownloadException("Impossibile creare il file nella cartella selezionata.")
            try {
                val output = context.contentResolver.openOutputStream(document.uri)
                    ?: throw DownloadException("Impossibile aprire il file di destinazione.")
                output.use { copyWithProgress(source, it, total, progressStart, progressEnd, onProgress) }
                return document.uri.toString() to (document.length().takeIf { it > 0 } ?: total)
            } catch (e: Exception) {
                document.delete()
                throw e
            }
        }

        val defaultDirectory = context.getExternalFilesDir(
            if (mimeType.startsWith("audio/")) android.os.Environment.DIRECTORY_MUSIC
            else android.os.Environment.DIRECTORY_MOVIES
        ) ?: context.filesDir
        if (!defaultDirectory.exists()) defaultDirectory.mkdirs()

        val target = File(defaultDirectory, fileName)
        try {
            FileOutputStream(target).use {
                copyWithProgress(source, it, total, progressStart, progressEnd, onProgress)
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return target.absolutePath to target.length()
    }

    private suspend fun copyWithProgress(
        source: File,
        output: OutputStream,
        total: Long,
        progressStart: Float,
        progressEnd: Float,
        onProgress: suspend (Float, Long, Long, String) -> Unit
    ) {
        var copied = 0L
        source.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                val fraction = if (total > 0) copied.toFloat() / total else 1f
                onProgress(progressStart + fraction * (progressEnd - progressStart), copied, total, "Salvataggio…")
            }
        }
        output.flush()
    }

    private fun buildFileName(title: String, videoId: String, extension: String): String =
        "${sanitizeTitle(title)}_${videoId.take(6)}$extension"

    private fun sanitizeTitle(title: String): String = title
        .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
        .take(80)
        .trim()
        .ifBlank { "Video YouTube" }
}

/**
 * Interroga l'API interna di YouTube ("InnerTube").
 *
 * Due dettagli sono indispensabili per non incappare nel controllo anti-bot
 * ("Sign in to confirm you're not a bot"):
 *  - una versione del client ANDROID_VR recente (le vecchie versioni vengono bloccate);
 *  - un `visitorData` valido, letto dalla pagina del video e inviato in ogni richiesta.
 */
private object YouTubeStreamService {

    private const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val WATCH_ENDPOINT = "https://www.youtube.com/watch?v="
    private const val HOME_ENDPOINT = "https://www.youtube.com/"

    /** User-Agent usati solo per leggere il visitorData dalla pagina web. */
    private val BROWSER_USER_AGENTS = listOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    )

    private val VISITOR_DATA_REGEXES = listOf(
        Regex("\"VISITOR_DATA\":\"([^\"]+)\""),
        Regex("\"visitorData\":\"([^\"]+)\"")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private data class ClientProfile(
        val name: String,
        val version: String,
        val userAgent: String,
        val extra: Map<String, Any>
    )

    /** In ordine di preferenza: il secondo è un ripiego se YouTube cambia le regole. */
    private val profiles = listOf(
        ClientProfile(
            name = "ANDROID_VR",
            version = "1.65.10",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12; en_US) gzip",
            extra = mapOf(
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12",
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3"
            )
        ),
        ClientProfile(
            name = "ANDROID_VR",
            version = "1.62.27",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12; en_US) gzip",
            extra = mapOf(
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12",
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3"
            )
        ),
        ClientProfile(
            name = "ANDROID",
            version = "21.26.364",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            extra = mapOf(
                "androidSdkVersion" to 30,
                "osName" to "Android",
                "osVersion" to "11"
            )
        )
    )

    @Volatile
    private var cachedVisitorData: String? = null

    fun fetchStreams(videoId: String, refreshSession: Boolean = false): List<StreamInfo> =
        fetchStreamsWithProfile(videoId, refreshSession).streams

    /**
     * Estrae i flussi indicando anche il client che li ha forniti, così il chiamante può
     * ripetere l'operazione saltando quel client se il CDN ne rifiuta le URL.
     */
    internal fun fetchStreamsWithProfile(
        videoId: String,
        refreshSession: Boolean = false,
        skipProfiles: Set<String> = emptySet()
    ): ProfileStreams {
        if (refreshSession) cachedVisitorData = null
        var lastError: String? = null

        for (profile in profiles.filterNot { it.name in skipProfiles }) {
            var refreshVisitor = false
            while (true) {
                val attempt = runCatching { requestStreams(videoId, profile, refreshVisitor) }
                val streams = attempt.getOrNull()
                if (!streams.isNullOrEmpty()) return ProfileStreams(profile.name, streams)

                val error = attempt.exceptionOrNull()
                lastError = error?.message ?: lastError

                // Il controllo anti-bot può dipendere da un visitorData scaduto:
                // si riprova una sola volta con un identificativo nuovo.
                if (error is LoginRequiredException && !refreshVisitor) {
                    refreshVisitor = true
                    continue
                }
                break
            }
        }

        throw DownloadException(
            lastError ?: "YouTube non ha restituito flussi scaricabili per questo video."
        )
    }

    private fun requestStreams(
        videoId: String,
        profile: ClientProfile,
        forceVisitorRefresh: Boolean
    ): List<StreamInfo> {
        var visitorData = ensureVisitorData(videoId, forceVisitorRefresh)
        var json = playerRequest(videoId, profile, visitorData)

        if (playabilityStatus(json) != "OK") {
            // YouTube assegna un visitorData in ogni risposta: se la pagina web non lo fornisce
            // (es. schermata di consenso), lo recuperiamo da qui e ripetiamo la richiesta.
            val assigned = json.optJSONObject("responseContext")?.optString("visitorData").orEmpty()
            if (assigned.isNotBlank() && assigned != visitorData) {
                cachedVisitorData = assigned
                visitorData = assigned
                json = playerRequest(videoId, profile, visitorData)
            }
        }

        val status = playabilityStatus(json)
        if (status != "OK") {
            val reason = json.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
            val message = explainPlayability(status, reason)
            if (status == "LOGIN_REQUIRED") throw LoginRequiredException(message)
            throw DownloadException(message)
        }

        val streaming = json.optJSONObject("streamingData")
            ?: throw DownloadException("YouTube non ha restituito flussi scaricabili per questo video.")

        val streams = mutableListOf<StreamInfo>()
        collectStreams(streaming.optJSONArray("formats"), progressive = true, profile, streams)
        collectStreams(streaming.optJSONArray("adaptiveFormats"), progressive = false, profile, streams)
        return streams
    }

    private fun playabilityStatus(json: JSONObject): String =
        json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()

    private fun playerRequest(videoId: String, profile: ClientProfile, visitorData: String?): JSONObject {
        val clientContext = JSONObject()
            .put("clientName", profile.name)
            .put("clientVersion", profile.version)
            .put("hl", "it")
            .put("userAgent", profile.userAgent)
        profile.extra.forEach { (key, value) -> clientContext.put(key, value) }
        visitorData?.let { clientContext.put("visitorData", it) }

        val payload = JSONObject()
            .put("context", JSONObject().put("client", clientContext))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)

        val builder = Request.Builder()
            .url(PLAYER_ENDPOINT)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", profile.userAgent)
            .header("Origin", "https://www.youtube.com")
        visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }

        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException("Impossibile interrogare YouTube (${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw DownloadException("Risposta di YouTube vuota.")
            JSONObject(body)
        }
    }

    private fun explainPlayability(status: String, reason: String): String = when (status) {
        "LOGIN_REQUIRED" ->
            "YouTube chiede un accesso per questo video (controllo anti-bot). Riprova più tardi, anche da rete mobile/Wi-Fi diversa."
        "ERROR" -> "Video non disponibile: ${reason.ifBlank { "motivo sconosciuto" }}"
        "UNPLAYABLE" -> "Questo video non può essere scaricato: ${reason.ifBlank { "non riproducibile" }}"
        "LIVE_STREAM_OFFLINE" -> "La diretta non è in corso."
        else -> "YouTube ha risposto \"$status\"${if (reason.isBlank()) "" else ": $reason"}"
    }

    /**
     * Il `visitorData` identifica la sessione e viene preteso da YouTube insieme al client
     * ANDROID_VR. Viene letto dalla pagina del video e riutilizzato per le richieste successive.
     */
    private fun ensureVisitorData(videoId: String, forceRefresh: Boolean): String? {
        if (forceRefresh) {
            cachedVisitorData = null
        } else {
            cachedVisitorData?.let { return it }
        }

        val pages = listOf("$WATCH_ENDPOINT$videoId", HOME_ENDPOINT)
        for (page in pages) {
            for (browserAgent in BROWSER_USER_AGENTS) {
                val visitor = try {
                    val request = Request.Builder()
                        .url(page)
                        .header("User-Agent", browserAgent)
                        .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                        // Senza questo cookie la pagina di consenso europea nasconde il visitorData.
                        .header("Cookie", "SOCS=CAI")
                        .build()
                    val html = client.newCall(request).execute().use { it.body?.string().orEmpty() }
                    VISITOR_DATA_REGEXES.firstNotNullOfOrNull { regex ->
                        regex.find(html)?.groupValues?.getOrNull(1)
                    }
                } catch (_: Exception) {
                    null
                }
                if (!visitor.isNullOrBlank()) {
                    cachedVisitorData = visitor
                    return visitor
                }
            }
        }
        return null
    }

    private fun collectStreams(
        array: JSONArray?,
        progressive: Boolean,
        profile: ClientProfile,
        into: MutableList<StreamInfo>
    ) {
        if (array == null) return
        for (index in 0 until array.length()) {
            parseStream(array.optJSONObject(index), progressive, profile)?.let { into.add(it) }
        }
    }

    private fun parseStream(item: JSONObject?, progressive: Boolean, profile: ClientProfile): StreamInfo? {
        val obj = item ?: return null
        // I flussi con "signatureCipher" richiedono di decifrare una firma: qui non sono utilizzabili.
        val url = obj.optString("url")
        if (url.isBlank()) return null

        val mimeType = obj.optString("mimeType").substringBefore(';')
        if (mimeType.isBlank()) return null

        return StreamInfo(
            itag = obj.optInt("itag"),
            url = url,
            mimeType = mimeType,
            height = obj.optInt("height"),
            bitrate = obj.optInt("bitrate"),
            contentLength = obj.optString("contentLength").toLongOrNull() ?: 0L,
            progressive = progressive,
            userAgent = profile.userAgent
        )
    }
}
