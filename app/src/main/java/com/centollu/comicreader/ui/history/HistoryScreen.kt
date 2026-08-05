package com.centollu.comicreader.ui.history

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.centollu.comicreader.ui.components.VerticalSliderBar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.centollu.comicreader.data.model.ReadingHistoryDocument
import com.centollu.comicreader.util.ComicExtractor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onResumeReading: (comicId: String, pageIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var itemToDelete by remember { mutableStateOf<ReadingHistoryDocument?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Lectura", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    if (uiState.historyItems.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar todo el historial")
                        }
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
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.historyItems.isEmpty()) {
                Text(
                    text = "Aún no has leído ningún cómic.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.historyItems, key = { it._id }) { history ->
                            HistoryCard(
                                context = context,
                                history = history,
                                onResumeClick = { onResumeReading(history.comicId, history.lastPageOpened) },
                                onDeleteClick = { itemToDelete = history }
                            )
                        }
                    }
                    VerticalSliderBar(
                        state = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Eliminar registro") },
            text = { Text("¿Eliminar \"${item.title}\" del historial?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHistoryItem(item._id)
                    itemToDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Borrar historial") },
            text = { Text("¿Eliminar todos los registros de lectura?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearAllDialog = false
                }) {
                    Text("Borrar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun HistoryCard(
    context: Context,
    history: ReadingHistoryDocument,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val coverFile = remember(history.comicId) {
        ComicExtractor.getCoverThumbnailFile(context, history.comicId)
    }

    val progress = if (history.totalPages > 0) {
        (history.lastPageOpened + 1).toFloat() / history.totalPages.toFloat()
    } else 0f

    val relativeTime = DateUtils.getRelativeTimeSpanString(
        history.lastReadTimestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onResumeClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (coverFile != null) {
                    Image(
                        painter = rememberAsyncImagePainter(coverFile),
                        contentDescription = history.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color.LightGray)
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Página ${history.lastPageOpened + 1} de ${history.totalPages}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Leído $relativeTime",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (history.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledIconButton(onClick = onResumeClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reanudar lectura")
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar registro",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
