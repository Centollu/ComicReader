package com.centollu.comicreader.ui.folders

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centollu.comicreader.data.model.ComicDocument
import java.io.File

private val COMIC_EXTENSIONS = setOf("cbz", "cbr")

private fun isComicFile(file: File): Boolean =
    file.isFile && file.extension.lowercase() in COMIC_EXTENSIONS

private data class StorageVolumeInfo(
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
        } catch (e: Exception) {
            null
        }
    }
}

private fun getStorageVolumes(context: Context): List<StorageVolumeInfo> {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    return try {
        storageManager.storageVolumes.mapNotNull { volume ->
            val dir = getStorageVolumePath(volume)
            if (dir == null || !dir.isDirectory) return@mapNotNull null
            StorageVolumeInfo(
                label = volume.getDescription(context),
                path = dir,
                isRemovable = volume.isRemovable
            )
        }.sortedWith(compareBy({ it.isRemovable }, { it.label.lowercase() }))
    } catch (e: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel,
    onComicSelected: (ComicDocument) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val storageVolumes = remember {
        val volumes = getStorageVolumes(context)
        if (volumes.isEmpty()) {
            listOf(
                StorageVolumeInfo(
                    label = "Almacenamiento interno",
                    path = Environment.getExternalStorageDirectory(),
                    isRemovable = false
                )
            )
        } else volumes
    }

    var currentDir by remember { mutableStateOf<File?>(null) }
    var backStack by remember { mutableStateOf<List<File?>>(emptyList()) }

    val entries = remember(currentDir) {
        if (currentDir == null) {
            emptyList()
        } else {
            runCatching {
                currentDir!!.listFiles()
                    ?.filter { it.isDirectory || isComicFile(it) }
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?.toList()
            }.getOrNull() ?: emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentDir == null) "Carpetas" else currentDir!!.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                navigationIcon = {
                    IconButton(
                        enabled = backStack.isNotEmpty(),
                        onClick = {
                            currentDir = backStack.last()
                            backStack = backStack.dropLast(1)
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Carpeta anterior")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        backStack = emptyList()
                        currentDir = null
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "Ir a inicio")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                currentDir == null -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(storageVolumes, key = { it.path.absolutePath }) { volume ->
                            StorageVolumeRow(
                                volume = volume,
                                onClick = {
                                    backStack = backStack + null
                                    currentDir = volume.path
                                }
                            )
                        }
                    }
                }
                entries.isEmpty() -> {
                    Text(
                        text = "No hay carpetas ni cómics aquí.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries, key = { it.absolutePath }) { file ->
                            if (file.isDirectory) {
                                FolderRow(
                                    folder = file,
                                    onClick = {
                                        backStack = backStack + currentDir
                                        currentDir = file
                                    }
                                )
                            } else {
                                ComicFileRow(
                                    comicFile = file,
                                    onClick = { viewModel.openComic(context, file, onComicSelected) }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isOpeningComic) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Añadiendo a la biblioteca...", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageVolumeRow(volume: StorageVolumeInfo, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = if (volume.isRemovable) Icons.Default.SdCard else Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = if (volume.isRemovable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = volume.label,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = volume.path.absolutePath,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderRow(folder: File, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = folder.name,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ComicFileRow(comicFile: File, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = comicFile.name,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = comicFile.extension.uppercase(),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
