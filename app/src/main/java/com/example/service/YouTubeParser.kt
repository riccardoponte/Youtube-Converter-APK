package com.example.service

import com.example.model.ParsedVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YouTubeParser {

    private val YOUTUBE_URL_REGEX = Pattern.compile(
        "(?:https?:\\/\\/)?(?:www\\.|m\\.|music\\.)?(?:youtube\\.com\\/(?:watch\\?(?:[^&\\n\\r]+\\&)*v=|shorts\\/|embed\\/|v\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts a list of unique YouTube video IDs and standard URLs from any multi-line or raw text
     */
    fun extractVideoIds(text: String): List<String> {
        val matches = LinkedHashSet<String>()
        val matcher = YOUTUBE_URL_REGEX.matcher(text)
        while (matcher.find()) {
            val id = matcher.group(1)
            if (!id.isNullOrBlank()) {
                matches.add(id)
            }
        }

        // Also check if user typed or pasted raw 11-char ID
        if (matches.isEmpty()) {
            val trimmed = text.trim()
            if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                matches.add(trimmed)
            }
        }
        return matches.toList()
    }

    fun buildStandardUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
    fun getThumbnailUrl(videoId: String): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
}

class YouTubeMetadataService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchVideoInfo(videoId: String): ParsedVideoInfo = withContext(Dispatchers.IO) {
        val standardUrl = YouTubeParser.buildStandardUrl(videoId)
        val defaultThumbnail = YouTubeParser.getThumbnailUrl(videoId)
        val oEmbedUrl = "https://www.youtube.com/oembed?url=$standardUrl&format=json"

        try {
            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val title = json.optString("title", "Video $videoId")
                    val author = json.optString("author_name", "YouTube Creator")
                    val authorUrl = json.optString("author_url", "")
                    val thumb = json.optString("thumbnail_url", defaultThumbnail)

                    return@withContext ParsedVideoInfo(
                        videoId = videoId,
                        originalUrl = standardUrl,
                        title = title,
                        author = author,
                        authorUrl = authorUrl,
                        thumbnailUrl = thumb.ifBlank { defaultThumbnail }
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful fallback on network glitch or restricted video
        }

        ParsedVideoInfo(
            videoId = videoId,
            originalUrl = standardUrl,
            title = "Video YouTube ($videoId)",
            author = "YouTube",
            thumbnailUrl = defaultThumbnail
        )
    }
}
