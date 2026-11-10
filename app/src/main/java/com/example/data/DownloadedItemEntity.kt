package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadedItemEntity(
    @PrimaryKey
    val id: String,
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val format: String, // "M4A" (audio) o "MP4" (video)
    val qualityLabel: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedTimestamp: Long = System.currentTimeMillis()
)
