package com.centollu.comicreader.ui.library

import android.content.Context
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.centollu.comicreader.ui.components.VerticalSliderBar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.util.AppPrefs
import com.centollu.comicreader.util.ComicExtractor
import com.centollu.comicreader.util.StorageVolumeInfo
import com.centollu.comicreader.util.getStorageVolumes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onComicSelected: (ComicDocument) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val filteredComics = remember(uiState) { viewModel.getFilteredComics() }
    val gridColumns = remember { AppPrefs.getGridColumns(context) }

    var showScanDialog by remember { mutableStateOf(false) }
    var editingComicMetadata by remember { mutableStateOf<ComicDocument?>(null) }
    var showSearchFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = getFileName(context, it) ?: "comic.cbz"
            viewModel.addComicFromFile(context, it, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.rescanFolders(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Volver a escanear carpetas")
                    }
                    IconButton(onClick = { showSearchFilters = !showSearchFilters }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar y filtrar")
                    }
                    IconButton(onClick = { showSort = !showSort }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
                    }
                    IconButton(onClick = { showScanDialog = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "Escanear carpeta")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("application/x-cbz", "application/x-cbr", "application/zip", "application/x-rar-compressed", "*/*"))
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Cómic")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search and Filters (ocultos por defecto, se muestran con el botón de búsqueda)
            if (showSearchFilters) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar por título, autor, arco...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filterOptions = listOf(
                            "ALL" to "Todos",
                            "TITLE" to "Título",
                            "AUTHOR" to "Autor",
                            "SERIES" to "Serie",
                            "PUBLISHER" to "Editorial",
                            "ARC" to "Arco"
                        )
                        filterOptions.forEach { (type, label) ->
                            FilterChip(
                                selected = uiState.filterType == type,
                                onClick = { viewModel.updateFilterType(type) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Sort selector (oculto por defecto, se muestra con el botón de ordenación)
            if (showSort) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(
                        text = "Ordenar por:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = uiState.sortType == "NAME",
                            onClick = { viewModel.updateSortType("NAME") },
                            label = { Text("Nombre (A-Z)", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = uiState.sortType == "PATH",
                            onClick = { viewModel.updateSortType("PATH") },
                            label = { Text("Ruta (A-Z)", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = uiState.sortType == "DATE",
                            onClick = { viewModel.updateSortType("DATE") },
                            label = { Text("Añadido (reciente)", fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.isRescanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Volviendo a escanear las carpetas...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filteredComics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.searchQuery.isEmpty()) "No hay cómics en la biblioteca.\nPulsa + para añadir uno." else "No se encontraron cómics con el filtro aplicado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val gridState = rememberLazyGridState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredComics, key = { it._id }) { comic ->
                            ComicGridItem(
                                context = context,
                                comic = comic,
                                onComicClick = { onComicSelected(comic) },
                                onChangeCoverClick = { viewModel.openCoverSelectionDialog(context, comic) },
                                onEditMetadataClick = { editingComicMetadata = comic },
                                onDeleteClick = { viewModel.deleteComic(comic._id) }
                            )
                        }
                    }
                    VerticalSliderBar(
                        state = gridState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    // Dialog: Scan directory
    if (showScanDialog) {
        FolderPickerDialog(
            onConfirm = { folder ->
                showScanDialog = false
                viewModel.scanLocalPath(context, folder.absolutePath)
            },
            onDismiss = { showScanDialog = false }
        )
    }

    // Dialog: Edit Metadata
    editingComicMetadata?.let { comic ->
        var editTitle by remember { mutableStateOf(comic.title) }
        var editIssueNumber by remember { mutableStateOf(comic.issueNumber?.toString() ?: "") }
        var editSeries by remember { mutableStateOf(comic.series) }
        var editAuthor by remember { mutableStateOf(comic.authors) }
        var editPublisher by remember { mutableStateOf(comic.publisher) }
        var editArc by remember { mutableStateOf(comic.storyArc) }

        AlertDialog(
            onDismissRequest = { editingComicMetadata = null },
            title = { Text("Editar Metadatos del Cómic") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Título") })
                    OutlinedTextField(
                        value = editIssueNumber,
                        onValueChange = { editIssueNumber = it },
                        label = { Text("Número (opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(value = editSeries, onValueChange = { editSeries = it }, label = { Text("Serie") })
                    OutlinedTextField(value = editAuthor, onValueChange = { editAuthor = it }, label = { Text("Autor(es)") })
                    OutlinedTextField(value = editPublisher, onValueChange = { editPublisher = it }, label = { Text("Editorial") })
                    OutlinedTextField(value = editArc, onValueChange = { editArc = it }, label = { Text("Arco Argumental") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateComicMetadata(
                        comic._id,
                        editTitle,
                        editIssueNumber.trim().toIntOrNull(),
                        editSeries,
                        editAuthor,
                        editPublisher,
                        editArc
                    )
                    editingComicMetadata = null
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingComicMetadata = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog: Select Cover Image
    if (uiState.selectedComicForCoverPicker != null) {
        CoverSelectorDialog(
            uiState = uiState,
            onCoverSelected = { selectedFile ->
                viewModel.setSelectedCover(context, selectedFile)
            },
            onLoadAll = { viewModel.loadAllCoverPages(context) },
            onDismiss = { viewModel.closeCoverSelectionDialog() }
        )
    }
}

@Composable
fun FolderPickerDialog(
    initialPath: String = Environment.getExternalStorageDirectory().absolutePath,
    onConfirm: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // null = nivel raíz con el listado de almacenamientos (interno y SD)
    var currentDir by remember { mutableStateOf<File?>(null) }
    var backStack by remember { mutableStateOf<List<File?>>(emptyList()) }
    var manualMode by remember { mutableStateOf(false) }
    var manualPath by remember { mutableStateOf(initialPath) }

    val storageVolumes = remember {
        val volumes = getStorageVolumes(context)
        volumes.ifEmpty {
            listOf(
                StorageVolumeInfo(
                    label = "Almacenamiento interno",
                    path = Environment.getExternalStorageDirectory(),
                    isRemovable = false
                )
            )
        }
    }

    val subdirs = remember(currentDir) {
        currentDir?.let { dir ->
            runCatching {
                dir.listFiles()
                    ?.filter { it.isDirectory && it.canRead() }
                    ?.sortedBy { it.name.lowercase() }
            }.getOrNull() ?: emptyList()
        } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escanear carpeta local") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Modo: ${if (manualMode) "manual" else "explorador"}", fontSize = 12.sp)
                    TextButton(onClick = { manualMode = !manualMode }) {
                        Text(if (manualMode) "Explorar carpetas" else "Introducir ruta manualmente")
                    }
                }

                if (manualMode) {
                    OutlinedTextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = { Text("Ruta del directorio") },
                        placeholder = { Text(Environment.getExternalStorageDirectory().absolutePath) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            enabled = currentDir != null,
                            onClick = {
                                currentDir = backStack.lastOrNull()
                                backStack = backStack.dropLast(1)
                            }
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Subir")
                        }
                        Text(
                            text = currentDir?.absolutePath ?: "Almacenamiento",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (currentDir == null) {
                        if (storageVolumes.isEmpty()) {
                            Text(
                                text = "No hay almacenamientos accesibles.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(300.dp)) {
                                items(storageVolumes, key = { it.path.absolutePath }) { volume ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                backStack = listOf(null)
                                                currentDir = volume.path
                                            }
                                            .padding(horizontal = 8.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (volume.isRemovable) Icons.Default.SdCard else Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = if (volume.isRemovable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = volume.label,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = volume.path.absolutePath,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (subdirs.isEmpty()) {
                        Text(
                            text = "No hay subcarpetas accesibles.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(subdirs, key = { it.absolutePath }) { dir ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            backStack = backStack + currentDir
                                            currentDir = dir
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dir.name,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selected = if (manualMode) File(manualPath.trim()) else currentDir
                if (selected != null && selected.isDirectory) {
                    onConfirm(selected)
                }
            }) {
                Text("Escanear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ComicGridItem(
    context: Context,
    comic: ComicDocument,
    onComicClick: () -> Unit,
    onChangeCoverClick: () -> Unit,
    onEditMetadataClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val coverFile = remember(comic.coverFilename, comic._id) {
        ComicExtractor.getCoverThumbnailFile(context, comic._id)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onComicClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.66f)
                    .background(Color.DarkGray)
            ) {
                if (coverFile != null) {
                    Image(
                        painter = rememberAsyncImagePainter(coverFile),
                        contentDescription = "Portada de ${comic.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cambiar Portada") },
                            onClick = {
                                menuExpanded = false
                                onChangeCoverClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar Metadatos") },
                            onClick = {
                                menuExpanded = false
                                onEditMetadataClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }

                // Page count badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${comic.pageCount} pág.",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = comic.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (comic.issueNumber != null) {
                    Text(
                        text = "Nº ${comic.issueNumber}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (comic.series.isNotEmpty()) {
                    Text(
                        text = comic.series,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = File(comic.filePath).name,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CoverSelectorDialog(
    uiState: LibraryUiState,
    onCoverSelected: (File) -> Unit,
    onLoadAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Portada") },
        text = {
            if (uiState.isExtractingForCover) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Extrayendo imágenes del cómic...")
                    }
                }
            } else {
                val extracted = uiState.extractedComicResult
                if (extracted != null && extracted.pageFiles.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Toca la imagen que deseas establecer como portada:",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(extracted.pageFiles) { file ->
                                val isCurrentCover = file.name == extracted.coverFilename
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.66f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isCurrentCover) 3.dp else 1.dp,
                                            color = if (isCurrentCover) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onCoverSelected(file) }
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(file),
                                        contentDescription = file.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    if (isCurrentCover) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        ) {
                                            Text(
                                                text = "Portada",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (extracted.isPartial) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = onLoadAll,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cargar todas las páginas restantes")
                            }
                        }
                    }
                } else {
                    Text("No se pudieron extraer imágenes del archivo.")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun getFileName(context: Context, uri: android.net.Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}
