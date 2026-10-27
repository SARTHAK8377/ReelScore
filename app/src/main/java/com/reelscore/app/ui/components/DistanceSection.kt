package com.reelscore.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.distance.Sketch
import com.reelscore.app.distance.Thing
import com.reelscore.app.distance.compare
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

private val easeOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Your distance as a lime line beside [thing]'s silhouette, both on one scale that fits the box.
 * Tall things stand on the ground with your line rising next to them; long things sit above
 * your line, which runs underneath. Dashed guides mark where the thing ends.
 */
@Composable
fun ScaleComparison(meters: Double, thing: Thing, imperial: Boolean, modifier: Modifier = Modifier) {
    val grow = remember(thing, meters) { Animatable(0f) }
    LaunchedEffect(thing, meters) { grow.animateTo(1f, tween(900, easing = easeOutExpo)) }

    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    val cut = MaterialTheme.colorScheme.surfaceContainerLow
    val accent = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val thingStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val youStyle = MaterialTheme.typography.labelLarge.copy(color = accent, fontWeight = FontWeight.SemiBold)
    val youText = "You · ${DistanceFormat.format(meters, imperial)}"
    val thingText = DistanceFormat.format(thing.meters, imperial)
    val picture = thing.picture?.let { ImageBitmap.imageResource(it) }

    Canvas(modifier.semantics { contentDescription = "Your $youText next to ${thing.one}, $thingText" }) {
        val labelH = measurer.measure("Ag", youStyle).size.height.toFloat()
        val lineW = 6.dp.toPx()
        val drawn = Drawn(this, measurer, ink, cut, accent, guide, thingStyle, youStyle, labelH, lineW, picture)
        if (thing.tall) drawn.tall(meters, thing, thingText, youText, grow.value) else drawn.long(meters, thing, thingText, youText, grow.value)
    }
}

private class Drawn(
    val scope: DrawScope,
    val measurer: TextMeasurer,
    val ink: androidx.compose.ui.graphics.Color,
    val cut: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val guide: androidx.compose.ui.graphics.Color,
    val thingStyle: TextStyle,
    val youStyle: TextStyle,
    val labelH: Float,
    val lineW: Float,
    val picture: ImageBitmap?,
) {
    private val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))

    /** The picture's own width / height when there is one, otherwise the vector drawing's. */
    private fun aspect(thing: Thing) = picture?.let { it.width.toFloat() / it.height } ?: thing.aspect

    private fun DrawScope.thing(thing: Thing, left: Float, top: Float, w: Float, h: Float) {
        val image = picture
        if (image == null) {
            Sketch(this, left, top, w, h, ink, cut).apply(thing.art)
        } else {
            drawImage(
                image,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.High,
            )
        }
    }

    private fun DrawScope.label(text: String, style: TextStyle, centerX: Float, top: Float) {
        val layout = measurer.measure(text, style)
        val x = (centerX - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, top.coerceAtLeast(0f)))
    }

    /** Height comparison: silhouette on the ground, your line rising beside it. */
    fun tall(meters: Double, thing: Thing, thingText: String, youText: String, grow: Float) = with(scope) {
        val groundY = size.height - 2.dp.toPx()
        val topReserve = labelH + 8.dp.toPx()
        val availH = groundY - topReserve
        val maxW = size.width * 0.5f
        val aspect = aspect(thing)
        var scale = availH / max(meters, thing.meters).toFloat()
        if (thing.meters.toFloat() * scale * aspect > maxW) scale = maxW / (thing.meters.toFloat() * aspect)
        val th = thing.meters.toFloat() * scale
        val tw = th * aspect
        val cx = size.width * 0.34f
        val left = (cx - tw / 2f).coerceAtLeast(0f)
        val lineX = size.width * 0.78f
        val youH = meters.toFloat() * scale * grow

        drawLine(guide, Offset(0f, groundY), Offset(size.width, groundY), 1.dp.toPx())
        thing(thing, left, groundY - th, tw, th)
        drawLine(guide, Offset(left + tw, groundY - th), Offset(lineX, groundY - th), 1.dp.toPx(), pathEffect = dash)
        drawLine(accent, Offset(lineX, groundY), Offset(lineX, groundY - youH), lineW, StrokeCap.Round)
        drawLine(accent, Offset(lineX - 8.dp.toPx(), groundY - youH), Offset(lineX + 8.dp.toPx(), groundY - youH), 2.dp.toPx(), StrokeCap.Round)
        label(thingText, thingStyle, left + tw / 2f, groundY - th - labelH - 4.dp.toPx())
        label(youText, youStyle, lineX, groundY - youH - labelH - 6.dp.toPx())
    }

    /** Length comparison: silhouette above, your line running underneath from the same start. */
    fun long(meters: Double, thing: Thing, thingText: String, youText: String, grow: Float) = with(scope) {
        val youTop = size.height - labelH
        val lineY = youTop - 10.dp.toPx() - lineW / 2f
        val groundY = lineY - 16.dp.toPx()
        val thingTop = labelH + 6.dp.toPx()
        val maxH = groundY - thingTop
        val availW = size.width - lineW
        val aspect = aspect(thing)
        var scale = availW / max(meters, thing.meters).toFloat()
        if (thing.meters.toFloat() * scale / aspect > maxH) scale = maxH * aspect / thing.meters.toFloat()
        val tw = thing.meters.toFloat() * scale
        val th = tw / aspect
        val left = lineW / 2f
        val youW = meters.toFloat() * scale * grow

        thing(thing, left, groundY - th, tw, th)
        drawLine(guide, Offset(left + tw, groundY - th), Offset(left + tw, lineY + lineW), 1.dp.toPx(), pathEffect = dash)
        drawLine(guide, Offset(left, groundY), Offset(left, lineY + lineW), 1.dp.toPx(), pathEffect = dash)
        drawLine(accent, Offset(left, lineY), Offset(left + youW, lineY), lineW, StrokeCap.Round)
        label(thingText, thingStyle, left + tw / 2f, groundY - th - labelH - 4.dp.toPx())
        label(youText, youStyle, left + max(youW, 1f) / 2f, youTop)
    }
}
