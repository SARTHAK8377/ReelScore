package com.reelscore.app.distance

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min

/**
 * Flat silhouettes for distance comparisons, drawn in code so they stay sharp at any size.
 * Each draws into a box in 0..1 coordinates (x left to right, y top to bottom, ground at y = 1).
 * [Sketch.ink] is the silhouette; [Sketch.cut] is the card colour, used for windows, lines and
 * arches cut into it.
 */
class Sketch(
    private val scope: DrawScope,
    private val left: Float,
    private val top: Float,
    val w: Float,
    val h: Float,
    val ink: Color,
    val cut: Color,
) {
    fun x(v: Float) = left + v * w
    fun y(v: Float) = top + v * h
    private val unit get() = min(w, h)

    fun rect(x0: Float, y0: Float, x1: Float, y1: Float, color: Color = ink, round: Float = 0f) =
        scope.drawRoundRect(color, Offset(x(x0), y(y0)), Size((x1 - x0) * w, (y1 - y0) * h), CornerRadius(round * unit))

    /** Circle at (cx, cy); radius as a fraction of the box height. */
    fun circle(cx: Float, cy: Float, r: Float, color: Color = ink) = scope.drawCircle(color, r * h, Offset(x(cx), y(cy)))

    fun oval(x0: Float, y0: Float, x1: Float, y1: Float, color: Color = ink) =
        scope.drawOval(color, Offset(x(x0), y(y0)), Size((x1 - x0) * w, (y1 - y0) * h))

    /** Filled polygon from x, y pairs. */
    fun poly(vararg p: Float, color: Color = ink) {
        val path = Path().apply {
            moveTo(x(p[0]), y(p[1]))
            for (i in 2 until p.size step 2) lineTo(x(p[i]), y(p[i + 1]))
            close()
        }
        scope.drawPath(path, color)
    }

    /** Straight line; width as a fraction of the box's smaller side. */
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Color = ink) =
        scope.drawLine(color, Offset(x(x0), y(y0)), Offset(x(x1), y(y1)), width * unit, StrokeCap.Round)

    fun shape(color: Color = ink, build: Path.() -> Unit) = scope.drawPath(Path().apply(build), color)

    fun stroke(width: Float, color: Color = ink, dashed: Boolean = false, build: Path.() -> Unit) = scope.drawPath(
        Path().apply(build),
        color,
        style = Stroke(
            width = width * unit,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(width * unit * 2.2f, width * unit * 2.2f)) else null,
        ),
    )

    fun tilted(degrees: Float, block: Sketch.() -> Unit) = scope.rotate(degrees, Offset(x(0.5f), y(1f))) { block() }

    fun Path.m(px: Float, py: Float) = moveTo(x(px), y(py))
    fun Path.l(px: Float, py: Float) = lineTo(x(px), y(py))
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x(x1), y(y1), x(x2), y(y2), x(x3), y(y3))
    fun Path.q(x1: Float, y1: Float, x2: Float, y2: Float) = quadraticTo(x(x1), y(y1), x(x2), y(y2))
}

/** Every drawing, keyed by name. Aspect ratios (width / height) live with the [Thing]s. */
object Art {
    val phone: Sketch.() -> Unit = {
        rect(0f, 0f, 1f, 1f, round = 0.22f)
        rect(0.08f, 0.07f, 0.92f, 0.93f, cut, round = 0.12f)
        circle(0.5f, 0.035f, 0.012f)
    }

    val bat: Sketch.() -> Unit = {
        rect(0.36f, 0f, 0.64f, 0.36f, round = 0.12f)
        for (i in 0 until 5) line(0.36f, 0.04f + i * 0.055f, 0.64f, 0.06f + i * 0.055f, 0.06f, cut)
        rect(0f, 0.32f, 1f, 1f, round = 0.45f)
        line(0.5f, 0.42f, 0.5f, 0.92f, 0.08f, cut)
    }

    val door: Sketch.() -> Unit = {
        rect(0f, 0f, 1f, 1f, round = 0.04f)
        rect(0.14f, 0.07f, 0.86f, 0.44f, cut, round = 0.03f)
        rect(0.2f, 0.11f, 0.8f, 0.4f, round = 0.02f)
        rect(0.14f, 0.52f, 0.86f, 0.93f, cut, round = 0.03f)
        rect(0.2f, 0.56f, 0.8f, 0.89f, round = 0.02f)
        circle(0.84f, 0.48f, 0.022f, cut)
    }

    val autoRickshaw: Sketch.() -> Unit = {
        // Soft canopy roof over open sides, nose on the right.
        shape {
            m(0.03f, 0.2f); c(0.03f, 0.08f, 0.1f, 0.02f, 0.22f, 0.02f); l(0.62f, 0.02f)
            c(0.72f, 0.02f, 0.78f, 0.08f, 0.8f, 0.16f); l(0.03f, 0.16f); close()
        }
        rect(0.03f, 0.14f, 0.11f, 0.62f)
        // Slanted windscreen and the driver's handlebar.
        poly(0.72f, 0.14f, 0.79f, 0.14f, 0.9f, 0.5f, 0.83f, 0.5f)
        line(0.7f, 0.42f, 0.78f, 0.36f, 0.05f)
        // Seat seen through the open side.
        rect(0.14f, 0.36f, 0.42f, 0.52f, round = 0.04f)
        // Body sweeping down to the front fender.
        shape {
            m(0.03f, 0.52f); l(0.82f, 0.52f); c(0.93f, 0.52f, 0.99f, 0.62f, 0.99f, 0.74f)
            l(0.99f, 0.8f); l(0.03f, 0.8f); close()
        }
        circle(0.96f, 0.6f, 0.035f, cut)
        circle(0.22f, 0.84f, 0.16f); circle(0.22f, 0.84f, 0.07f, cut)
        circle(0.86f, 0.86f, 0.13f); circle(0.86f, 0.86f, 0.055f, cut)
    }

    val car: Sketch.() -> Unit = {
        shape {
            m(0f, 0.78f); l(0f, 0.52f); c(0f, 0.45f, 0.04f, 0.42f, 0.1f, 0.41f); l(0.26f, 0.38f)
            l(0.36f, 0.12f); l(0.68f, 0.12f); l(0.8f, 0.38f); l(0.95f, 0.44f); c(1f, 0.47f, 1f, 0.52f, 1f, 0.56f)
            l(1f, 0.78f); close()
        }
        poly(0.38f, 0.19f, 0.5f, 0.19f, 0.5f, 0.37f, 0.31f, 0.37f, color = cut)
        poly(0.53f, 0.19f, 0.66f, 0.19f, 0.75f, 0.37f, 0.53f, 0.37f, color = cut)
        circle(0.2f, 0.8f, 0.2f); circle(0.2f, 0.8f, 0.09f, cut)
        circle(0.8f, 0.8f, 0.2f); circle(0.8f, 0.8f, 0.09f, cut)
    }

    val elephant: Sketch.() -> Unit = {
        oval(0.22f, 0.14f, 0.92f, 0.7f)
        rect(0.28f, 0.5f, 0.4f, 1f, round = 0.06f); rect(0.44f, 0.5f, 0.55f, 0.98f, round = 0.06f)
        rect(0.68f, 0.5f, 0.79f, 1f, round = 0.06f); rect(0.81f, 0.5f, 0.91f, 0.98f, round = 0.06f)
        oval(0.02f, 0.08f, 0.38f, 0.56f)
        // Trunk curling down to the ground.
        stroke(0.13f) { m(0.08f, 0.42f); c(0.02f, 0.62f, 0.0f, 0.82f, 0.06f, 0.96f) }
        oval(0.18f, 0.16f, 0.38f, 0.5f, cut); oval(0.2f, 0.19f, 0.36f, 0.47f)
        circle(0.12f, 0.27f, 0.025f, cut)
        stroke(0.03f) { m(0.92f, 0.36f); l(0.99f, 0.6f) }
        line(0.11f, 0.48f, 0.2f, 0.55f, 0.05f, cut)
    }

    val bus: Sketch.() -> Unit = {
        rect(0f, 0.04f, 1f, 0.84f, round = 0.12f)
        for (i in 0 until 7) rect(0.04f + i * 0.122f, 0.16f, 0.14f + i * 0.122f, 0.46f, cut, round = 0.04f)
        rect(0.9f, 0.16f, 0.97f, 0.74f, cut, round = 0.03f)
        rect(0f, 0.56f, 1f, 0.6f, cut)
        circle(0.17f, 0.86f, 0.14f); circle(0.17f, 0.86f, 0.06f, cut)
        circle(0.8f, 0.86f, 0.14f); circle(0.8f, 0.86f, 0.06f, cut)
    }

    /** Cricket pitch from the side: stumps and bails at both ends, a ball mid-pitch. */
    val cricketPitch: Sketch.() -> Unit = {
        rect(0f, 0.9f, 1f, 1f)
        for (base in floatArrayOf(0.006f, 0.946f)) {
            for (i in 0 until 3) rect(base + i * 0.02f, 0.18f, base + i * 0.02f + 0.01f, 0.9f)
            rect(base - 0.003f, 0.12f, base + 0.053f, 0.18f)
        }
        circle(0.5f, 0.7f, 0.08f)
        line(0.485f, 0.7f, 0.515f, 0.7f, 0.025f, cut)
    }

    val trainCoach: Sketch.() -> Unit = {
        rect(0f, 0.04f, 1f, 0.76f, round = 0.1f)
        for (i in 0 until 10) rect(0.1f + i * 0.08f, 0.16f, 0.15f + i * 0.08f, 0.38f, cut, round = 0.02f)
        rect(0.025f, 0.14f, 0.07f, 0.7f, cut, round = 0.01f); rect(0.93f, 0.14f, 0.975f, 0.7f, cut, round = 0.01f)
        rect(0f, 0.5f, 1f, 0.54f, cut)
        rect(0.08f, 0.76f, 0.3f, 0.84f); rect(0.7f, 0.76f, 0.92f, 0.84f)
        for (x in floatArrayOf(0.13f, 0.25f, 0.75f, 0.87f)) { circle(x, 0.88f, 0.12f); circle(x, 0.88f, 0.05f, cut) }
    }

    /** Tennis court from above. */
    val tennisCourt: Sketch.() -> Unit = {
        rect(0f, 0f, 1f, 1f, round = 0.03f)
        val s = 0.025f
        stroke(s, cut) { m(0.03f, 0.04f); l(0.97f, 0.04f); l(0.97f, 0.96f); l(0.03f, 0.96f); close() }
        line(0.03f, 0.16f, 0.97f, 0.16f, s, cut); line(0.03f, 0.84f, 0.97f, 0.84f, s, cut)
        line(0.24f, 0.16f, 0.24f, 0.84f, s, cut); line(0.76f, 0.16f, 0.76f, 0.84f, s, cut)
        line(0.24f, 0.5f, 0.76f, 0.5f, s, cut)
        line(0.5f, 0f, 0.5f, 1f, s * 2.2f, cut)
    }

    val blueWhale: Sketch.() -> Unit = {
        shape {
            m(0.01f, 0.5f); c(0.03f, 0.3f, 0.2f, 0.2f, 0.4f, 0.22f); c(0.6f, 0.24f, 0.78f, 0.36f, 0.88f, 0.46f)
            l(0.97f, 0.22f); c(0.99f, 0.3f, 0.97f, 0.44f, 0.94f, 0.5f); c(0.97f, 0.56f, 0.99f, 0.7f, 0.97f, 0.78f)
            l(0.88f, 0.54f); c(0.74f, 0.68f, 0.5f, 0.74f, 0.3f, 0.72f); c(0.14f, 0.7f, 0.02f, 0.64f, 0.01f, 0.5f); close()
        }
        poly(0.3f, 0.68f, 0.4f, 0.92f, 0.43f, 0.7f)
        poly(0.62f, 0.3f, 0.66f, 0.2f, 0.69f, 0.33f)
        circle(0.13f, 0.46f, 0.03f, cut)
        stroke(0.025f, cut) { m(0.04f, 0.56f); c(0.12f, 0.62f, 0.24f, 0.63f, 0.34f, 0.61f) }
    }

    /** Basketball court from above. */
    val basketballCourt: Sketch.() -> Unit = {
        rect(0f, 0f, 1f, 1f, round = 0.03f)
        val s = 0.022f
        line(0.5f, 0f, 0.5f, 1f, s, cut)
        stroke(s, cut) { addOval(androidx.compose.ui.geometry.Rect(x(0.5f) - 0.18f * h, y(0.32f), x(0.5f) + 0.18f * h, y(0.68f))) }
        stroke(s, cut) { m(0f, 0.34f); l(0.2f, 0.34f); l(0.2f, 0.66f); l(0f, 0.66f) }
        stroke(s, cut) { m(1f, 0.34f); l(0.8f, 0.34f); l(0.8f, 0.66f); l(1f, 0.66f) }
        stroke(s, cut) { m(0f, 0.06f); c(0.38f, 0.1f, 0.38f, 0.9f, 0f, 0.94f) }
        stroke(s, cut) { m(1f, 0.06f); c(0.62f, 0.1f, 0.62f, 0.9f, 1f, 0.94f) }
    }

    val gatewayOfIndia: Sketch.() -> Unit = {
        rect(0f, 0.36f, 1f, 1f)
        rect(-0.02f, 0.32f, 1.02f, 0.38f)
        // Central arch and two side arches.
        rect(0.38f, 0.56f, 0.62f, 1f, cut); circle(0.5f, 0.56f, 0.12f, cut)
        rect(0.1f, 0.7f, 0.22f, 1f, cut); circle(0.16f, 0.7f, 0.06f, cut)
        rect(0.78f, 0.7f, 0.9f, 1f, cut); circle(0.84f, 0.7f, 0.06f, cut)
        // Four corner turrets with domes, and the central parapet.
        for (xs in floatArrayOf(0.02f, 0.27f, 0.63f, 0.88f)) {
            rect(xs, 0.12f, xs + 0.1f, 0.34f)
            circle(xs + 0.05f, 0.12f, 0.055f)
            line(xs + 0.05f, 0.02f, xs + 0.05f, 0.08f, 0.02f)
        }
        rect(0.37f, 0.22f, 0.63f, 0.34f)
        circle(0.5f, 0.22f, 0.06f)
    }

    val indiaGate: Sketch.() -> Unit = {
        rect(0.06f, 0.26f, 0.94f, 1f)
        rect(0f, 0.22f, 1f, 0.28f)
        rect(0.2f, 0.12f, 0.8f, 0.22f)
        rect(0.34f, 0.05f, 0.66f, 0.12f)
        oval(0.38f, 0f, 0.62f, 0.1f)
        rect(0.32f, 0.5f, 0.68f, 1f, cut); circle(0.5f, 0.5f, 0.24f * 0.75f, cut)
        rect(0.1f, 0.36f, 0.22f, 0.44f, cut); rect(0.78f, 0.36f, 0.9f, 0.44f, cut)
        rect(0f, 0.95f, 1f, 1f)
    }

    /** Olympic pool from above, with lanes. */
    val pool: Sketch.() -> Unit = {
        rect(0f, 0f, 1f, 1f, round = 0.04f)
        rect(0.03f, 0.05f, 0.97f, 0.95f, cut, round = 0.02f)
        for (i in 1 until 8) {
            val yy = 0.05f + i * 0.9f / 8
            stroke(0.02f, dashed = true) { m(0.06f, yy); l(0.94f, yy) }
        }
        rect(0.03f, 0.05f, 0.06f, 0.95f); rect(0.94f, 0.05f, 0.97f, 0.95f)
    }

    val pisa: Sketch.() -> Unit = {
        tilted(5f) {
            rect(0.22f, 0.1f, 0.78f, 1f, round = 0.02f)
            for (i in 0 until 7) {
                val yy = 0.16f + i * 0.12f
                rect(0.18f, yy, 0.82f, yy + 0.025f)
                for (j in 0 until 4) rect(0.27f + j * 0.13f, yy + 0.04f, 0.33f + j * 0.13f, yy + 0.1f, cut, round = 0.02f)
            }
            rect(0.32f, 0f, 0.68f, 0.12f, round = 0.02f)
            for (j in 0 until 3) rect(0.37f + j * 0.1f, 0.03f, 0.42f + j * 0.1f, 0.09f, cut)
        }
    }

    val qutubMinar: Sketch.() -> Unit = {
        poly(0.1f, 1f, 0.9f, 1f, 0.68f, 0.06f, 0.32f, 0.06f)
        for (yy in floatArrayOf(0.3f, 0.52f, 0.7f, 0.84f)) {
            val half = 0.22f + (yy - 0.06f) * 0.25f
            rect(0.5f - half - 0.06f, yy, 0.5f + half + 0.06f, yy + 0.025f)
        }
        for (xx in floatArrayOf(0.4f, 0.5f, 0.6f)) line(xx, 0.1f, xx + (xx - 0.5f) * 0.6f, 0.98f, 0.035f, cut)
        rect(0.36f, 0.02f, 0.64f, 0.07f)
        oval(0.4f, 0f, 0.6f, 0.04f)
    }

    val tajMahal: Sketch.() -> Unit = {
        rect(0f, 0.9f, 1f, 1f)
        // Minarets.
        for (xs in floatArrayOf(0.03f, 0.92f)) {
            rect(xs, 0.3f, xs + 0.05f, 0.9f)
            rect(xs - 0.01f, 0.5f, xs + 0.06f, 0.52f); rect(xs - 0.01f, 0.7f, xs + 0.06f, 0.72f)
            circle(xs + 0.025f, 0.29f, 0.03f)
        }
        rect(0.27f, 0.5f, 0.73f, 0.9f)
        rect(0.44f, 0.58f, 0.56f, 0.9f, cut); circle(0.5f, 0.58f, 0.06f, cut)
        rect(0.31f, 0.62f, 0.38f, 0.74f, cut); rect(0.62f, 0.62f, 0.69f, 0.74f, cut)
        rect(0.31f, 0.78f, 0.38f, 0.88f, cut); rect(0.62f, 0.78f, 0.69f, 0.88f, cut)
        // Drum and onion dome with finial.
        rect(0.41f, 0.38f, 0.59f, 0.5f)
        shape {
            m(0.4f, 0.4f); c(0.34f, 0.3f, 0.4f, 0.16f, 0.5f, 0.06f); c(0.6f, 0.16f, 0.66f, 0.3f, 0.6f, 0.4f); close()
        }
        line(0.5f, 0f, 0.5f, 0.07f, 0.015f)
        // Small chhatris beside the dome.
        for (xs in floatArrayOf(0.33f, 0.67f)) { rect(xs - 0.03f, 0.44f, xs + 0.03f, 0.5f); circle(xs, 0.44f, 0.035f) }
    }

    val statueOfLiberty: Sketch.() -> Unit = {
        rect(0.12f, 0.88f, 0.88f, 1f)
        rect(0.22f, 0.56f, 0.78f, 0.88f)
        rect(0.18f, 0.54f, 0.82f, 0.58f)
        rect(0.3f, 0.66f, 0.7f, 0.68f, cut)
        // Robed figure.
        poly(0.36f, 0.54f, 0.64f, 0.54f, 0.6f, 0.24f, 0.5f, 0.2f, 0.4f, 0.24f)
        circle(0.5f, 0.16f, 0.04f)
        for (a in floatArrayOf(0.38f, 0.44f, 0.5f, 0.56f, 0.62f)) line(0.5f, 0.15f, a, 0.1f, 0.03f)
        // Raised arm with torch, and the tablet.
        line(0.57f, 0.25f, 0.68f, 0.06f, 0.08f)
        poly(0.65f, 0.06f, 0.72f, 0.06f, 0.685f, 0f)
        rect(0.32f, 0.3f, 0.4f, 0.4f, round = 0.02f)
    }

    val bigBen: Sketch.() -> Unit = {
        rect(0.15f, 0.36f, 0.85f, 1f)
        rect(0.08f, 0.18f, 0.92f, 0.38f)
        circle(0.5f, 0.28f, 0.075f, cut); circle(0.5f, 0.28f, 0.06f)
        line(0.5f, 0.28f, 0.5f, 0.24f, 0.05f, cut); line(0.5f, 0.28f, 0.57f, 0.28f, 0.05f, cut)
        rect(0.18f, 0.12f, 0.82f, 0.18f)
        poly(0.18f, 0.12f, 0.82f, 0.12f, 0.5f, 0f)
        for (xx in floatArrayOf(0.32f, 0.5f, 0.68f)) line(xx, 0.42f, xx, 0.96f, 0.06f, cut)
    }

    /** Football pitch from above. */
    val footballField: Sketch.() -> Unit = {
        rect(0f, 0f, 1f, 1f, round = 0.02f)
        val s = 0.02f
        stroke(s, cut) { m(0.02f, 0.03f); l(0.98f, 0.03f); l(0.98f, 0.97f); l(0.02f, 0.97f); close() }
        line(0.5f, 0.03f, 0.5f, 0.97f, s, cut)
        stroke(s, cut) { addOval(androidx.compose.ui.geometry.Rect(x(0.5f) - 0.14f * h, y(0.36f), x(0.5f) + 0.14f * h, y(0.64f))) }
        stroke(s, cut) { m(0.02f, 0.22f); l(0.16f, 0.22f); l(0.16f, 0.78f); l(0.02f, 0.78f) }
        stroke(s, cut) { m(0.98f, 0.22f); l(0.84f, 0.22f); l(0.84f, 0.78f); l(0.98f, 0.78f) }
        stroke(s, cut) { m(0.02f, 0.38f); l(0.07f, 0.38f); l(0.07f, 0.62f); l(0.02f, 0.62f) }
        stroke(s, cut) { m(0.98f, 0.38f); l(0.93f, 0.38f); l(0.93f, 0.62f); l(0.98f, 0.62f) }
    }

    val statueOfUnity: Sketch.() -> Unit = {
        // Tall stepped base.
        rect(0.04f, 0.9f, 0.96f, 1f)
        rect(0.14f, 0.82f, 0.86f, 0.9f)
        // Broad-shouldered walking figure in dhoti and shawl, arms at the sides.
        poly(
            0.3f, 0.82f, 0.46f, 0.82f, 0.5f, 0.66f, 0.54f, 0.82f, 0.7f, 0.82f,
            0.66f, 0.52f, 0.7f, 0.3f, 0.62f, 0.22f, 0.38f, 0.22f, 0.3f, 0.3f, 0.34f, 0.52f,
        )
        rect(0.22f, 0.26f, 0.3f, 0.58f, round = 0.04f)
        rect(0.7f, 0.26f, 0.78f, 0.58f, round = 0.04f)
        line(0.34f, 0.24f, 0.64f, 0.56f, 0.07f, cut)
        rect(0.45f, 0.16f, 0.55f, 0.23f)
        circle(0.5f, 0.12f, 0.06f)
    }

    val eiffelTower: Sketch.() -> Unit = {
        poly(0f, 1f, 0.33f, 0.62f, 0.44f, 0.32f, 0.48f, 0.08f, 0.5f, 0f, 0.52f, 0.08f, 0.56f, 0.32f, 0.67f, 0.62f, 1f, 1f)
        // Arch between the legs.
        shape(cut) { m(0.2f, 1.001f); c(0.26f, 0.74f, 0.74f, 0.74f, 0.8f, 1.001f); close() }
        rect(0.18f, 0.6f, 0.82f, 0.645f)
        rect(0.38f, 0.36f, 0.62f, 0.39f)
        rect(0.455f, 0.12f, 0.545f, 0.14f)
        // Lattice cross-bracing.
        line(0.36f, 0.6f, 0.52f, 0.4f, 0.025f, cut); line(0.64f, 0.6f, 0.48f, 0.4f, 0.025f, cut)
        line(0.45f, 0.35f, 0.52f, 0.16f, 0.02f, cut); line(0.55f, 0.35f, 0.48f, 0.16f, 0.02f, cut)
    }

    val empireState: Sketch.() -> Unit = {
        rect(0f, 0.46f, 1f, 1f)
        rect(0.12f, 0.3f, 0.88f, 0.47f)
        rect(0.22f, 0.2f, 0.78f, 0.31f)
        rect(0.32f, 0.12f, 0.68f, 0.21f)
        rect(0.42f, 0.06f, 0.58f, 0.13f)
        rect(0.48f, 0f, 0.52f, 0.07f)
        for (xx in floatArrayOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f)) line(xx, 0.5f, xx, 0.98f, 0.05f, cut)
        for (xx in floatArrayOf(0.35f, 0.5f, 0.65f)) line(xx, 0.33f, xx, 0.45f, 0.05f, cut)
    }

    val howrahBridge: Sketch.() -> Unit = {
        rect(0f, 0.74f, 1f, 0.8f)
        for (xs in floatArrayOf(0.24f, 0.72f)) {
            poly(xs, 0.8f, xs + 0.04f, 0.8f, xs + 0.045f, 0.06f, xs - 0.005f, 0.06f)
            rect(xs - 0.01f, 0.8f, xs + 0.05f, 1f)
        }
        // Cantilever top chord: the bridge's "M".
        stroke(0.06f) { m(0f, 0.72f); l(0.26f, 0.06f); l(0.5f, 0.48f); l(0.74f, 0.06f); l(1f, 0.72f) }
        for (xx in floatArrayOf(0.08f, 0.16f, 0.34f, 0.42f, 0.58f, 0.66f, 0.84f, 0.92f)) {
            val topY = when {
                xx < 0.26f -> 0.72f - (xx / 0.26f) * 0.66f
                xx < 0.5f -> 0.06f + ((xx - 0.26f) / 0.24f) * 0.42f
                xx < 0.74f -> 0.48f - ((xx - 0.5f) / 0.24f) * 0.42f
                else -> 0.06f + ((xx - 0.74f) / 0.26f) * 0.66f
            }
            line(xx, topY, xx, 0.74f, 0.025f)
        }
    }

    val burjKhalifa: Sketch.() -> Unit = {
        rect(0f, 0.78f, 1f, 1f)
        rect(0.1f, 0.6f, 0.9f, 0.79f)
        rect(0.2f, 0.44f, 0.8f, 0.61f)
        rect(0.3f, 0.3f, 0.7f, 0.45f)
        rect(0.38f, 0.19f, 0.62f, 0.31f)
        rect(0.44f, 0.1f, 0.56f, 0.2f)
        poly(0.46f, 0.11f, 0.54f, 0.11f, 0.5f, 0f)
        line(0.5f, 0.2f, 0.5f, 0.98f, 0.12f, cut)
    }

    val goldenGate: Sketch.() -> Unit = {
        rect(0f, 0.68f, 1f, 0.73f)
        for (xs in floatArrayOf(0.24f, 0.72f)) {
            rect(xs, 0.02f, xs + 0.04f, 0.96f)
            for (yy in floatArrayOf(0.12f, 0.3f, 0.5f)) rect(xs + 0.008f, yy, xs + 0.032f, yy + 0.08f, cut)
        }
        stroke(0.03f) { m(0f, 0.44f); q(0.13f, 0.4f, 0.26f, 0.03f); q(0.5f, 1.1f, 0.74f, 0.03f); q(0.87f, 0.4f, 1f, 0.44f) }
        for (i in 1 until 24) {
            val xx = i / 24f
            val cy = cableY(xx)
            if (cy < 0.68f) line(xx, cy, xx, 0.68f, 0.008f)
        }
    }

    /** Approximate main-cable height for the Golden Gate suspenders. */
    private fun cableY(x: Float): Float = when {
        x < 0.26f -> 0.44f - (x / 0.26f) * 0.41f
        x < 0.74f -> { val t = (x - 0.5f) / 0.24f; 0.565f - (0.535f) * t * t }
        else -> 0.03f + ((x - 0.74f) / 0.26f) * 0.41f
    }

    /** Marine Drive, the "Queen's Necklace": a curved promenade dotted with lights. */
    val marineDrive: Sketch.() -> Unit = {
        stroke(0.16f) { m(0.01f, 0.1f); c(0.2f, 0.95f, 0.75f, 0.98f, 0.99f, 0.3f) }
        for (i in 0..16) {
            val t = i / 16f
            val u = 1 - t
            val px = u * u * u * 0.01f + 3 * u * u * t * 0.2f + 3 * u * t * t * 0.75f + t * t * t * 0.99f
            val py = u * u * u * 0.1f + 3 * u * u * t * 0.95f + 3 * u * t * t * 0.98f + t * t * t * 0.3f
            circle(px, py, 0.035f, cut)
        }
    }

    val seaLink: Sketch.() -> Unit = {
        rect(0f, 0.7f, 1f, 0.75f)
        for (xs in floatArrayOf(0.36f, 0.62f)) {
            poly(xs - 0.03f, 0.75f, xs, 0.04f, xs + 0.03f, 0.75f)
            rect(xs - 0.02f, 0.75f, xs + 0.02f, 1f)
            for (i in 1..6) {
                line(xs, 0.08f + i * 0.03f, xs - i * 0.04f, 0.7f, 0.01f)
                line(xs, 0.08f + i * 0.03f, xs + i * 0.04f, 0.7f, 0.01f)
            }
        }
        for (xx in floatArrayOf(0.1f, 0.85f)) rect(xx, 0.75f, xx + 0.02f, 1f)
    }

    val everest: Sketch.() -> Unit = {
        poly(0f, 1f, 0.2f, 0.58f, 0.3f, 0.66f, 0.52f, 0.04f, 0.66f, 0.38f, 0.78f, 0.28f, 1f, 1f)
        poly(0.44f, 0.27f, 0.52f, 0.04f, 0.6f, 0.22f, 0.56f, 0.2f, 0.53f, 0.28f, 0.49f, 0.22f, color = cut)
        poly(0.74f, 0.37f, 0.78f, 0.28f, 0.83f, 0.42f, 0.79f, 0.39f, color = cut)
        poly(0.16f, 0.66f, 0.2f, 0.58f, 0.25f, 0.62f, color = cut)
    }

    /** A journey: start dot, dashed route, map pin at the destination. */
    val route: Sketch.() -> Unit = {
        stroke(0.07f, dashed = true) { m(0.06f, 0.75f); c(0.3f, 0.1f, 0.62f, 1.05f, 0.9f, 0.42f) }
        circle(0.06f, 0.75f, 0.08f); circle(0.06f, 0.75f, 0.035f, cut)
        shape { m(0.9f, 0.48f); l(0.85f, 0.26f); l(0.95f, 0.26f); close() }
        circle(0.9f, 0.22f, 0.18f); circle(0.9f, 0.22f, 0.07f, cut)
    }

    val earth: Sketch.() -> Unit = {
        circle(0.5f, 0.5f, 0.5f)
        val s = 0.022f
        line(0f, 0.5f, 1f, 0.5f, s, cut)
        line(0.07f, 0.28f, 0.93f, 0.28f, s, cut); line(0.07f, 0.72f, 0.93f, 0.72f, s, cut)
        line(0.5f, 0f, 0.5f, 1f, s, cut)
        stroke(s, cut) { addOval(androidx.compose.ui.geometry.Rect(x(0.28f), y(0f), x(0.72f), y(1f))) }
        stroke(s, cut) { addOval(androidx.compose.ui.geometry.Rect(x(0.1f), y(0f), x(0.9f), y(1f))) }
    }

    val moonTrip: Sketch.() -> Unit = {
        stroke(0.06f, dashed = true) { m(0.17f, 0.4f); q(0.5f, -0.1f, 0.84f, 0.32f) }
        circle(0.12f, 0.6f, 0.38f)
        line(0.04f, 0.52f, 0.2f, 0.52f, 0.04f, cut); line(0.03f, 0.68f, 0.21f, 0.68f, 0.04f, cut)
        circle(0.9f, 0.4f, 0.2f)
        circle(0.87f, 0.34f, 0.05f, cut); circle(0.93f, 0.46f, 0.04f, cut); circle(0.86f, 0.48f, 0.03f, cut)
    }
}
