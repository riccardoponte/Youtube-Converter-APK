package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class FolderInfo(
    val uriString: String? = null,
    val displayName: String = "Cartella predefinita (Music/Movies)"
)

class FolderPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences("tubeconvert_folder_prefs", Context.MODE_PRIVATE)

    fun getSelectedFolder(): FolderInfo {
        val uriStr = prefs.getString("folder_tree_uri", null)
        val name = prefs.getString("folder_display_name", null)
        return if (!uriStr.isNullOrBlank() && !name.isNullOrBlank()) {
            FolderInfo(uriString = uriStr, displayName = name)
        } else {
            FolderInfo(uriString = null, displayName = "Predefinita (Memoria interna/TubeConvert)")
        }
    }

    fun saveSelectedFolder(treeUri: Uri): FolderInfo {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val docFile = DocumentFile.fromTreeUri(context, treeUri)
        val folderName = docFile?.name ?: "Cartella selezionata"

        prefs.edit()
            .putString("folder_tree_uri", treeUri.toString())
            .putString("folder_display_name", folderName)
            .apply()

        return FolderInfo(uriString = treeUri.toString(), displayName = folderName)
    }

    fun resetToDefault(): FolderInfo {
        prefs.edit().clear().apply()
        return FolderInfo(uriString = null, displayName = "Predefinita (Memoria interna/TubeConvert)")
    }
}
