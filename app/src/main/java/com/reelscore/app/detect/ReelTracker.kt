package com.reelscore.app.detect

/**
 * Decides whether a sighting is a new reel worth counting.
 * - The same reel staying on screen (playback, likes updating) never counts twice.
 * - A new reel only starts after the user scrolls or opens a screen, so a caption expanding
 *   or text loading late is treated as the same reel, not a new one.
 * - A new reel counts only after it stays on screen for [MIN_WATCH_MS]; the caller waits that
 *   long and then calls [confirm]. Reels skipped faster than that don't count.
 * - Scrolling back to a recently counted reel doesn't count again.
 */
class ReelTracker(private val clock: () -> Long = System::currentTimeMillis) {

    private var lastPkg: String? = null
    private var current: String? = null
    private var lastNavigationAt = 0L
    private val recent = ArrayDeque<String>()
    private var pending: ReelSighting? = null
    private var pendingId = 0L

    fun onNavigation() {
        lastNavigationAt = clock()
    }

    fun onLeftReels() {
        current = null
        pending = null
    }

    /** Returns an id to pass to [confirm] after [MIN_WATCH_MS], or null when no new reel started. */
    fun onSighting(sighting: ReelSighting): Long? {
        if (sighting.pkg != lastPkg) {
            lastPkg = sighting.pkg
            current = null
            pending = null
            recent.clear()
        }
        if (sighting.fingerprint == current) return null
        val navigated = current == null || clock() - lastNavigationAt <= NAVIGATION_WINDOW_MS
        current = sighting.fingerprint
        if (!navigated) {
            // Same reel, its text changed: keep timing it with the newer details.
            if (pending != null) pending = sighting
            return null
        }
        if (sighting.fingerprint in recent) {
            pending = null
            return null
        }
        pending = sighting
        return ++pendingId
    }

    /** Returns the reel to count if the reel started by [id] is still on screen, else null. */
    fun confirm(id: Long): ReelSighting? {
        val sighting = pending?.takeIf { id == pendingId } ?: return null
        pending = null
        recent.addLast(sighting.fingerprint)
        if (recent.size > RECENT_LIMIT) recent.removeFirst()
        return sighting
    }

    companion object {
        const val MIN_WATCH_MS = 5_000L
        private const val NAVIGATION_WINDOW_MS = 2_000L
        private const val RECENT_LIMIT = 30
    }
}
