package com.centollu.comicreader.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URL

data class NfsServerConfig(
    val serverIp: String = "",
    val exportPath: String = "",
    val isEnabled: Boolean = false
)

object NfsManager {

    private const val PREF_NAME = "nfs_config"
    private const val KEY_IP = "server_ip"
    private const val KEY_PATH = "export_path"
    private const val KEY_ENABLED = "is_enabled"

    fun saveConfig(context: Context, config: NfsServerConfig) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_IP, config.serverIp)
            .putString(KEY_PATH, config.exportPath)
            .putBoolean(KEY_ENABLED, config.isEnabled)
            .apply()
    }

    fun getConfig(context: Context): NfsServerConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return NfsServerConfig(
            serverIp = prefs.getString(KEY_IP, "") ?: "",
            exportPath = prefs.getString(KEY_PATH, "") ?: "",
            isEnabled = prefs.getBoolean(KEY_ENABLED, false)
        )
    }

    fun getInputStreamForPath(path: String): InputStream? {
        return try {
            if (path.startsWith("nfs://") || path.startsWith("http://") || path.startsWith("https://")) {
                val url = URL(path)
                url.openStream()
            } else {
                val file = File(path)
                if (file.exists()) FileInputStream(file) else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun scanLocalDirectory(directory: File): List<File> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        directory.walkTopDown()
            .filter { it.isFile && (it.extension.lowercase() == "cbz" || it.extension.lowercase() == "cbr" || it.extension.lowercase() == "pdf") }
            .toList()
    }
}
