package com.reelscore.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import java.text.NumberFormat

/** A number whose digits roll like an odometer when it changes. Each digit animates on its own. */
@Composable
fun RollingNumber(
    value: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val text = NumberFormat.getIntegerInstance().format(value)
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { index, char ->
            // Keyed from the right, so the units digit stays the units digit when the number grows.
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val up = targetState > initialState
                        val motion = spring<IntOffset>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        (slideInVertically(motion) { h -> if (up) h else -h } + fadeIn()) togetherWith
                            (slideOutVertically(motion) { h -> if (up) -h else h } + fadeOut()) using
                            SizeTransform(clip = true)
                    },
                    label = "digit",
                ) { digit ->
                    Text(digit.toString(), style = style, color = color)
                }
            }
        }
    }
}
