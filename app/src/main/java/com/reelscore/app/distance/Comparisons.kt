package com.reelscore.app.distance

import androidx.annotation.DrawableRes
import com.reelscore.app.R
import kotlin.math.roundToInt

/**
 * Something to compare a distance with. [one] reads after "62% of", [many] after a count.
 * [meters] is its height when [tall], otherwise its length. [aspect] is the drawing's width / height.
 * [picture] is a Canva illustration (res/drawable-nodpi, cut by design/cut_pictures.ps1); when set
 * it's drawn instead of [art], at its own aspect ratio.
 */
data class Thing(
    val one: String,
    val many: String,
    val meters: Double,
    val tall: Boolean,
    val aspect: Float,
    val art: Sketch.() -> Unit,
    @DrawableRes val picture: Int? = null,
)

data class Comparison(val text: String, val thing: Thing)

private fun long(one: String, many: String, meters: Double, aspect: Float, art: Sketch.() -> Unit, @DrawableRes picture: Int? = null) =
    Thing(one, many, meters, tall = false, aspect = aspect, art = art, picture = picture)

private fun tall(one: String, many: String, meters: Double, aspect: Float, art: Sketch.() -> Unit, @DrawableRes picture: Int? = null) =
    Thing(one, many, meters, tall = true, aspect = aspect, art = art, picture = picture)

/** Everyday objects, then landmarks, then journeys. Approximate public figures. */
val things = listOf(
    tall("a phone", "phones", 0.16, 0.48f, Art.phone, R.drawable.pic_phone),
    tall("a cricket bat", "cricket bats", 0.96, 0.18f, Art.bat, R.drawable.pic_bat),
    tall("a door", "doors", 2.0, 0.45f, Art.door, R.drawable.pic_door),
    long("an auto-rickshaw", "auto-rickshaws", 2.6, 1.35f, Art.autoRickshaw, R.drawable.pic_auto_rickshaw),
    long("a car", "cars", 4.5, 2.6f, Art.car, R.drawable.pic_car),
    long("an elephant", "elephants", 6.0, 1.35f, Art.elephant, R.drawable.pic_elephant),
    long("a bus", "buses", 12.0, 3.4f, Art.bus, R.drawable.pic_bus),
    // Drawn taller than life so the stumps read; only the length is compared.
    long("a cricket pitch", "cricket pitches", 20.12, 3.6f, Art.cricketPitch, R.drawable.pic_cricket_pitch),
    long("a train coach", "train coaches", 23.0, 6.0f, Art.trainCoach, R.drawable.pic_train_coach),
    long("a tennis court", "tennis courts", 23.77, 2.17f, Art.tennisCourt, R.drawable.pic_tennis_court),
    long("a blue whale", "blue whales", 25.0, 4.5f, Art.blueWhale, R.drawable.pic_blue_whale),
    tall("the Gateway of India", "Gateways of India", 26.0, 1.0f, Art.gatewayOfIndia),
    long("a basketball court", "basketball courts", 28.0, 1.87f, Art.basketballCourt, R.drawable.pic_basketball_court),
    tall("India Gate", "India Gates", 42.0, 0.75f, Art.indiaGate),
    long("an Olympic pool", "Olympic pools", 50.0, 2.0f, Art.pool, R.drawable.pic_pool),
    tall("the Leaning Tower of Pisa", "Leaning Towers of Pisa", 56.0, 0.45f, Art.pisa),
    tall("the Qutub Minar", "Qutub Minars", 73.0, 0.22f, Art.qutubMinar),
    tall("the Taj Mahal", "Taj Mahals", 73.0, 1.9f, Art.tajMahal),
    tall("the Statue of Liberty", "Statues of Liberty", 93.0, 0.42f, Art.statueOfLiberty),
    tall("Big Ben", "Big Bens", 96.0, 0.24f, Art.bigBen),
    long("a football field", "football fields", 105.0, 1.55f, Art.footballField, R.drawable.pic_football_field),
    tall("the Statue of Unity", "Statues of Unity", 182.0, 0.42f, Art.statueOfUnity),
    tall("the Eiffel Tower", "Eiffel Towers", 330.0, 0.42f, Art.eiffelTower),
    tall("the Empire State Building", "Empire State Buildings", 443.0, 0.3f, Art.empireState),
    long("Howrah Bridge", "Howrah Bridges", 705.0, 4.5f, Art.howrahBridge),
    tall("the Burj Khalifa", "Burj Khalifas", 828.0, 0.17f, Art.burjKhalifa),
    long("the Golden Gate Bridge", "Golden Gate Bridges", 2_737.0, 5.5f, Art.goldenGate),
    long("Marine Drive", "Marine Drives", 3_600.0, 3.0f, Art.marineDrive),
    long("the Bandra-Worli Sea Link", "Bandra-Worli Sea Links", 5_600.0, 6.0f, Art.seaLink),
    tall("Mount Everest", "Mount Everests", 8_849.0, 1.7f, Art.everest),
    long("a marathon", "marathons", 42_195.0, 3.0f, Art.route),
    long("Mumbai to Pune", "trips from Mumbai to Pune", 120_000.0, 3.0f, Art.route),
    long("Delhi to Agra", "trips from Delhi to Agra", 180_000.0, 3.0f, Art.route),
    long("Delhi to Mumbai", "trips from Delhi to Mumbai", 1_150_000.0, 3.0f, Art.route),
    long("the length of India", "lengths of India", 3_214_000.0, 3.0f, Art.route),
    long("London to New York", "trips from London to New York", 5_570_000.0, 3.0f, Art.route),
    long("a trip around the Earth", "trips around the Earth", 40_075_000.0, 1.0f, Art.earth),
    long("a trip to the Moon", "trips to the Moon", 384_400_000.0, 3.5f, Art.moonTrip),
)

/**
 * Compares [meters] with the smallest thing still bigger than it, so the comparison moves on to
 * the next thing once you pass one. Past the biggest, it counts how many of it you've covered.
 * Things of the same size take turns by [day].
 */
fun compare(meters: Double, day: Long): Comparison? {
    if (meters <= 0) return null
    val next = things.filter { it.meters > meters }.minOfOrNull { it.meters }
    if (next == null) {
        val biggest = things.maxBy { it.meters }
        return Comparison("About ${formatRatio(meters / biggest.meters)} ${biggest.many}", biggest)
    }
    val ties = things.filter { it.meters == next }
    val thing = ties[Math.floorMod(day, ties.size.toLong()).toInt()]
    val percent = (meters / thing.meters * 100).toInt().coerceIn(1, 99)
    return Comparison("$percent% of ${thing.one}", thing)
}

/** Halves below 10 ("1.5", "3"), whole numbers above ("12", "1,250"). */
private fun formatRatio(ratio: Double): String =
    if (ratio < 10) {
        val half = (ratio * 2).roundToInt() / 2.0
        if (half % 1.0 == 0.0) half.toInt().toString() else half.toString()
    } else {
        "%,d".format(ratio.roundToInt())
    }
