package com.reelscore.app.journey

import android.content.Context
import androidx.core.content.edit
import com.reelscore.app.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * A journey end point, stored with its display names so it never needs the place list again.
 * For a country-only end point, [city] is the country's name and the coordinates are its capital.
 */
data class Place(val city: String, val region: String?, val country: String, val lat: Double, val lon: Double) {
    val label: String get() = listOfNotNull(city.takeIf { it != country }, region, country).joinToString(", ")
}

/**
 * One journey at a time, at real scale: progress is the scroll distance of every reel counted
 * since [startedAt]. Finished journeys move to the completed_journey table.
 */
data class Journey(val from: Place, val to: Place, val meters: Double, val startedAt: Long)

/** Long journeys move in tiny steps, so small percentages keep more decimals. */
fun formatPercent(fraction: Double): String {
    val pct = fraction * 100
    return when {
        pct <= 0.0 -> "0%"
        pct < 0.01 -> "<0.01%"
        pct < 1 -> "%.2f%%".format(pct)
        else -> "%.1f%%".format(pct)
    }
}

object JourneyStore {
    private const val KEY_FROM = "journey_from"
    private const val KEY_TO = "journey_to"
    private const val KEY_METERS = "journey_meters"
    private const val KEY_STARTED = "journey_started"
    private const val SEP = '\t'

    fun active(context: Context): Journey? {
        val prefs = AppSettings.prefs(context)
        val from = prefs.getString(KEY_FROM, null)?.let(::decode) ?: return null
        val to = prefs.getString(KEY_TO, null)?.let(::decode) ?: return null
        val meters = prefs.getString(KEY_METERS, null)?.toDoubleOrNull() ?: return null
        return Journey(from, to, meters, prefs.getLong(KEY_STARTED, System.currentTimeMillis()))
    }

    fun start(context: Context, from: Place, to: Place) = AppSettings.prefs(context).edit {
        putString(KEY_FROM, encode(from))
        putString(KEY_TO, encode(to))
        putString(KEY_METERS, distanceMeters(from.lat, from.lon, to.lat, to.lon).toString())
        putLong(KEY_STARTED, System.currentTimeMillis())
    }

    fun clear(context: Context) = AppSettings.prefs(context).edit {
        remove(KEY_FROM)
        remove(KEY_TO)
        remove(KEY_METERS)
        remove(KEY_STARTED)
    }

    fun observe(context: Context): Flow<Journey?> = AppSettings.observe(context) { active(it) }

    private fun encode(p: Place) = listOf(p.city, p.region.orEmpty(), p.country, p.lat, p.lon).joinToString(SEP.toString())

    private fun decode(s: String): Place? {
        val f = s.split(SEP)
        if (f.size != 5) return null
        return Place(f[0], f[1].ifEmpty { null }, f[2], f[3].toDoubleOrNull() ?: return null, f[4].toDoubleOrNull() ?: return null)
    }
}
