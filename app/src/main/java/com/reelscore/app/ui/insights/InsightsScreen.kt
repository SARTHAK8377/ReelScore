package com.reelscore.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.data.ReelRepository.Period
import com.reelscore.app.detect.AppRules
import com.reelscore.app.detect.DetectorRules
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.distance.ScrollDistance
import com.reelscore.app.journey.JourneyStore
import com.reelscore.app.ui.components.AppIcon
import com.reelscore.app.ui.components.BarChart
import com.reelscore.app.ui.components.CalendarHeatmap
import com.reelscore.app.ui.components.Card
import com.reelscore.app.ui.components.CardChevron
import com.reelscore.app.ui.components.CardGap
import com.reelscore.app.ui.components.HourHeatmap
import com.reelscore.app.ui.components.Label
import com.reelscore.app.ui.components.PillTabs
import com.reelscore.app.ui.components.RollingNumber
import com.reelscore.app.ui.components.SectionHeader
import com.reelscore.app.ui.components.SharedScopes
import com.reelscore.app.ui.components.Sparkline
import com.reelscore.app.ui.components.StatTile
import com.reelscore.app.ui.components.TileRow
import com.reelscore.app.ui.components.enter
import com.reelscore.app.ui.components.sharedAppContainer
import com.reelscore.app.ui.components.sharedAppIcon
import com.reelscore.app.ui.components.supportedApps
import com.reelscore.app.ui.components.tabContentPadding
import com.reelscore.app.ui.home.hourLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

private val ranges = listOf(7 to "Week", 30 to "Month")

/** Trends and Apps in one place: totals, when you watch, a month calendar and each app's trend. */
@Composable
fun InsightsScreen(scopes: SharedScopes, onOpenApp: (String) -> Unit, onOpenDistance: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ReelRepository.get(context) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    val range = ranges[rangeIndex].first
    val days by remember(range) { repo.dailyCounts(range) }.collectAsStateWithLifecycle(emptyList())
    val week by remember { repo.total(Period.WEEK) }.collectAsStateWithLifecycle(0)
    val allTime by remember { repo.total(Period.ALL_TIME) }.collectAsStateWithLifecycle(0)
    val today by remember { repo.total(Period.TODAY) }.collectAsStateWithLifecycle(0)
    val grid by remember { repo.weekHourGrid() }.collectAsStateWithLifecycle(List(7) { List(24) { 0 } })
    val journey by remember { JourneyStore.observe(context) }.collectAsStateWithLifecycle(JourneyStore.active(context))
    val journeyDays by remember(range, journey?.startedAt) {
        journey?.let { repo.dailyCounts(range, notBefore = it.startedAt) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    // Percentage of the whole journey covered on each day of the range.
    val journeyShare = journey?.let { j -> journeyDays.map { (ScrollDistance.meters(it.count, context) / j.meters * 100).toFloat() } }

    val total = days.sumOf { it.count }
    val average = if (days.isEmpty()) 0 else (total.toFloat() / days.size).roundToInt()
    val busiest = days.maxByOrNull { it.count }?.takeIf { it.count > 0 }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabContentPadding()),
    ) {
        Text("Insights", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.enter(0))
        PillTabs(ranges.map { it.second }, rangeIndex, { rangeIndex = it }, Modifier.padding(top = 20.dp).enter(0))

        Card(Modifier.fillMaxWidth().padding(top = 12.dp).enter(1)) {
            Label(if (range == 7) "Reels in the last 7 days" else "Reels in the last 30 days")
            RollingNumber(total, MaterialTheme.typography.displayMedium, Modifier.padding(top = 4.dp, bottom = 16.dp))
            BarChart(days, journeyShare = journeyShare)
        }

        Spacer(Modifier.height(CardGap))
        TileRow(Modifier.enter(2)) {
            StatTile("Daily average", "$average", Modifier.weight(1f))
            StatTile(
                "Busiest day",
                busiest?.let { LocalDate.parse(it.day).format(DateTimeFormatter.ofPattern("EEE d")) } ?: "None yet",
                Modifier.weight(1f),
                caption = busiest?.let { "${it.count} reels" },
            )
        }
        Spacer(Modifier.height(CardGap))
        TileRow(Modifier.enter(3)) {
            StatTile("This week", "$week", Modifier.weight(1f))
            StatTile("All time", "$allTime", Modifier.weight(1f))
        }

        SectionHeader("When you watch")
        Card(Modifier.fillMaxWidth()) {
            Label("Last 4 weeks, by hour")
            HourHeatmap(grid, Modifier.padding(top = 14.dp))
            val byHour = List(24) { h -> grid.sumOf { it[h] } }
            val top = byHour.indices.filter { byHour[it] > 0 }.sortedByDescending { byHour[it] }.take(3)
            if (top.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Label("Busiest hours")
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    top.forEachIndexed { i, h ->
                        Column(Modifier.weight(1f)) {
                            Text(hourLabel(h), style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${byHour[h]} reels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        SectionHeader("Calendar")
        MonthCard(repo)

        SectionHeader("Apps")
        AppsCard(repo, scopes, onOpenApp)

        SectionHeader("Distance")
        val meters = ScrollDistance.meters(allTime, context)
        Card(Modifier.fillMaxWidth(), onClick = onOpenDistance, onClickLabel = "Open distance") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Scrolled, all time", Modifier.weight(1f))
                CardChevron()
            }
            Text(DistanceFormat.format(meters), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Today ${DistanceFormat.format(ScrollDistance.meters(today, context))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A month of days as heat cells, with arrows to step months and the chosen day's apps below. */
@Composable
private fun MonthCard(repo: ReelRepository) {
    val thisMonth = remember { YearMonth.now() }
    var month by remember { mutableStateOf(thisMonth) }
    var selected by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    val span = ChronoUnit.DAYS.between(month.atDay(1), LocalDate.now()).toInt() + 1
    val counts by remember(month) { repo.dailyCounts(span) }.collectAsStateWithLifecycle(emptyList())
    val byDay = counts.associate { it.day to it.count }
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = month.minusMonths(1); selected = null }) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            IconButton(onClick = { month = month.plusMonths(1); selected = null }, enabled = month < thisMonth) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        CalendarHeatmap(month, byDay, selected, { selected = it }, Modifier.padding(top = 8.dp))
        selected?.let { day ->
            val apps by remember(day) { repo.countsByAppOn(day) }.collectAsStateWithLifecycle(emptyList())
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), Modifier.weight(1f))
                Text("${byDay[day.toString()] ?: 0} reels", style = MaterialTheme.typography.titleMedium)
            }
            apps.sortedByDescending { it.count }.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val label = DetectorRules.labelFor(app.pkg)
                    AppIcon(app.pkg, label, 24.dp)
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text("${app.count}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Every supported app with a 7-day trend line and its totals. */
@Composable
private fun AppsCard(repo: ReelRepository, scopes: SharedScopes, onOpenApp: (String) -> Unit) {
    val context = LocalContext.current
    // Package lookups are IPC calls; keep them off the UI thread.
    val split by produceState(DetectorRules.apps to emptyList<AppRules>()) {
        value = withContext(Dispatchers.IO) { supportedApps(context) }
    }
    val (installed, missing) = split
    val week by remember { repo.countsByApp(Period.WEEK) }.collectAsStateWithLifecycle(emptyList())
    val allTime by remember { repo.countsByApp(Period.ALL_TIME) }.collectAsStateWithLifecycle(emptyList())
    val weekByPkg = week.associate { it.pkg to it.count }
    val allByPkg = allTime.associate { it.pkg to it.count }
    // Apps with history stay listed even if they've since been uninstalled.
    val apps = (installed + missing.filter { (allByPkg[it.pkg] ?: 0) > 0 }).sortedByDescending { allByPkg[it.pkg] ?: 0 }
    val absent = missing.filter { it !in apps }

    Card(Modifier.fillMaxWidth(), padding = 0.dp) {
        apps.forEachIndexed { index, app ->
            val trend by remember(app.pkg) { repo.dailyCountsForApp(app.pkg, 7) }.collectAsStateWithLifecycle(emptyList())
            Row(
                Modifier
                    .fillMaxWidth()
                    .sharedAppContainer(scopes, app.pkg)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(onClickLabel = "Open ${app.label}") { onOpenApp(app.pkg) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.pkg, app.label, 40.dp, Modifier.sharedAppIcon(scopes, app.pkg))
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${weekByPkg[app.pkg] ?: 0} this week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Sparkline(trend.map { it.count }, Modifier.width(56.dp).height(24.dp))
                Column(Modifier.padding(start = 16.dp).width(52.dp), horizontalAlignment = Alignment.End) {
                    Text("${allByPkg[app.pkg] ?: 0}", style = MaterialTheme.typography.titleMedium)
                    Text("all time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (index < apps.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (absent.isNotEmpty()) {
            Text(
                "${absent.joinToString(" and ") { it.label }} ${if (absent.size == 1) "isn't" else "aren't"} installed. " +
                    "ReelScore counts ${if (absent.size == 1) "it" else "them"} automatically once installed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp),
            )
        }
    }
}
