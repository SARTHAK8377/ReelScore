package com.reelscore.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import com.reelscore.app.BuildConfig
import kotlin.math.abs
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The master switch plus one optional daily window. The counter runs only when [enabled] is on and,
 * if [scheduleOn], the time is inside [startMinute]..[endMinute] (minutes after midnight).
 * A window can cross midnight, like 22:00 to 02:00. Equal start and end means all day.
 */
data class CounterSettings(
    val enabled: Boolean,
    val scheduleOn: Boolean,
    val startMinute: Int,
    val endMinute: Int,
) {
    fun inWindow(time: LocalTime): Boolean {
        val m = time.hour * 60 + time.minute
        return when {
            startMinute == endMinute -> true
            startMinute < endMinute -> m in startMinute until endMinute
            else -> m >= startMinute || m < endMinute
        }
    }

    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        enabled && (!scheduleOn || inWindow(now.toLocalTime()))

    /** Time until the schedule next starts or stops the counter, or null when nothing is scheduled. */
    fun untilNextChange(now: LocalDateTime = LocalDateTime.now()): Duration? {
        if (!enabled || !scheduleOn || startMinute == endMinute) return null
        val next = listOf(startMinute, endMinute)
            .flatMap { m -> (0L..1L).map { now.toLocalDate().plusDays(it).atTime(m / 60, m % 60) } }
            .filter { it.isAfter(now) }
            .min()
        return Duration.between(now, next)
    }

    val start: LocalTime get() = LocalTime.of(startMinute / 60, startMinute % 60)
    val end: LocalTime get() = LocalTime.of(endMinute / 60, endMinute % 60)
}

/** An optional daily reel limit. Reaching it sends one notification that day; nothing is blocked. */
data class LimitSettings(val on: Boolean, val count: Int) {
    companion object {
        const val DEFAULT = 100
        const val MIN = 1
        const val MAX = 2_000
    }
}

object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_CALIBRATION = "calibration_enabled"
    private const val KEY_ENABLED = "counter_enabled"
    private const val KEY_SCHEDULE = "schedule_on"
    private const val KEY_START = "schedule_start"
    private const val KEY_END = "schedule_end"
    private const val KEY_SVC_ALIVE = "service_alive"
    private const val KEY_SVC_BOOT = "service_boot"
    private const val KEY_SVC_INSTALL = "service_install"
    private const val KEY_BATTERY_PROMPT = "battery_prompt_pending"
    private const val SAME_BOOT_TOLERANCE_MS = 60_000L
    private const val KEY_LIMIT_ON = "limit_on"
    private const val KEY_LIMIT = "limit_count"
    private const val KEY_LIMIT_NOTIFIED = "limit_notified"
    private const val KEY_GAP_DAY = "gap_day"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Debug builds only, so a switch left on while testing never dumps screens in a release build. */
    fun calibrationEnabled(context: Context): Boolean = BuildConfig.DEBUG && prefs(context).getBoolean(KEY_CALIBRATION, false)

    fun setCalibrationEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_CALIBRATION, enabled) }

    fun counter(context: Context): CounterSettings = prefs(context).run {
        CounterSettings(
            enabled = getBoolean(KEY_ENABLED, true),
            scheduleOn = getBoolean(KEY_SCHEDULE, false),
            startMinute = getInt(KEY_START, 9 * 60),
            endMinute = getInt(KEY_END, 23 * 60),
        )
    }

    fun setCounterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (!enabled) markGap(context)
    }

    /** Remembers that today's count may be missing reels (the counter was off for a while). */
    fun markGap(context: Context) = prefs(context).edit { putString(KEY_GAP_DAY, LocalDate.now().toString()) }

    /**
     * Whether today's count may be missing reels: the counter was paused, stopped or killed today,
     * or set hours leave part of the day uncounted. Shown as a neutral note on shared scores.
     */
    fun hadGapToday(context: Context): Boolean {
        val counter = counter(context)
        return !counter.enabled || counter.scheduleOn || prefs(context).getString(KEY_GAP_DAY, null) == LocalDate.now().toString()
    }

    fun setScheduleOn(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_SCHEDULE, on) }

    fun setScheduleStart(context: Context, minute: Int) = prefs(context).edit { putInt(KEY_START, minute) }

    fun setScheduleEnd(context: Context, minute: Int) = prefs(context).edit { putInt(KEY_END, minute) }

    /**
     * Called when the counter service starts. Returns true when the previous run was killed:
     * it never stopped cleanly, and there was no reboot or app update in between. That is the
     * sign the phone's battery manager is shutting ReelScore down.
     */
    fun recordServiceStart(context: Context): Boolean {
        val prefs = prefs(context)
        val boot = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        val killed = prefs.getBoolean(KEY_SVC_ALIVE, false) &&
            abs(prefs.getLong(KEY_SVC_BOOT, 0L) - boot) < SAME_BOOT_TOLERANCE_MS &&
            prefs.getLong(KEY_SVC_INSTALL, 0L) == installed
        prefs.edit {
            putBoolean(KEY_SVC_ALIVE, true)
            putLong(KEY_SVC_BOOT, boot)
            putLong(KEY_SVC_INSTALL, installed)
            if (killed) {
                putBoolean(KEY_BATTERY_PROMPT, true)
                putString(KEY_GAP_DAY, LocalDate.now().toString())
            }
        }
        return killed
    }

    fun recordServiceStop(context: Context) = prefs(context).edit {
        putBoolean(KEY_SVC_ALIVE, false)
        putString(KEY_GAP_DAY, LocalDate.now().toString())
    }

    fun batteryPromptPending(context: Context): Boolean = prefs(context).getBoolean(KEY_BATTERY_PROMPT, false)

    fun clearBatteryPrompt(context: Context) = prefs(context).edit { putBoolean(KEY_BATTERY_PROMPT, false) }

    fun limit(context: Context): LimitSettings = prefs(context).run {
        LimitSettings(on = getBoolean(KEY_LIMIT_ON, false), count = getInt(KEY_LIMIT, LimitSettings.DEFAULT))
    }

    fun setLimitOn(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_LIMIT_ON, on) }

    fun setLimitCount(context: Context, count: Int) =
        prefs(context).edit { putInt(KEY_LIMIT, count.coerceIn(LimitSettings.MIN, LimitSettings.MAX)) }

    /**
     * Marks the limit notification as sent for [day] at [count]. Returns false if it was already
     * sent for that same day and limit, so a raised limit can notify again the same day.
     */
    fun markLimitNotified(context: Context, day: String, count: Int): Boolean {
        val key = "$day:$count"
        if (prefs(context).getString(KEY_LIMIT_NOTIFIED, null) == key) return false
        prefs(context).edit { putString(KEY_LIMIT_NOTIFIED, key) }
        return true
    }

    /** Current counter settings, then a new value after every change. */
    fun observeCounter(context: Context): Flow<CounterSettings> = observe(context) { counter(it) }

    fun observeLimit(context: Context): Flow<LimitSettings> = observe(context) { limit(it) }

    /** [read] now, then again after every settings change. */
    fun <T> observe(context: Context, read: (Context) -> T): Flow<T> = callbackFlow {
        val prefs = prefs(context)
        // SharedPreferences holds listeners weakly; this flow keeps the reference alive until closed.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read(context)) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(read(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
