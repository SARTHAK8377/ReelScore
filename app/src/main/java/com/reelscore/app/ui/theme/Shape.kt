package com.reelscore.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** One radius system: 20 dp for large surfaces, 12 dp for small ones, full pill for chips and the nav. */
val ReelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Outer = 20.dp
private val Inner = 6.dp

/** Shape for row [index] of [count] in a grouped list: rounded ends, tight joins between rows. */
fun groupShape(index: Int, count: Int): RoundedCornerShape = when {
    count == 1 -> RoundedCornerShape(Outer)
    index == 0 -> RoundedCornerShape(Outer, Outer, Inner, Inner)
    index == count - 1 -> RoundedCornerShape(Inner, Inner, Outer, Outer)
    else -> RoundedCornerShape(Inner)
}
