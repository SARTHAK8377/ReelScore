package com.reelscore.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.reelscore.app.R

/** Bricolage Grotesque is a variable font; the optical-size axis gives big numbers tighter, punchier shapes. */
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, opticalSize: Float) = Font(
    resId = R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

/** Score numbers and display sizes. */
val DisplayFamily = FontFamily(bricolage(600, 96f), bricolage(700, 96f))

/** Screen titles and section headings. */
val TitleFamily = FontFamily(bricolage(600, 24f), bricolage(700, 24f))

private const val TABULAR = "tnum"
private val base = Typography()

val ReelTypography = Typography(
    displayLarge = base.displayLarge.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR,
    ),
    displayMedium = base.displayMedium.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR,
    ),
    displaySmall = base.displaySmall.copy(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR,
    ),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = TitleFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = TitleFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = TitleFamily, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR,
    ),
    titleLarge = base.titleLarge.copy(fontFamily = TitleFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR),
)

/** The big score on Home's hero card. Shrinks as the number gains digits so it never wraps. */
fun scoreStyle(digits: Int): TextStyle {
    val size: TextUnit = when {
        digits <= 3 -> 104.sp
        digits == 4 -> 88.sp
        digits == 5 -> 72.sp
        else -> 60.sp
    }
    return TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size,
        letterSpacing = (-0.04).em,
        fontFeatureSettings = TABULAR,
        // Trim the font's built-in leading so the label below sits tight against the digits.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
}
