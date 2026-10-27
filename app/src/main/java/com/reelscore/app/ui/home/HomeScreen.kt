package com.reelscore.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.account.Account
import com.reelscore.app.account.Friend
import com.reelscore.app.account.Friends
import com.reelscore.app.account.Period
import com.reelscore.app.account.Profile
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.detect.DetectorRules
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.distance.ScrollDistance
import com.reelscore.app.distance.compare
import com.reelscore.app.journey.JourneyStore
import com.reelscore.app.journey.formatPercent
import com.reelscore.app.settings.AppSettings
import com.reelscore.app.ui.components.AppIcon
import com.reelscore.app.ui.components.Avatar
import com.reelscore.app.ui.components.Card
import com.reelscore.app.ui.components.CardChevron
import com.reelscore.app.ui.components.CardGap
import com.reelscore.app.ui.components.DayLine
import com.reelscore.app.ui.components.HeatStrip
import com.reelscore.app.ui.components.Label
import com.reelscore.app.ui.components.ProgressLine
import com.reelscore.app.ui.components.RollingNumber
import com.reelscore.app.ui.components.SharedScopes
import com.reelscore.app.ui.components.StatTile
import com.reelscore.app.ui.components.StatusTag
import com.reelscore.app.ui.components.TileRow
import com.reelscore.app.ui.components.enter
import com.reelscore.app.ui.components.rememberCounterEnabled
import com.reelscore.app.ui.components.sharedAppContainer
import com.reelscore.app.ui.components.sharedAppIcon
import com.reelscore.app.ui.components.tabContentPadding
import com.reelscore.app.ui.theme.scoreStyle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The dashboard: today's count with how the day built up, then tiles that each open a full page.
 * Everything here is read from the phone; only the friends strip reads friends' shared totals.
 */
@Composable
fun HomeScreen(
    scopes: SharedScopes,
    onOpenApp: (String) -> Unit,
    onTurnOn: () -> Unit,
    onOpenDistance: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenYou: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { ReelRepository.get(context) }
    val enabled = rememberCounterEnabled()
    val counter by remember { AppSettings.observeCounter(context) }.collectAsStateWithLifecycle(AppSettings.counter(context))
    val limit by remember { AppSettings.observeLimit(context) }.collectAsStateWithLifecycle(AppSettings.limit(context))
    val journey by remember { JourneyStore.observe(context) }.collectAsStateWithLifecycle(JourneyStore.active(context))
    val today by remember { repo.total(ReelRepository.Period.TODAY) }.collectAsStateWithLifecycle(0)
    val yesterday by remember { repo.yesterdayTotal() }.collectAsStateWithLifecycle(null)
    val byHour by remember { repo.todayByHour() }.collectAsStateWithLifecycle(List(24) { 0 })
    val byApp by remember { repo.countsByApp(ReelRepository.Period.TODAY) }.collectAsStateWithLifecycle(emptyList())
    val user by remember { Account.user() }.collectAsStateWithLifecycle(Account.auth.currentUser)
    val now = LocalTime.now()
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    val meters = ScrollDistance.meters(today, context)
    val comparison = remember(meters) { compare(meters, LocalDate.now().toEpochDay()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabContentPadding()),
    ) {
        Row(Modifier.enter(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(date)
                Text(greeting(now), style = MaterialTheme.typography.headlineMedium)
            }
            Avatar(
                Account.username(context) ?: "You",
                44.dp,
                Modifier.clip(CircleShape).clickable(onClickLabel = "Open You", onClick = onOpenYou),
                you = true,
            )
        }
        Spacer(Modifier.height(20.dp))

        if (!enabled) {
            CounterOff(onTurnOn, Modifier.enter(1).padding(bottom = CardGap))
        }

        Card(Modifier.fillMaxWidth().enter(1), onClick = onOpenInsights, onClickLabel = "Open insights") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Reels today", Modifier.weight(1f))
                val status = when {
                    !enabled -> "Off" to false
                    !counter.enabled -> "Paused" to false
                    counter.scheduleOn && !counter.isActive() -> "Outside set hours" to false
                    else -> "Counting" to true
                }
                StatusTag(status.first, status.second)
            }
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            // Side by side only when there's room: small phones, big counts or large text stack instead.
            val stacked = config.screenWidthDp < 400 && "$today".length >= 3 || config.fontScale > 1.15f
            val versus = comparison(today, yesterday)
            if (stacked) {
                RollingNumber(today, scoreStyle("$today".length), Modifier.padding(top = 8.dp), MaterialTheme.colorScheme.onSurface)
                versus?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    RollingNumber(today, scoreStyle("$today".length), Modifier.weight(1f), MaterialTheme.colorScheme.onSurface)
                    versus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 14.dp).width(110.dp),
                        )
                    }
                }
            }
            DayLine(byHour, now, Modifier.padding(top = 10.dp))
            if (limit.on) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("Daily limit", Modifier.weight(1f))
                    Text("$today of ${limit.count}", style = MaterialTheme.typography.labelLarge)
                }
                ProgressLine(today.toFloat() / limit.count, Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(CardGap))
        TileRow(Modifier.enter(2)) {
            StatTile(
                label = "Scrolled",
                value = DistanceFormat.format(meters),
                modifier = Modifier.weight(1f),
                caption = comparison?.text ?: "No reels yet today",
                onClick = onOpenDistance,
                visual = {
                    val fraction = comparison?.let { (meters / it.thing.meters).toFloat() } ?: 0f
                    ProgressLine(fraction)
                },
            )
            val peak = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 }
            StatTile(
                label = "Busiest hour",
                value = peak?.let { hourLabel(it) } ?: "None yet",
                modifier = Modifier.weight(1f),
                caption = peak?.let { "${byHour[it]} reels" } ?: "Watch a few reels first",
                onClick = onOpenInsights,
                // Eight 3-hour blocks so the strip reads at half width.
                visual = { HeatStrip(byHour.chunked(3).map { it.sum() }) },
            )
        }

        val uid = user?.uid
        Spacer(Modifier.height(CardGap))
        if (uid == null) {
            Card(Modifier.fillMaxWidth().enter(3), onClick = onOpenFriends, onClickLabel = "Open friends") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compare with friends", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sign in to see where you stand. Only totals are shared.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CardChevron()
                }
            }
        } else {
            FriendsStrip(uid, today, onOpenFriends, Modifier.enter(3))
        }

        journey?.let { active ->
            val reels by remember(active.startedAt) { repo.totalSince(active.startedAt) }.collectAsStateWithLifecycle(0)
            val done = ScrollDistance.meters(reels, context).coerceAtMost(active.meters)
            Spacer(Modifier.height(CardGap))
            Card(Modifier.fillMaxWidth().enter(4), onClick = onOpenYou, onClickLabel = "Open journey") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Journey", Modifier.weight(1f))
                    Text(formatPercent(done / active.meters), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${active.from.city} → ${active.to.city}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                ProgressLine((done / active.meters).toFloat(), Modifier.padding(top = 12.dp))
                Text(
                    "${DistanceFormat.format(done)} of ${DistanceFormat.format(active.meters)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        val rows = byApp.sortedByDescending { it.count }
        Spacer(Modifier.height(CardGap))
        Card(Modifier.fillMaxWidth().enter(5), padding = 0.dp) {
            Label("By app", Modifier.padding(start = 18.dp, top = 18.dp))
            if (rows.isEmpty()) {
                Text(
                    "Reels you watch in Instagram, YouTube, Facebook and Snapchat show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp),
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rows.forEach { row ->
                        val label = DetectorRules.labelFor(row.pkg)
                        Column(
                            Modifier
                                .sharedAppContainer(scopes, row.pkg)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable(onClickLabel = "Open $label") { onOpenApp(row.pkg) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(row.pkg, label, 36.dp, Modifier.sharedAppIcon(scopes, row.pkg))
                            Text("${row.count}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Overlapping avatars and where you stand among friends today. */
@Composable
private fun FriendsStrip(uid: String, today: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val me by remember(uid) { Account.profile(uid) }.collectAsStateWithLifecycle(null)
    val friends by remember(uid) { Friends.friends(uid) }.collectAsStateWithLifecycle(emptyList())
    var profiles by remember { mutableStateOf<Map<Friend, Profile?>>(emptyMap()) }
    // Fetched when the friend list changes, not on every reel, to stay inside the free read quota.
    LaunchedEffect(friends) { profiles = runCatching { Friends.friendProfiles(friends) }.getOrDefault(emptyMap()) }
    val mine = me?.copy(today = today.toLong(), todayDate = LocalDate.now().toString(), weekStart = Friends.thisWeek())

    Card(modifier.fillMaxWidth(), onClick = onOpen, onClickLabel = "Open friends") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Friends today", Modifier.weight(1f))
            CardChevron()
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val shown = (listOfNotNull(mine?.username) + friends.map { it.name }).take(4)
            Box {
                shown.forEachIndexed { i, name ->
                    Avatar(
                        name,
                        34.dp,
                        Modifier
                            .offset(x = (i * 22).dp)
                            .zIndex((shown.size - i).toFloat())
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                            .padding(2.dp),
                        you = i == 0 && mine != null,
                    )
                }
            }
            Spacer(Modifier.width(22.dp * (shown.size.coerceAtLeast(1) - 1) + 14.dp))
            val text = when {
                mine == null -> "Pick a username to join in"
                friends.isEmpty() -> "Add a friend to compare"
                else -> {
                    val board = Friends.friendsBoard(mine, profiles, Period.TODAY, highFirst = true).filter { it.score != null }
                    val place = board.indexOfFirst { it.isYou } + 1
                    "You're ${ordinal(place)} of ${board.size} today"
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CounterOff(onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 4.dp, end = 12.dp).size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text("Counter is off", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Reels aren't being counted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Button(
                onClick = onTurnOn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) { Text("Turn on") }
        }
    }
}

private fun greeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

fun hourLabel(hour: Int): String = when (hour) {
    0 -> "12 am"
    12 -> "12 pm"
    in 1..11 -> "$hour am"
    else -> "${hour - 12} pm"
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

/**
 * Neutral comparison with yesterday: states the difference, never judges it.
 * Hidden when yesterday has no reels, since that usually means counting hadn't started yet.
 */
private fun comparison(today: Int, yesterday: Int?): String? = when {
    yesterday == null || yesterday == 0 -> null
    today > yesterday -> "${today - yesterday} more than yesterday"
    today < yesterday -> "${yesterday - today} fewer than yesterday"
    else -> "Same as yesterday"
}
