package com.example.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.lastIndex) {
            size /= 1024
            unitIndex++
        }
        return "%.1f %s".format(Locale.getDefault(), size, units[unitIndex])
    }

    fun formatDuration(ms: Int): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
    }

    fun formatTimestamp(timeMs: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }

    fun getPlayableUri(context: Context, pathOrUri: String): Uri? {
        return try {
            if (pathOrUri.startsWith("content://")) {
                Uri.parse(pathOrUri)
            } else {
                val file = File(pathOrUri)
                if (!file.exists()) return null
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openMediaFile(context: Context, pathOrUri: String, mimeType: String) {
        val uri = getPlayableUri(context, pathOrUri)
        if (uri == null) {
            Toast.makeText(context, "File non trovato sul dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Grant explicit read permissions to resolving apps
        try {
            val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                context.grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {}

        try {
            context.startActivity(Intent.createChooser(intent, "Apri video con"))
        } catch (_: Exception) {
            Toast.makeText(context, "Nessun player trovato per questo file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMediaFile(context: Context, pathOrUri: String, mimeType: String, title: String) {
        val uri = getPlayableUri(context, pathOrUri)
        if (uri == null) {
            Toast.makeText(context, "File non disponibile per la condivisione", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Scaricato con TubeConvert: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(shareIntent, "Condividi file"))
        } catch (_: Exception) {
            Toast.makeText(context, "Impossibile condividere il file", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteLocalFile(context: Context, pathOrUri: String): Boolean {
        return try {
            if (pathOrUri.startsWith("content://")) {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(pathOrUri))
                doc?.delete() ?: false
            } else {
                val file = File(pathOrUri)
                if (file.exists()) file.delete() else true
            }
        } catch (_: Exception) {
            false
        }
    }
}
