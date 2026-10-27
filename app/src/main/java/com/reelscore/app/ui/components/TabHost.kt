package com.reelscore.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

private const val PREWARM_DELAY_MS = 400L

/**
 * Hosts the bottom-nav tabs without rebuilding them. Each tab is composed once and then kept;
 * hidden tabs stay composed but are not placed, so they draw nothing and take no touches.
 * Switching is a short GPU-only crossfade. Rebuilding a whole tab on every switch was what
 * made tab changes lag. Tabs not yet visited are composed shortly after launch, so the first
 * switch is smooth too.
 */
@Composable
fun TabHost(
    selected: String,
    allTabs: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val composed = remember { mutableStateListOf<String>() }
    if (selected !in composed) composed += selected
    LaunchedEffect(Unit) {
        delay(PREWARM_DELAY_MS)
        allTabs.filterNot { it in composed }.forEach { composed += it }
    }

    Box(modifier) {
        composed.forEach { tab ->
            key(tab) {
                val isSelected = tab == selected
                val alpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(durationMillis = if (isSelected) 200 else 110),
                    label = "tabAlpha",
                )
                val shown = isSelected || alpha > 0f
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (isSelected) 1f else 0f)
                        .then(
                            if (shown) {
                                Modifier.graphicsLayer {
                                    this.alpha = alpha
                                    val scale = 0.985f + 0.015f * alpha
                                    scaleX = scale
                                    scaleY = scale
                                }
                            } else {
                                Modifier.notPlaced()
                            },
                        ),
                ) { content(tab) }
            }
        }
    }
}

/** Measures but never places: the content stays composed and keeps its state, but isn't drawn or hit-tested. */
private fun Modifier.notPlaced(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {}
}
