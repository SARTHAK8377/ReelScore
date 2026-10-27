package com.reelscore.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.detect.CalibrationDumper
import com.reelscore.app.detect.DetectorRules
import com.reelscore.app.detect.ReelDetector
import com.reelscore.app.detect.ReelTracker
import com.reelscore.app.detect.RuleBasedDetector
import com.reelscore.app.R
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.distance.ScrollDistance
import com.reelscore.app.journey.JourneyStore
import com.reelscore.app.notify.LimitAlert
import java.text.NumberFormat
import com.reelscore.app.settings.AppSettings
import com.reelscore.app.settings.CounterSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Counts reels. Android only sends this service events from the apps listed in
 * res/xml/reel_counter_service.xml, so it is idle whenever those apps aren't open.
 *
 * Deep pause: when the switch is off or the time is outside the counting window, the service asks
 * Android for zero event types and stops its heartbeat, so it gets no events from any app and
 * runs nothing. It wakes only for a settings change, the next schedule boundary, or screen-on
 * (which catches boundaries that passed while the phone was asleep, when Handler delays stall).
 */
class ReelCounterService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val detectors = mutableMapOf<String, ReelDetector>()
    private val tracker = ReelTracker()
    private val handler = Handler(Looper.getMainLooper())
    private val screen = Rect()
    private val sourceBounds = Rect()
    private lateinit var repo: ReelRepository

    private var settings: CounterSettings? = null
    private var running: Boolean? = null
    private var activeEventTypes = 0
    private var heartbeat: Job? = null
    private var popup: View? = null
    private val scheduleTick = Runnable { applyState() }
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyState() }
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyState()
    }

    override fun onServiceConnected() {
        instance = this
        AppSettings.recordServiceStart(this)
        repo = ReelRepository.get(this)
        CalibrationDumper.enabled = AppSettings.calibrationEnabled(this)
        activeEventTypes = serviceInfo.eventTypes
        scope.launch { repo.prunePings() }
        AppSettings.prefs(this).registerOnSharedPreferenceChangeListener(settingsListener)
        val wakeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, wakeReceiver, wakeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        applyState()
    }

    /** Starts or deep-pauses counting to match the switch and schedule, and arms the next boundary. */
    private fun applyState() {
        val current = AppSettings.counter(this).also { settings = it }
        CalibrationDumper.enabled = AppSettings.calibrationEnabled(this)
        val shouldRun = current.isActive()
        handler.removeCallbacks(scheduleTick)
        current.untilNextChange()?.let { handler.postDelayed(scheduleTick, it.toMillis() + SCHEDULE_SLACK_MS) }
        if (shouldRun == running) return
        running = shouldRun

        serviceInfo = serviceInfo.apply { eventTypes = if (shouldRun) activeEventTypes else 0 }
        if (shouldRun) {
            heartbeat = scope.launch {
                while (isActive) {
                    repo.ping()
                    delay(HEARTBEAT_MS)
                }
            }
        } else {
            heartbeat?.cancel()
            heartbeat = null
            // Drop reels still waiting out their minimum watch time, then re-arm the schedule.
            handler.removeCallbacksAndMessages(null)
            current.untilNextChange()?.let { handler.postDelayed(scheduleTick, it.toMillis() + SCHEDULE_SLACK_MS) }
            tracker.onLeftReels()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (running != true) return
        // A window end can pass while the phone sleeps; catch it on the next event.
        if (settings?.isActive() == false) {
            applyState()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        val rules = DetectorRules.forPackage(pkg) ?: return
        if (isNavigation(event)) tracker.onNavigation()

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        root.getBoundsInScreen(screen)

        val sighting = detectors.getOrPut(pkg) { RuleBasedDetector(rules) }.detect(root, pkg, screen)
        CalibrationDumper.maybeDump(this, pkg, root, sighting)

        if (sighting == null) {
            tracker.onLeftReels()
            return
        }
        val id = tracker.onSighting(sighting) ?: return
        // Events run on the main thread, so checking back on it keeps the tracker single-threaded.
        handler.postDelayed({
            // No events arrive from other apps, so check the user didn't switch away meanwhile.
            if (rootInActiveWindow?.packageName?.toString() != pkg) {
                tracker.onLeftReels()
                return@postDelayed
            }
            tracker.confirm(id)?.let { reel ->
                scope.launch {
                    if (!repo.recordReelOnce(reel)) return@launch
                    val today = repo.todayCount()
                    LimitAlert.check(this@ReelCounterService, today)?.let {
                        handler.post {
                            showPopup(
                                big = NumberFormat.getIntegerInstance().format(today),
                                title = "Daily limit reached",
                                body = "You've watched $today reels today.",
                            )
                        }
                    }
                    checkJourney()
                }
            }
        }, ReelTracker.MIN_WATCH_MS)
    }

    /**
     * A screen change, or a scroll of something at least half the screen tall (the reels pager).
     * Small scrollers inside a reel, like YouTube's sound and remix chips, scroll on their own
     * and must not count as moving to a new reel.
     */
    private fun isNavigation(event: AccessibilityEvent): Boolean = when (event.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
            val source = event.source
            source != null && sourceBounds.also(source::getBoundsInScreen).height() >= resources.displayMetrics.heightPixels / 2
        }
        else -> false
    }

    /**
     * Finishes the active journey once the scroll distance since it started reaches its length:
     * saves it for future achievements and shows the arrival card. Real scale, straight line.
     */
    private suspend fun checkJourney() {
        val journey = JourneyStore.active(this) ?: return
        val scrolled = ScrollDistance.meters(repo.countSince(journey.startedAt), this)
        if (scrolled < journey.meters) return
        JourneyStore.clear(this)
        repo.saveCompletedJourney(journey.from.label, journey.to.label, journey.meters, journey.startedAt)
        handler.post {
            showPopup(
                big = "Arrived",
                title = "You've arrived in ${journey.to.city}",
                body = "${journey.from.city} to ${journey.to.city}, ${DistanceFormat.format(journey.meters)} scrolled.",
            )
        }
    }

    /**
     * A card drawn over the reels app as an accessibility overlay, so it needs no extra permission.
     * It doesn't block anything: OK or a tap outside the card closes it. Used for the daily limit
     * and journey arrival.
     */
    private fun showPopup(big: String, title: String, body: String) {
        if (popup != null) return
        val windows = getSystemService(WindowManager::class.java)
        val view = LayoutInflater.from(this).inflate(R.layout.milestone_popup, null)
        view.findViewById<TextView>(R.id.popup_big).text = big
        view.findViewById<TextView>(R.id.popup_title).text = title
        view.findViewById<TextView>(R.id.popup_body).text = body
        val close = View.OnClickListener {
            runCatching { windows.removeView(view) }
            popup = null
        }
        view.findViewById<View>(R.id.popup_ok).setOnClickListener(close)
        view.findViewById<View>(R.id.popup_scrim).setOnClickListener(close)
        view.findViewById<View>(R.id.popup_card).setOnClickListener { } // Taps on the card itself don't close it.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windows.addView(view, params) }.onSuccess { popup = view }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        popup?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        popup = null
        instance = null
        AppSettings.recordServiceStop(this)
        AppSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(settingsListener)
        runCatching { unregisterReceiver(wakeReceiver) }
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val HEARTBEAT_MS = 5 * 60 * 1000L
        private const val SCHEDULE_SLACK_MS = 1_000L

        @Volatile private var instance: ReelCounterService? = null

        /**
         * Switches the service off in Accessibility settings, so Android unloads it entirely.
         * Android doesn't let an app switch itself back on; that needs Accessibility settings.
         */
        fun turnOffCompletely() {
            instance?.disableSelf()
        }

        /**
         * Whether the user has the counter switched on in Accessibility settings. Reads the saved
         * setting rather than the live connection list, which is briefly empty while Android
         * reconnects the service (after an app update, for example).
         */
        fun isEnabled(context: Context): Boolean {
            val ours = ComponentName(context, ReelCounterService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
        }
    }
}
