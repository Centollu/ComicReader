package com.centollu.comicreader.util

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

object AppPrefs {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_GRID_COLUMNS = "library_grid_columns"
    private const val KEY_SCANNED_FOLDERS = "scanned_folders"
    private const val KEY_RECENT_SEARCHES = "recent_searches"
    private const val MAX_RECENT_SEARCHES = 10

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

    private fun parseRecentSearches(value: String?): List<String> {
        if (value == null) return emptyList()
        return runCatching {
            val arr = JSONArray(value)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i, "").ifBlank { null } }
        }.getOrDefault(emptyList())
    }

    fun getRecentSearches(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return parseRecentSearches(prefs.getString(KEY_RECENT_SEARCHES, null))
    }

    fun addRecentSearch(context: Context, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getRecentSearches(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = parseRecentSearches(prefs.getString(KEY_RECENT_SEARCHES, null))
        val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
        prefs.edit { putString(KEY_RECENT_SEARCHES, JSONArray(updated).toString()) }
        return updated
    }

    fun clearRecentSearches(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_RECENT_SEARCHES) }
    }
}
