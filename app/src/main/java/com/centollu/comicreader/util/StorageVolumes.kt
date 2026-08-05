package com.centollu.comicreader.util

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class StorageVolumeInfo(
    val label: String,
    val path: File,
    val isRemovable: Boolean
)

private fun getStorageVolumePath(volume: StorageVolume): File? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        volume.directory
    } else {
        try {
            val path = volume.javaClass.getMethod("getPath").invoke(volume) as? String
            if (path != null) File(path) else null
        } catch (_: Exception) {
            null
        }
    }
}

fun getStorageVolumes(context: Context): List<StorageVolumeInfo> {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    return try {
        storageManager.storageVolumes.mapNotNull { volume ->
            val directory = getStorageVolumePath(volume)
            if (directory == null || !directory.isDirectory) return@mapNotNull null
            StorageVolumeInfo(
                label = volume.getDescription(context),
                path = directory,
                isRemovable = volume.isRemovable
            )
        }.sortedWith(compareBy({ it.isRemovable }, { it.label.lowercase() }))
    } catch (_: Exception) {
        emptyList()
    }
}