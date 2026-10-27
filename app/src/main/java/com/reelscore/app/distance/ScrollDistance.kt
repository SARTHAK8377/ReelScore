package com.reelscore.app.distance

import android.content.Context
import android.view.WindowManager
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns reels into distance: each reel counts as a quarter of the screen's physical height. */
object ScrollDistance {
    private const val METERS_PER_INCH = 0.0254
    private const val FALLBACK_SCREEN_M = 0.16
    private const val MIN_SCREEN_M = 0.08
    private const val MAX_SCREEN_M = 0.40

    @Volatile private var cached: Double? = null

    fun metersPerReel(context: Context): Double = cached ?: (screenHeightMeters(context) / 4.0).also { cached = it }

    fun meters(reels: Int, context: Context): Double = reels * metersPerReel(context)

    /**
     * Uses the panel's physical pixels and dpi, not the app's window size, which the phone's
     * "display size" setting scales. Falls back to a typical 16 cm phone if the numbers look wrong.
     */
    @Suppress("DEPRECATION")
    private fun screenHeightMeters(context: Context): Double {
        val metrics = context.resources.displayMetrics
        val mode = runCatching { context.getSystemService(WindowManager::class.java).defaultDisplay.mode }.getOrNull()
        val heightPx = max(mode?.physicalHeight ?: metrics.heightPixels, mode?.physicalWidth ?: metrics.widthPixels)
        val meters = heightPx / metrics.ydpi.toDouble() * METERS_PER_INCH
        return if (meters in MIN_SCREEN_M..MAX_SCREEN_M) meters else FALLBACK_SCREEN_M
    }
}

/** Metric everywhere except the countries that use feet and miles day to day. */
object DistanceFormat {
    private val imperialCountries = setOf("US", "LR", "MM")

    fun imperial(locale: Locale = Locale.getDefault()): Boolean = locale.country in imperialCountries

    fun format(meters: Double, imperial: Boolean = imperial()): String =
        if (imperial) formatImperial(meters) else formatMetric(meters)

    private fun formatMetric(m: Double): String = when {
        m < 1 -> "${(m * 100).roundToInt()} cm"
        m < 10 -> "%.1f m".format(m)
        m < 1_000 -> "${m.roundToInt()} m"
        m < 10_000 -> "%.2f km".format(m / 1_000)
        m < 100_000 -> "%.1f km".format(m / 1_000)
        else -> "%,d km".format((m / 1_000).roundToInt())
    }

    private fun formatImperial(m: Double): String {
        val feet = m * 3.28084
        val miles = feet / 5_280
        return when {
            feet < 1 -> "${(feet * 12).roundToInt()} in"
            feet < 10 -> "%.1f ft".format(feet)
            feet < 5_280 -> "%,d ft".format(feet.roundToInt())
            miles < 10 -> "%.2f mi".format(miles)
            miles < 100 -> "%.1f mi".format(miles)
            else -> "%,d mi".format(miles.roundToInt())
        }
    }
}
