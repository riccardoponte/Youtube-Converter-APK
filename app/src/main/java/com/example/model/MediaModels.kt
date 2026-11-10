package com.example.model

enum class MediaFormat(val label: String, val extension: String, val mimeType: String) {
    /**
     * Audio AAC in contenitore M4A: è l'unico audio che YouTube fornisce (max ~128 kbps).
     * Un vero MP3 richiederebbe un encoder sul dispositivo; l'M4A è supportato da Android
     * e da tutti i player più comuni.
     */
    M4A("Audio M4A", ".m4a", "audio/mp4"),
    MP4("Video MP4", ".mp4", "video/mp4");

    companion object {
        /** Riconosce anche i download salvati dalle vecchie versioni con formato "MP3". */
        fun isAudioFormat(storedFormat: String): Boolean =
            storedFormat.equals("M4A", ignoreCase = true) || storedFormat.equals("MP3", ignoreCase = true)
    }
}

enum class AudioQuality(val label: String, val subtitle: String, val compact: Boolean = false) {
    BEST("128 kbps", "Qualità originale AAC"),
    LIGHT("48 kbps", "File più leggero", compact = true)
}

enum class VideoQuality(val label: String, val subtitle: String, val height: Int) {
    FHD_1080("1080p", "Full HD", 1080),
    HD_720("720p", "HD", 720),
    SD_480("480p", "Standard", 480),
    COMPACT_360("360p", "Economico", 360)
}

enum class TaskStatus {
    QUEUED,
    FETCHING_METADATA,
    CONVERTING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class ParsedVideoInfo(
    val videoId: String,
    val originalUrl: String,
    val title: String,
    val author: String,
    val authorUrl: String = "",
    val thumbnailUrl: String
)

data class ConversionTask(
    val id: String,
    val videoId: String,
    val originalUrl: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val format: MediaFormat,
    val audioQuality: AudioQuality = AudioQuality.BEST,
    val videoQuality: VideoQuality = VideoQuality.HD_720,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f, // 0f to 1f
    val speedText: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val outputFilePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
