package com.reelscore.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelscore.app.ui.theme.LocalBrandColors

data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val ItemHeight = 52.dp
private val IconSize = 24.dp
private val SidePadding = 18.dp
private val LabelGap = 8.dp
private val ItemSpacing = 2.dp
private val BarPadding = 6.dp

/**
 * A floating pill bar. The selected tab is a lime pill showing icon and label; the others are icons.
 *
 * Each tab has one animated progress value (0 = icon only, 1 = selected) that drives its width,
 * lime fill, label fade and icon colour together, so they can never drift apart. Target widths
 * come from the label text up front, not from measuring mid-animation, and the bar has a fixed
 * overall width, so nothing chases a moving target.
 */
@Composable
fun FloatingNavBar(
    tabs: List<NavTab>,
    selectedRoute: String,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = LocalBrandColors.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidths: Map<String, Dp> = remember(tabs, labelStyle, density) {
        tabs.associate { tab ->
            tab.route to with(density) { measurer.measure(tab.label, labelStyle, maxLines = 1).size.width.toDp() }
        }
    }
    val iconOnly = SidePadding * 2 + IconSize
    // Widest the row can get: every tab as an icon, plus the longest label expanded.
    val rowWidth = iconOnly * tabs.size + ItemSpacing * (tabs.size - 1) +
        LabelGap + (labelWidths.values.maxOrNull() ?: 0.dp)

    Surface(modifier = modifier, shape = CircleShape, color = brand.navBar, shadowElevation = 10.dp) {
        Row(
            Modifier
                .padding(BarPadding)
                .width(rowWidth)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab.route == selectedRoute
                val progress by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "navItem",
                )
                val labelWidth = labelWidths.getValue(tab.route)
                // progress is only read inside layout and draw lambdas, so each animation frame
                // re-lays-out and redraws this pill without recomposing anything.
                Box(
                    Modifier
                        .height(ItemHeight)
                        .layout { measurable, constraints ->
                            val width = (iconOnly + (LabelGap + labelWidth) * progress).roundToPx()
                            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                            layout(width, placeable.height) { placeable.place(0, 0) }
                        }
                        .clip(CircleShape)
                        .drawBehind { drawRect(brand.navIndicator, alpha = progress) }
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) }),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Laid out at full width and clipped by the pill, so the label is revealed, never reflowed.
                    Row(
                        Modifier
                            .padding(start = SidePadding)
                            .wrapContentWidth(align = Alignment.Start, unbounded = true),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Two icon layers crossfade from grey to dark, instead of recolouring per frame.
                        Box {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = brand.navContent,
                                modifier = Modifier
                                    .width(IconSize)
                                    .graphicsLayer { alpha = 1f - progress },
                            )
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                tint = brand.onNavIndicator,
                                modifier = Modifier
                                    .width(IconSize)
                                    .graphicsLayer { alpha = progress },
                            )
                        }
                        Spacer(Modifier.width(LabelGap))
                        Text(
                            text = tab.label,
                            color = brand.onNavIndicator,
                            style = labelStyle,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .graphicsLayer { alpha = progress }
                                .clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
    }
}
