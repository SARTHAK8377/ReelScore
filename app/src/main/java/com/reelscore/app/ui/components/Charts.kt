package com.reelscore.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** 0..1 that animates in once, or is 1 straight away when animations are off. */
@Composable
private fun drawIn(key: Any?): Float {
    val reduce = reducedMotion()
    val progress = remember(key) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key) { if (!reduce) progress.animateTo(1f, tween(900, easing = EaseOutExpo)) }
    return progress.value
}

/** A lime tint for a heat cell: grey when empty, full lime at the busiest value. */
@Composable
private fun heatColor(): (Float) -> Color {
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val full = MaterialTheme.colorScheme.primaryContainer
    return { level -> if (level <= 0f) empty else lerp(lerp(empty, full, 0.25f), full, level.coerceIn(0f, 1f)) }
}

/**
 * How today built up: a running total across the whole day, drawn from midnight to now as a line
 * with a lime fill. Each hour's reels land at the end of that hour (or at now, for this hour).
 */
@Composable
fun DayLine(byHour: List<Int>, now: java.time.LocalTime, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fill = MaterialTheme.colorScheme.primaryContainer
    val guide = MaterialTheme.colorScheme.outlineVariant
    val running = remember(byHour) { byHour.runningReduce { a, b -> a + b } }
    val nowHour = now.hour
    val nowFraction = (now.hour + now.minute / 60f) / 24f
    val top = (running.getOrNull(nowHour) ?: 0).coerceAtLeast(1)
    val reveal = drawIn(running)

    Column(modifier.semantics { contentDescription = "Reels through the day, ${running.getOrNull(nowHour) ?: 0} so far" }) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val usable = size.height - 4.dp.toPx()
            fun y(total: Int) = size.height - total.toFloat() / top * usable
            val pts = (0 until nowHour).map { h -> Offset((h + 1) / 24f * size.width, y(running[h])) } +
                Offset(nowFraction * size.width, y(running[nowHour]))
            drawLine(guide, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            val line = Path().apply {
                moveTo(0f, size.height)
                pts.forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                close()
            }
            clipRect(right = size.width * reveal) {
                drawPath(area, fill.copy(alpha = 0.55f))
                drawPath(line, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(ink, 3.5.dp.toPx(), pts.last())
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("12 am", "noon", "12 am").forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (i) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A row of heat cells, one per value. */
@Composable
fun HeatStrip(values: List<Int>, modifier: Modifier = Modifier) {
    val color = heatColor()
    val peak = values.maxOrNull()?.takeIf { it > 0 } ?: 1
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        values.forEach { v ->
            Box(
                Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color(v.toFloat() / peak)),
            )
        }
    }
}

/** Weekdays down, hours across: which hours you watch most. [grid] is 7 rows (Monday first) of 24. */
@Composable
fun HourHeatmap(grid: List<List<Int>>, modifier: Modifier = Modifier) {
    val color = heatColor()
    val peak = grid.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1
    val reveal = drawIn(grid)
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(modifier) {
        grid.forEachIndexed { d, row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.5.dp)) {
                Text(
                    days[d],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp).width(12.dp),
                )
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val gap = 2.dp.toPx()
                    val w = (size.width - gap * 23) / 24f
                    row.forEachIndexed { h, v ->
                        val level = v.toFloat() / peak * reveal
                        drawRoundRect(color(level), Offset(h * (w + gap), 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        // One label per 6 columns, so each starts under its own hour.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 18.dp)) {
            listOf("12 am", "6 am", "noon", "6 pm").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * One month as a calendar of heat cells, Monday first. Tapping a day reports it through [onDay].
 * [counts] maps ISO days to reels.
 */
@Composable
fun CalendarHeatmap(
    month: YearMonth,
    counts: Map<String, Int>,
    selected: LocalDate?,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = heatColor()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val peak = counts.values.maxOrNull()?.takeIf { it > 0 } ?: 1
    val today = LocalDate.now()
    val lead = month.atDay(1).dayOfWeek.value - 1
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(DateTextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val n = counts[day.toString()] ?: 0
                            val future = day.isAfter(today)
                            Box(
                                Modifier
                                    .matchParentSizeCompat()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (future) Color.Transparent else color(n.toFloat() / peak))
                                    .then(
                                        if (day == selected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(if (!future) Modifier.clickable(onClickLabel = "Show $day") { onDay(day) } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${day.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (future) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().aspectRatio(1f)

/** A small trend line with no axes, for a row or tile. */
@Composable
fun Sparkline(values: List<Int>, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val reveal = drawIn(values)
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val peak = values.max().coerceAtLeast(1)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * stepX, size.height - v.toFloat() / peak * size.height)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        clipRect(right = size.width * reveal) {
            drawPath(path, ink, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
