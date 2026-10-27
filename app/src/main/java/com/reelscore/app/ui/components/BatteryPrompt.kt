package com.reelscore.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.reelscore.app.settings.AppSettings

/**
 * Asks to keep ReelScore running only after the phone has actually killed the counter
 * (see [AppSettings.recordServiceStart]) and only if ReelScore isn't already exempt.
 * "Allow" opens Android's own small confirmation popup on top of the app, not a settings page.
 */
@Composable
fun BatteryPrompt() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        show = AppSettings.batteryPromptPending(context) && !isExempt(context)
        if (AppSettings.batteryPromptPending(context) && !show) AppSettings.clearBatteryPrompt(context)
        onPauseOrDispose { }
    }
    if (!show) return

    val dismiss = {
        show = false
        AppSettings.clearBatteryPrompt(context)
    }
    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null) },
        title = { Text("Keep counting in the background?") },
        text = { Text("Your phone stopped ReelScore to save battery, so some reels weren't counted.") },
        confirmButton = {
            TextButton(onClick = {
                dismiss()
                requestExemption(context)
            }) { Text("Allow") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Not now") } },
    )
}

private fun isExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // Requested only after the counter was actually killed.
private fun requestExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
}
