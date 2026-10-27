package com.reelscore.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelscore.app.ui.components.RowGap
import com.reelscore.app.ui.theme.groupShape

private data class HelpTopic(val question: String, val answer: String)

/** Task-first answers: what to tap, in which situation. One or two sentences each. */
private val topics = listOf(
    HelpTopic(
        "Set up the counter",
        "On the You tab, tap Set up, then Continue to settings. Open Downloaded apps, tap ReelScore counter and turn it on.",
    ),
    HelpTopic(
        "ReelScore counter is greyed out",
        "On Android 13 and newer, apps installed from a file need one extra step. Open your phone's Settings, then Apps, then ReelScore. Tap ⋮ in the top corner, choose Allow restricted settings, then turn on ReelScore counter in Accessibility.",
    ),
    HelpTopic(
        "Pause counting for a while",
        "Flip the Counter switch on the You tab. While it's off, ReelScore uses no battery. Flip it again to resume.",
    ),
    HelpTopic(
        "Count only at certain times",
        "On the You tab, turn on Use set hours, then tap From and To to pick your times.",
    ),
    HelpTopic(
        "Set a daily limit",
        "On the You tab, turn on Alert me at a limit, then tap Limit to pick a number. It counts the whole day's reels, and a pop-up appears once when you reach it.",
    ),
    HelpTopic(
        "Start a journey",
        "On the You tab, tap Start a journey and pick two countries. Cities are optional between different countries (it measures from the capital), but needed within one country; in India, pick the state first. Every reel moves you a quarter of your screen's height, and a pop-up appears when you arrive.",
    ),
    HelpTopic(
        "How scroll distance works",
        "Each reel counts as a quarter of your screen's height. Trends shows today's and all-time distance next to the next object or landmark you haven't passed yet. Once you pass it, the picture moves on to the next bigger one.",
    ),
    HelpTopic(
        "A reel didn't count",
        "A reel counts after 5 seconds on screen, and each reel counts only once, ever. Also check the counter isn't paused or outside your set hours.",
    ),
    HelpTopic(
        "The counter stopped counting",
        "Your phone may have closed ReelScore to save battery. Next time you open ReelScore it will ask to keep running; tap Allow.",
    ),
    HelpTopic(
        "Turn it back on after turning it off completely",
        "Open Settings here, tap Accessibility, then turn ReelScore counter back on. Your counts are kept.",
    ),
    HelpTopic(
        "Which apps are counted",
        "Instagram Reels, YouTube Shorts, Facebook Reels, Snapchat Spotlight, Moj and Josh.",
    ),
    HelpTopic(
        "Add friends",
        "On the Friends tab, sign in with Google and pick a username. Then tap Add a friend and type their username. You'll see each other's scores once they accept.",
    ),
    HelpTopic(
        "Choose who sees your scores",
        "Open Settings, then Who can see your scores. Public shows them to anyone on ReelScore, Friends only to your friends, Private to no one.",
    ),
    HelpTopic(
        "Counter was off for part of the day",
        "Shown next to a score when the counter was paused, stopped or limited to set hours that day, so the number may be missing some reels.",
    ),
    HelpTopic(
        "Where your data is kept",
        "On this phone. If you sign in, only your username, privacy choice, friends and three totals (today, this week, all time) are stored online, in Google's Firebase. Single reels, apps and journeys never leave the phone. Delete account in Settings removes it all.",
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(-1) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        PageHeader("Help", onBack)
        Column(Modifier.padding(top = 20.dp)) {
            topics.forEachIndexed { index, topic ->
                HelpItem(
                    topic = topic,
                    expanded = open == index,
                    index = index,
                    count = topics.size,
                    onToggle = { open = if (open == index) -1 else index },
                )
                if (index < topics.lastIndex) RowGap()
            }
        }
    }
}

@Composable
private fun HelpItem(topic: HelpTopic, expanded: Boolean, index: Int, count: Int, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(topic.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn),
            )
        }
        AnimatedVisibility(expanded) {
            Text(
                topic.answer,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
