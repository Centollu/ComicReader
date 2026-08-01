package com.centollu.comicreader.util

import android.content.Context

object AppPrefs {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_GRID_COLUMNS = "library_grid_columns"

    fun getGridColumns(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(1, 6)
    }

    fun setGridColumns(context: Context, columns: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_GRID_COLUMNS, columns.coerceIn(1, 6))
            .apply()
    }
}
