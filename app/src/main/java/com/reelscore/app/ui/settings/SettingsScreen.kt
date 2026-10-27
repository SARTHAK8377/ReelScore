package com.reelscore.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.BuildConfig
import com.reelscore.app.account.Account
import com.reelscore.app.ui.friends.PrivacyChoice
import com.reelscore.app.detect.CalibrationDumper
import com.reelscore.app.service.ReelCounterService
import com.reelscore.app.settings.AppSettings
import com.reelscore.app.ui.components.NavRow
import com.reelscore.app.ui.components.RowGap
import com.reelscore.app.ui.components.SectionTitle
import com.reelscore.app.ui.components.ToggleRow
import com.reelscore.app.ui.components.rememberCounterEnabled
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenHelp: () -> Unit, onOpenGallery: () -> Unit) {
    val context = LocalContext.current
    val serviceOn = rememberCounterEnabled()
    var confirmTurnOff by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            PageHeader("Settings", onBack)

            SectionTitle("Counter")
            NavRow(
                Icons.Rounded.AccessibilityNew, "Accessibility", 0, if (serviceOn) 2 else 1,
                subtitle = if (serviceOn) "On" else "Off",
            ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            if (serviceOn) {
                RowGap()
                NavRow(Icons.Rounded.PowerSettingsNew, "Turn off completely", 1, 2, destructive = true) { confirmTurnOff = true }
            }

            AccountSection(snackbar)

            SectionTitle("Support")
            NavRow(Icons.AutoMirrored.Rounded.HelpOutline, "Help", 0, 1, onClick = onOpenHelp)

            if (BuildConfig.DEBUG) {
                Calibration(snackbar)
                RowGap()
                NavRow(null, "Silhouette gallery", 0, 1, onClick = onOpenGallery)
            }

            Text(
                "ReelScore ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp, start = 4.dp),
            )
            // Required credit for the bundled place list (CC BY 4.0).
            Text(
                "Place data from GeoNames (geonames.org), CC BY 4.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }

    if (confirmTurnOff) {
        AlertDialog(
            onDismissRequest = { confirmTurnOff = false },
            icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
            title = { Text("Turn off completely?") },
            text = { Text("To start again, open Accessibility here and turn on ReelScore counter.") },
            confirmButton = {
                TextButton(onClick = { confirmTurnOff = false; ReelCounterService.turnOffCompletely() }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmTurnOff = false }) { Text("Cancel") } },
        )
    }
}

/** Username, privacy, sign out and delete. Hidden until an account has a username. */
@Composable
private fun AccountSection(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by remember { Account.user() }.collectAsStateWithLifecycle(Account.auth.currentUser)
    val uid = user?.uid ?: return
    val profile by remember(uid) { Account.profile(uid) }.collectAsStateWithLifecycle(null)
    val me = profile ?: return
    var choosingPrivacy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // The choice being saved, from anywhere in the app; shown in place of the online value.
    val saving by Account.pendingPrivacy.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        Account.privacyFailures.collect { snackbar.showSnackbar("Couldn't save. Check your connection.") }
    }

    SectionTitle("Account")
    NavRow(Icons.Rounded.AccountCircle, "Username", 0, 4, value = me.username) {}
    RowGap()
    NavRow(
        Icons.Rounded.Visibility,
        "Who can see your scores",
        1,
        4,
        subtitle = if (saving != null) "Saving…" else null,
        value = (saving ?: me.privacy).label,
    ) { choosingPrivacy = true }
    RowGap()
    NavRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", 2, 4) { scope.launch { Account.signOut(context) } }
    RowGap()
    NavRow(Icons.Rounded.DeleteForever, "Delete account", 3, 4, destructive = true) { confirmDelete = true }

    if (choosingPrivacy) {
        AlertDialog(
            onDismissRequest = { choosingPrivacy = false },
            title = { Text("Who can see your scores") },
            text = {
                Column {
                    PrivacyChoice(saving ?: me.privacy) { choice ->
                        choosingPrivacy = false
                        if (choice != (saving ?: me.privacy)) Account.changePrivacy(context, choice)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingPrivacy = false }) { Text("Close") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text("Delete your account?") },
            text = { Text("Your username, friends and shared scores are removed from ReelScore's servers. Counts on this phone stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { Account.deleteAccount(context) }
                            .onSuccess { snackbar.showSnackbar("Account deleted") }
                            .onFailure { snackbar.showSnackbar("Couldn't delete. Check your connection and try again.") }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Back arrow and page title, shared by Settings and Help. */
@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    Column {
        // Shifted left so the arrow glyph, not its 48 dp touch target, lines up with the content edge.
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Row(Modifier.padding(top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun Calibration(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(AppSettings.calibrationEnabled(context)) }

    SectionTitle("Calibration")
    ToggleRow(Icons.Rounded.BugReport, "Record screen layouts", on, 0, 2) {
        on = it
        AppSettings.setCalibrationEnabled(context, it)
        CalibrationDumper.enabled = it
    }
    RowGap()
    NavRow(Icons.Rounded.DeleteOutline, "Delete calibration files", 1, 2) {
        CalibrationDumper.clear(context)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("Calibration files deleted")
        }
    }
}
