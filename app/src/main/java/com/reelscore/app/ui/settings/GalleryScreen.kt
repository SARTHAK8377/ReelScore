package com.reelscore.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.reelscore.app.distance.Sketch
import com.reelscore.app.distance.things
import kotlin.math.min

/** Developer-only: every comparison silhouette, to check they read at a glance. */
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    val cut = MaterialTheme.colorScheme.surfaceContainerLow
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(3) }) { PageHeader("Silhouettes", onBack) }
        items(things) { thing ->
            Column(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(cut)
                    .padding(8.dp),
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val box = min(size.width, size.height)
                    val w: Float
                    val h: Float
                    if (thing.aspect >= 1f) { w = box; h = box / thing.aspect } else { h = box; w = box * thing.aspect }
                    Sketch(this, (size.width - w) / 2f, size.height - h, w, h, ink, cut).apply(thing.art)
                }
                Text(thing.one, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
