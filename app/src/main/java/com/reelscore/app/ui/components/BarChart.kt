package com.reelscore.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelscore.app.data.DayCount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val easeOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Daily reels as bars, oldest on the left. Today's bar is the accent; the rest are quiet.
 * Bars grow in when the chart first appears or the range changes. Short ranges label every bar.
 *
 * With [journeyShare] (one percentage per day, for an active journey), each day gets a second,
 * lime bar for the share of the journey covered that day. Both series scale to their own peak,
 * since they're in different units, and a legend names them.
 */
@Composable
fun BarChart(days: List<DayCount>, modifier: Modifier = Modifier, journeyShare: List<Float>? = null) {
    if (days.isEmpty()) return
    val paired = journeyShare != null && journeyShare.size == days.size
    val labelled = days.size <= 7
    val gap = if (labelled) 10.dp else 4.dp
    val grow = remember(days.size, paired) { Animatable(0f) }
    LaunchedEffect(days.size, paired) { grow.animateTo(1f, tween(durationMillis = 700, easing = easeOutExpo)) }

    // Today in the text colour, other days in a light grey, journey share in lime.
    val today = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primaryContainer
    val rest = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val reelsToday = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val peak = max(days.maxOf { it.count }, 1)
    val journeyPeak = if (paired) max(journeyShare!!.max(), 0.000001f) else 1f
    val summary = days.joinToString { "${it.day}: ${it.count}" }

    Column(modifier.semantics { contentDescription = "Reels per day. $summary" }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(168.dp),
        ) {
            val gapPx = gap.toPx()
            val labelSpace = if (labelled && !paired) 22.dp.toPx() else 0f
            val slotWidth = (size.width - gapPx * (days.size - 1)) / days.size
            val innerGap = if (paired) min(3.dp.toPx(), slotWidth * 0.15f) else 0f
            val barWidth = if (paired) (slotWidth - innerGap) / 2f else slotWidth
            val usable = size.height - labelSpace
            val radius = CornerRadius(min(barWidth / 2f, 8.dp.toPx()))
            days.forEachIndexed { i, day ->
                val full = usable * day.count / peak
                val height = max(full * grow.value, if (day.count > 0) 4.dp.toPx() else 2.dp.toPx())
                val left = i * (slotWidth + gapPx)
                drawRoundRect(
                    color = when {
                        paired -> if (i == days.lastIndex) reelsToday else rest
                        i == days.lastIndex -> today
                        else -> rest
                    },
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
                if (paired) {
                    val share = journeyShare!![i]
                    val jHeight = max(usable * (share / journeyPeak) * grow.value, if (share > 0f) 4.dp.toPx() else 2.dp.toPx())
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left + barWidth + innerGap, size.height - jHeight),
                        size = Size(barWidth, jHeight),
                        cornerRadius = radius,
                    )
                }
                if (labelled && !paired && day.count > 0) {
                    val text = measurer.measure("${day.count}", valueStyle)
                    drawText(
                        text,
                        topLeft = Offset(
                            left + (barWidth - text.size.width) / 2f,
                            size.height - height - text.size.height - 4.dp.toPx(),
                        ),
                        alpha = grow.value,
                    )
                }
            }
        }
        val labelStyle = MaterialTheme.typography.labelMedium
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        if (labelled) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                days.forEach { day ->
                    Text(
                        LocalDate.parse(day.day).dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.weight(1f),
                        style = labelStyle,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            val format = DateTimeFormatter.ofPattern("d MMM")
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(LocalDate.parse(days.first().day).format(format), style = labelStyle, color = labelColor)
                Text("Today", style = labelStyle, color = labelColor)
            }
        }
        if (paired) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Legend(rest, "Reels")
                Legend(accent, "Journey covered")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
