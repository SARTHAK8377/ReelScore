package com.reelscore.app.ui.distance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.data.ReelRepository.Period
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.distance.ScrollDistance
import com.reelscore.app.distance.compare
import com.reelscore.app.distance.things
import com.reelscore.app.ui.components.Card
import com.reelscore.app.ui.components.CardGap
import com.reelscore.app.ui.components.Label
import com.reelscore.app.ui.components.PillTabs
import com.reelscore.app.ui.components.ScaleComparison
import com.reelscore.app.ui.components.SectionHeader
import com.reelscore.app.ui.components.StatTile
import com.reelscore.app.ui.components.TileRow
import com.reelscore.app.ui.components.enter
import com.reelscore.app.ui.settings.PageHeader
import java.time.LocalDate

/**
 * How far you've scrolled, drawn to scale next to the next object you haven't passed yet, and the
 * three after it.
 */
@Composable
fun DistanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ReelRepository.get(context) }
    val imperial = remember { DistanceFormat.imperial() }
    val today by remember { repo.total(Period.TODAY) }.collectAsStateWithLifecycle(0)
    val allTime by remember { repo.total(Period.ALL_TIME) }.collectAsStateWithLifecycle(0)
    var which by rememberSaveable { mutableIntStateOf(0) }
    val todayMeters = ScrollDistance.meters(today, context)
    val allMeters = ScrollDistance.meters(allTime, context)
    val meters = if (which == 0) todayMeters else allMeters
    val comparison = remember(meters) { compare(meters, LocalDate.now().toEpochDay()) }
    val upNext = remember(meters) { things.filter { it.meters > meters }.sortedBy { it.meters }.distinctBy { it.meters }.take(3) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        PageHeader("Distance", onBack)
        Spacer(Modifier.height(20.dp))
        TileRow(Modifier.enter(0)) {
            StatTile("Today", DistanceFormat.format(todayMeters, imperial), Modifier.weight(1f))
            StatTile("All time", DistanceFormat.format(allMeters, imperial), Modifier.weight(1f))
        }
        PillTabs(listOf("Today", "All time"), which, { which = it }, Modifier.padding(top = 16.dp).enter(1))
        Spacer(Modifier.height(CardGap))
        Card(Modifier.fillMaxWidth().enter(2)) {
            if (comparison == null) {
                Text(
                    "Watch a few reels to see how far you've scrolled.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Label("Compared with")
                Text(comparison.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp))
                ScaleComparison(
                    meters = meters,
                    thing = comparison.thing,
                    imperial = imperial,
                    modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 16.dp),
                )
            }
        }

        if (upNext.isNotEmpty()) {
            SectionHeader("Up next")
            Card(Modifier.fillMaxWidth().enter(3), padding = 0.dp) {
                upNext.forEachIndexed { index, thing ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(
                                if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(thing.one.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                DistanceFormat.format(thing.meters, imperial),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val left = thing.meters - meters
                        Text(
                            // Under a centimetre left rounds to "0 cm", which reads as already passed.
                            if (left < 0.01) "Almost there" else "in ${DistanceFormat.format(left, imperial)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (index < upNext.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Text(
            "Each reel counts as a quarter of your screen's height.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, bottom = 24.dp),
        )
    }
}
