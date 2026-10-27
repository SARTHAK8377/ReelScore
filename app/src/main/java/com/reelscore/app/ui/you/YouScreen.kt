@file:OptIn(ExperimentalMaterial3Api::class)

package com.reelscore.app.ui.you

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.distance.ScrollDistance
import com.reelscore.app.journey.Journey
import com.reelscore.app.journey.JourneyStore
import com.reelscore.app.journey.formatPercent
import com.reelscore.app.settings.AppSettings
import com.reelscore.app.settings.CounterSettings
import com.reelscore.app.ui.theme.groupShape
import com.reelscore.app.settings.LimitSettings
import com.reelscore.app.account.Account
import com.reelscore.app.ui.components.Avatar
import com.reelscore.app.ui.components.CardShape
import com.reelscore.app.ui.components.NavRow
import com.reelscore.app.ui.components.ProgressLine
import com.reelscore.app.ui.components.StatusTag
import com.reelscore.app.ui.components.RowGap
import com.reelscore.app.ui.components.SectionTitle
import com.reelscore.app.ui.components.ToggleRow
import com.reelscore.app.ui.components.rememberCounterEnabled
import com.reelscore.app.ui.components.tabContentPadding
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun YouScreen(onOpenSettings: () -> Unit, onStartJourney: () -> Unit) {
    val context = LocalContext.current
    val serviceOn = rememberCounterEnabled()
    val settings by remember { AppSettings.observeCounter(context) }
        .collectAsStateWithLifecycle(AppSettings.counter(context))
    var showInfo by remember { mutableStateOf(false) }
    var infoLeadsToSetup by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimeField?>(null) }
    val limit by remember { AppSettings.observeLimit(context) }.collectAsStateWithLifecycle(AppSettings.limit(context))
    var editingLimit by remember { mutableStateOf(false) }
    val journey by remember { JourneyStore.observe(context) }.collectAsStateWithLifecycle(JourneyStore.active(context))
    var confirmEndJourney by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabContentPadding()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val user by remember { Account.user() }.collectAsStateWithLifecycle(Account.auth.currentUser)
            val name = if (user != null) Account.username(context) else null
            Avatar(name ?: "You", 52.dp, you = true)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(name ?: "You", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (name != null) Account.privacy(context).label else "Not signed in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { infoLeadsToSetup = false; showInfo = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "What ReelScore reads")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(20.dp))
        if (serviceOn) {
            CounterCard(settings) { AppSettings.setCounterEnabled(context, it) }
        } else {
            SetupCard(onSetUp = { infoLeadsToSetup = true; showInfo = true })
        }

        SectionTitle("Counting hours")
        ToggleRow(
            icon = Icons.Rounded.Schedule,
            title = "Use set hours",
            checked = settings.scheduleOn,
            index = 0,
            count = if (settings.scheduleOn) 3 else 1,
            onChange = { AppSettings.setScheduleOn(context, it) },
        )
        AnimatedVisibility(settings.scheduleOn) {
            Column {
                RowGap()
                NavRow(null, "From", 1, 3, value = settings.start.format(timeFormat)) { editing = TimeField.START }
                RowGap()
                NavRow(null, "To", 2, 3, value = settings.end.format(timeFormat)) { editing = TimeField.END }
            }
        }

        SectionTitle("Daily limit")
        ToggleRow(
            icon = Icons.Rounded.Flag,
            title = "Alert me at a limit",
            checked = limit.on,
            index = 0,
            count = if (limit.on) 2 else 1,
            onChange = { AppSettings.setLimitOn(context, it) },
        )
        AnimatedVisibility(limit.on) {
            Column {
                RowGap()
                NavRow(null, "Limit", 1, 2, value = "${limit.count} reels") { editingLimit = true }
            }
        }

        SectionTitle("Journey")
        val current = journey
        if (current == null) {
            NavRow(Icons.Rounded.Explore, "Start a journey", 0, 1, onClick = onStartJourney)
        } else {
            JourneyCard(current)
            RowGap()
            NavRow(Icons.Rounded.Close, "End journey", 1, 2, destructive = true) { confirmEndJourney = true }
        }
    }

    if (confirmEndJourney) {
        AlertDialog(
            onDismissRequest = { confirmEndJourney = false },
            title = { Text("End this journey?") },
            text = { Text("Progress on it will be lost.") },
            confirmButton = {
                TextButton(onClick = { confirmEndJourney = false; JourneyStore.clear(context) }) { Text("End") }
            },
            dismissButton = { TextButton(onClick = { confirmEndJourney = false }) { Text("Cancel") } },
        )
    }

    if (editingLimit) {
        LimitDialog(
            initial = limit.count,
            onConfirm = { AppSettings.setLimitCount(context, it); editingLimit = false },
            onDismiss = { editingLimit = false },
        )
    }

    if (showInfo) {
        InfoSheet(
            showSetup = infoLeadsToSetup,
            onSetUp = {
                showInfo = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showInfo = false },
        )
    }
    editing?.let { field ->
        TimeDialog(
            title = if (field == TimeField.START) "Start counting at" else "Stop counting at",
            initialMinute = if (field == TimeField.START) settings.startMinute else settings.endMinute,
            onConfirm = {
                if (field == TimeField.START) AppSettings.setScheduleStart(context, it) else AppSettings.setScheduleEnd(context, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private enum class TimeField { START, END }

/** The one big switch. Off means a deep pause: the counter gets no events and runs nothing. */
@Composable
private fun CounterCard(settings: CounterSettings, onToggle: (Boolean) -> Unit) {
    val active = settings.isActive()
    val badge by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "counterBadge",
    )
    val status = when {
        !settings.enabled -> "Paused"
        settings.scheduleOn && active -> "On until ${settings.end.format(timeFormat)}"
        settings.scheduleOn -> "Paused until ${settings.start.format(timeFormat)}"
        else -> "On"
    }
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = settings.enabled, role = Role.Switch, onValueChange = onToggle)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(badge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (active) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Counter", style = MaterialTheme.typography.headlineSmall)
                StatusTag(status, active, Modifier.padding(top = 6.dp))
            }
            Switch(checked = settings.enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun SetupCard(onSetUp: () -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Counter isn't set up",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSetUp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) { Text("Set up") }
        }
    }
}

/** The Play-required disclosure, kept out of the main screen. Also shown before first setup. */
@Composable
private fun InfoSheet(showSetup: Boolean, onSetUp: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("What ReelScore reads", style = MaterialTheme.typography.headlineSmall)
            InfoLine(Icons.Rounded.Slideshow, "When a reels screen is open")
            InfoLine(Icons.Rounded.AlternateEmail, "The channel name on each reel")
            InfoLine(Icons.Rounded.Lock, "Nothing else. It all stays on this phone.")
            if (showSetup) {
                // Android 13+ greys out accessibility for apps installed from a file until the user
                // allows restricted settings in App info.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val context = LocalContext.current
                    Text(
                        "If ReelScore counter is greyed out: open App info, tap ⋮ in the top corner, choose " +
                            "Allow restricted settings, then come back and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                        )
                    }) { Text("Open App info") }
                }
                Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Continue to settings")
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The active journey: route, distance so far at real scale, and a progress bar. */
@Composable
private fun JourneyCard(journey: Journey) {
    val context = LocalContext.current
    val repo = remember { ReelRepository.get(context) }
    val reels by remember(journey.startedAt) { repo.totalSince(journey.startedAt) }.collectAsStateWithLifecycle(0)
    val done = ScrollDistance.meters(reels, context).coerceAtMost(journey.meters)
    val fraction = (done / journey.meters).toFloat().coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(groupShape(0, 2))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("${journey.from.city} → ${journey.to.city}", style = MaterialTheme.typography.titleLarge)
        Text(
            "${DistanceFormat.format(done)} of ${DistanceFormat.format(journey.meters)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressLine(fraction, Modifier.weight(1f))
            Text(formatPercent(fraction.toDouble()), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LimitDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in LimitSettings.MIN..LimitSettings.MAX
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily limit") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                label = { Text("Reels per day") },
                singleLine = true,
                isError = !valid,
                supportingText = { Text("${LimitSettings.MIN} to ${"%,d".format(LimitSettings.MAX)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(value!!) }, enabled = valid) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeDialog(title: String, initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
