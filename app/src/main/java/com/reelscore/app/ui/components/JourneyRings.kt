package com.reelscore.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.data.ReelRepository.Period
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.distance.ScrollDistance
import com.reelscore.app.journey.Journey
import com.reelscore.app.journey.formatPercent

private val easeOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Today tab, shown only during a journey: overall progress and today's share, as two rings. */
@Composable
fun JourneyRings(journey: Journey, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { ReelRepository.get(context) }
    val reels by remember(journey.startedAt) { repo.totalSince(journey.startedAt) }.collectAsStateWithLifecycle(0)
    val todayFrom = maxOf(Period.TODAY.start(), journey.startedAt)
    val todayReels by remember(todayFrom) { repo.totalSince(todayFrom) }.collectAsStateWithLifecycle(0)
    val done = ScrollDistance.meters(reels, context).coerceAtMost(journey.meters)
    val today = ScrollDistance.meters(todayReels, context)

    Column(modifier) {
        Text("Journey", style = MaterialTheme.typography.titleLarge)
        Text(
            "${journey.from.city} → ${journey.to.city}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            Row(Modifier.padding(vertical = 20.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Ring(
                    fraction = done / journey.meters,
                    title = "Overall",
                    caption = "${DistanceFormat.format(done)} of ${DistanceFormat.format(journey.meters)}",
                    modifier = Modifier.weight(1f),
                )
                Ring(
                    fraction = today / journey.meters,
                    title = "Today",
                    caption = DistanceFormat.format(today),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A hollow progress ring with the percentage inside. Any progress shows at least a sliver. */
@Composable
private fun Ring(fraction: Double, title: String, caption: String, modifier: Modifier = Modifier) {
    val clamped = fraction.coerceIn(0.0, 1.0).toFloat()
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(clamped) { sweep.animateTo(clamped, tween(900, easing = easeOutExpo)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary
    val percent = formatPercent(fraction)

    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$title: $percent, $caption" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                val degrees = if (clamped > 0f) maxOf(sweep.value * 360f, 4f) else 0f
                if (degrees > 0f) {
                    drawArc(accent, -90f, degrees, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text(percent, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp).clearAndSetSemantics { },
        )
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
