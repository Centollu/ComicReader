package com.centollu.comicreader.util

import android.content.Context
import androidx.core.content.edit

object AppPrefs {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_GRID_COLUMNS = "library_grid_columns"
    private const val KEY_SCANNED_FOLDERS = "scanned_folders"

    fun getGridColumns(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(1, 6)
    }

    fun setGridColumns(context: Context, columns: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_GRID_COLUMNS, columns.coerceIn(1, 6))
            }
    }

    fun getScannedFolders(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SCANNED_FOLDERS, emptySet())?.toList() ?: emptyList()
    }

    fun addScannedFolder(context: Context, folderPath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val folders = prefs.getStringSet(KEY_SCANNED_FOLDERS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        folders.add(folderPath)
        prefs.edit { putStringSet(KEY_SCANNED_FOLDERS, folders) }
    }
}
