package com.reelscore.app.journey

import android.content.Context
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Country(val iso: String, val name: String, val capital: String)

data class Region(val iso: String, val code: String, val name: String)

data class City(val name: String, val iso: String, val regionCode: String, val lat: Double, val lon: Double)

/**
 * Countries, states and cities bundled in assets/geo (built by design/build_geo.py from GeoNames,
 * CC BY 4.0). Read once on a background thread and kept in memory; nothing goes online.
 */
class GeoData(
    val countries: List<Country>,
    private val regions: Map<String, List<Region>>,
    private val cities: Map<String, List<City>>,
) {
    private val regionNames = regions.values.flatten().associate { (it.iso to it.code) to it.name }

    fun regions(iso: String): List<Region> = regions[iso].orEmpty()

    /** Cities in a country, biggest first; narrowed to one state when [regionCode] is given. */
    fun cities(iso: String, regionCode: String?): List<City> =
        cities[iso].orEmpty().let { list -> if (regionCode == null) list else list.filter { it.regionCode == regionCode } }

    fun regionName(iso: String, code: String): String? = regionNames[iso to code]

    fun countryName(iso: String): String = countries.firstOrNull { it.iso == iso }?.name ?: iso

    /**
     * Where a country-only journey end point is measured from: its capital, or its biggest city
     * when the capital isn't in the list.
     */
    fun countryPoint(iso: String): City? {
        val capital = countries.firstOrNull { it.iso == iso }?.capital.orEmpty()
        val all = cities(iso, null)
        return all.firstOrNull { capital.isNotEmpty() && it.name == capital } ?: all.firstOrNull()
    }
}

object Geo {
    @Volatile private var loaded: GeoData? = null

    suspend fun load(context: Context): GeoData = loaded ?: withContext(Dispatchers.IO) {
        loaded ?: read(context).also { loaded = it }
    }

    private fun read(context: Context): GeoData {
        fun rows(name: String) = context.assets.open("geo/$name").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { it.split('\t') }.toList()
        }
        val countries = rows("countries.tsv").map { Country(it[0], it[1], it.getOrElse(2) { "" }) }
        val regions = rows("states.tsv").map { Region(it[0], it[1], it[2]) }.groupBy { it.iso }
        val cities = rows("cities.tsv").map { City(it[0], it[1], it[2], it[3].toDouble(), it[4].toDouble()) }.groupBy { it.iso }
        return GeoData(countries, regions, cities)
    }
}

private const val EARTH_RADIUS_M = 6_371_000.0

/** Straight-line (great-circle) distance in metres. */
fun distanceMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}
