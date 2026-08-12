package com.centollu.comicreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import coil.compose.rememberAsyncImagePainter
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    comicId: String,
    initialPageIndex: Int = 0,
    onBackClick: () -> Unit,
    onNextComic: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(comicId) {
        viewModel.loadComic(context, comicId, initialPageIndex)
    }

    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.errorMessage ?: "Error", color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackClick) {
                    Text("Volver")
                }
            }
        } else if (uiState.pageFiles.isEmpty()) {
            // Aún no hay ninguna página extraída
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Descomprimiendo cómic en la caché...", color = Color.White)
            }
        } else {
            var scale by remember(comicId) { mutableStateOf(1f) }
            var offsetX by remember(comicId) { mutableStateOf(0f) }
            var offsetY by remember(comicId) { mutableStateOf(0f) }

            val pagerState = rememberPagerState(
                initialPage = uiState.currentPageIndex.coerceIn(0, uiState.pageFiles.lastIndex),
                pageCount = { uiState.pageFiles.size }
            )
            val scope = rememberCoroutineScope()

            LaunchedEffect(pagerState.currentPage) {
                viewModel.onPageChanged(pagerState.currentPage)
            }

            // Al terminar la extracción, saltar a la página de reanudación si procede
            LaunchedEffect(uiState.isLoading, uiState.currentPageIndex) {
                if (!uiState.isLoading && uiState.currentPageIndex in uiState.pageFiles.indices) {
                    if (pagerState.currentPage != uiState.currentPageIndex) {
                        pagerState.scrollToPage(uiState.currentPageIndex)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1f,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val imageFile = uiState.pageFiles[pageIndex]

                ZoomableImage(
                    imageFile = imageFile,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onScaleChange = { scale = it },
                    onOffsetChange = { x, y ->
                        offsetX = x
                        offsetY = y
                    },
                    onPageBack = {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    },
                    onPageForward = {
                        if (pagerState.currentPage < pagerState.pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    onToggleControls = { showControls = !showControls },
                    onZoom = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                    scope = scope
                )
            }

            // Botón "siguiente" al llegar a la última página: abre el siguiente cómic ordenado por ruta
            if (!uiState.isLoading && uiState.nextComic != null &&
                pagerState.currentPage == pagerState.pageCount - 1
            ) {
                Surface(
                    onClick = { uiState.nextComic?.let { onNextComic(it._id) } },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Siguiente",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Indicador de extracción en curso
            if (uiState.isLoading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Extrayendo páginas... ${uiState.pageFiles.size}",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Top Bar Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = buildString {
                                append(uiState.comic?.title ?: "")
                                uiState.comic?.issueNumber?.let { append(" #$it") }
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                )
            }

            // Bottom Page Indicator Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Página ${pagerState.currentPage + 1} / ${uiState.pageFiles.size}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ZoomableImage(
    imageFile: File,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (x: Float, y: Float) -> Unit,
    onPageBack: () -> Unit,
    onPageForward: () -> Unit,
    onToggleControls: () -> Unit,
    onZoom: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    // rememberUpdatedState: evita que el gesto lea valores "stale" al recomponer
    val currentScale by rememberUpdatedState(scale)
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageFile) {
                val touchSlop = viewConfiguration.touchSlop * 2
                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                val cornerRadius = size.width * 0.30f
                val centerRadius = size.width * 0.30f
                var lastTapAt = 0L
                var lastTap: Offset? = null

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downId = down.id
                    var upPos: Offset? = null
                    var upTime = 0L
                    var isTap = true

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }

                        when (pressedChanges.size) {
                            0 -> {
                                val up = event.changes.firstOrNull { it.id == downId }
                                if (up != null) {
                                    upPos = up.position
                                    upTime = up.uptimeMillis
                                    up.consume()
                                }
                                break
                            }
                            1 -> {
                                val change = event.changes.firstOrNull { it.id == downId }
                                    ?: pressedChanges.firstOrNull()
                                    ?: break
                                if (currentScale > 1f) {
                                    // Con zoom: un dedo desplaza la página sin cambiar de página
                                    val pan = change.positionChange()
                                    change.consume()
                                    onOffsetChange(currentOffsetX + pan.x, currentOffsetY + pan.y)
                                    isTap = false
                                } else {
                                    // Sin zoom: un dedo deja que el pager cambie de página
                                    if (change.positionChange().getDistance() > touchSlop) {
                                        isTap = false
                                    }
                                }
                            }
                            else -> {
                                // Pinch (2 o más dedos): agrandar / reducir y mover
                                isTap = false
                                try {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    // Error handling: ignora deltas no finitos (NaN/∞) que provocan saltos
                                    if (!zoomChange.isFinite() || !panChange.x.isFinite() || !panChange.y.isFinite()) {
                                        continue
                                    }
                                    event.changes.fastForEach { it.consume() }
                                    val newScale = (currentScale * zoomChange).coerceIn(1f, 8f)
                                    onScaleChange(newScale)
                                    onOffsetChange(currentOffsetX + panChange.x, currentOffsetY + panChange.y)
                                } catch (e: Exception) {
                                    // Error handling: ignora eventos de pinch inválidos y sigue el gesto
                                }
                            }
                        }
                    }

                    val up = upPos
                    if (isTap && up != null) {
                        val upDy = size.height - up.y
                        val inBottomLeft =
                            (up.x * up.x + upDy * upDy) <= cornerRadius * cornerRadius
                        val inBottomRight =
                            ((size.width - up.x) * (size.width - up.x) + upDy * upDy) <= cornerRadius * cornerRadius
                        val dxCenter = up.x - size.width / 2f
                        val dyCenter = up.y - size.height / 2f
                        val inCenter =
                            (dxCenter * dxCenter + dyCenter * dyCenter) <= centerRadius * centerRadius
                        val zoomed = currentScale > 1f

                        val isDoubleTap = lastTap != null &&
                            (upTime - lastTapAt) <= doubleTapTimeout
                        lastTapAt = upTime
                        lastTap = up

                        when {
                            // Cambio de página por esquinas solo cuando NO hay zoom
                            !zoomed && inBottomLeft -> {
                                lastTap = null
                                onPageBack()
                            }
                            !zoomed && inBottomRight -> {
                                lastTap = null
                                onPageForward()
                            }
                            // Con zoom: doble tap reinicia; sin zoom solo en el centro: hace zoom in
                            isDoubleTap && (zoomed || inCenter) -> {
                                lastTap = null
                                onZoom()
                            }
                            !isDoubleTap -> {
                                // Tap simple: alternar controles, esperando por si llega un doble tap
                                scope.launch {
                                    delay(doubleTapTimeout)
                                    if (lastTap == up) {
                                        lastTap = null
                                        onToggleControls()
                                    }
                                }
                            }
                        }
                    } else {
                        lastTap = null
                    }

                    if (currentScale <= 1f) {
                        onOffsetChange(0f, 0f)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(imageFile),
            contentDescription = "Página del cómic",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )
    }
}
