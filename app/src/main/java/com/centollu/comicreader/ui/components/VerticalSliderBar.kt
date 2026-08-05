package com.centollu.comicreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VerticalSliderBar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    VerticalSliderBarCore(
        firstVisibleItemIndex = state.firstVisibleItemIndex,
        totalItemsCount = state.layoutInfo.totalItemsCount,
        visibleItemsCount = state.layoutInfo.visibleItemsInfo.size,
        onScrollTo = { index -> state.scrollToItem(index) },
        modifier = modifier
    )
}

@Composable
fun VerticalSliderBar(
    state: LazyGridState,
    modifier: Modifier = Modifier
) {
    VerticalSliderBarCore(
        firstVisibleItemIndex = state.firstVisibleItemIndex,
        totalItemsCount = state.layoutInfo.totalItemsCount,
        visibleItemsCount = state.layoutInfo.visibleItemsInfo.size,
        onScrollTo = { index -> state.scrollToItem(index) },
        modifier = modifier
    )
}

@Composable
private fun VerticalSliderBarCore(
    firstVisibleItemIndex: Int,
    totalItemsCount: Int,
    visibleItemsCount: Int,
    onScrollTo: suspend (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentFirst by rememberUpdatedState(firstVisibleItemIndex)
    val currentTotal by rememberUpdatedState(totalItemsCount)
    val currentVisible by rememberUpdatedState(visibleItemsCount)
    val currentOnScrollTo by rememberUpdatedState(onScrollTo)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var barHeightPx by remember { mutableIntStateOf(0) }

    val scrollableSpan = (currentTotal - currentVisible).coerceAtLeast(1)
    val thumbFraction = if (scrollableSpan <= 0) 0f else {
        currentFirst.toFloat() / scrollableSpan
    }.coerceIn(0f, 1f)
    val currentFraction = dragFraction ?: thumbFraction

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(18.dp)
            .onSizeChanged { barHeightPx = it.height }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (barHeightPx > 0) {
                            dragFraction = (offset.y / barHeightPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = { dragFraction = null },
                    onDragCancel = { dragFraction = null },
                    onVerticalDrag = { change, _ ->
                        if (barHeightPx > 0) {
                            dragFraction = (change.position.y / barHeightPx).coerceIn(0f, 1f)
                            val target = ((dragFraction ?: 0f) * currentTotal)
                                .roundToInt()
                                .coerceIn(0, (currentTotal - 1).coerceAtLeast(0))
                            scope.launch { currentOnScrollTo(target) }
                        }
                        change.consume()
                    }
                )
            }
    ) {
        val trackColor = MaterialTheme.colorScheme.outlineVariant
        val thumbColor = MaterialTheme.colorScheme.primary

        val trackWidth = with(density) { 3.dp.toPx() }
        val thumbWidth = with(density) { 4.dp.toPx() }
        val minThumbHeight = with(density) { 24.dp.toPx() }

        val maxHeightPx = barHeightPx.toFloat()
        val thumbHeight = if (currentTotal == 0) maxHeightPx else {
            (maxHeightPx * (currentVisible.toFloat() / currentTotal)).coerceAtLeast(minThumbHeight)
        }
        val thumbTop = if (currentTotal == 0 || currentTotal <= currentVisible) {
            0f
        } else {
            (maxHeightPx - thumbHeight) * currentFraction
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp)
        ) {
            val centerX = size.width / 2
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - trackWidth / 2, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackWidth / 2)
            )
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(centerX - thumbWidth / 2, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbWidth / 2)
            )
        }
    }
}
