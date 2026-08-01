package com.centollu.comicreader.ui.library

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.util.ComicExtractor
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

    var showScanDialog by remember { mutableStateOf(false) }
    var scanPathInput by remember { mutableStateOf("") }
    var editingComicMetadata by remember { mutableStateOf<ComicDocument?>(null) }

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
                title = { Text("Biblioteca de Cómics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
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
            // Search and Filters
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

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredComics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.searchQuery.isEmpty()) "No hay cómics en la biblioteca.\nPulsa + para añadir uno." else "No se encontraron cómics con el filtro aplicado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
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
            }
        }
    }

    // Dialog: Scan directory
    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Escanear carpeta local") },
            text = {
                OutlinedTextField(
                    value = scanPathInput,
                    onValueChange = { scanPathInput = it },
                    label = { Text("Ruta del directorio") },
                    placeholder = { Text("/sdcard/Download/Comics") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showScanDialog = false
                    if (scanPathInput.isNotEmpty()) {
                        viewModel.scanLocalPath(context, scanPathInput)
                    }
                }) {
                    Text("Escanear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog: Edit Metadata
    editingComicMetadata?.let { comic ->
        var editTitle by remember { mutableStateOf(comic.title) }
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
            onDismiss = { viewModel.closeCoverSelectionDialog() }
        )
    }
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
                    .height(200.dp)
                    .background(Color.DarkGray)
            ) {
                if (coverFile != null) {
                    Image(
                        painter = rememberAsyncImagePainter(coverFile),
                        contentDescription = "Portada de ${comic.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
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
                                        .aspectRatio(0.7f)
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
                                        contentScale = ContentScale.Crop
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
