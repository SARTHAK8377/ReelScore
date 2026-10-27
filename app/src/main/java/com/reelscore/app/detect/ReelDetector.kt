package com.reelscore.app.detect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** One reel seen on screen. [fingerprint] identifies the reel; [creator] is the channel name if found. */
data class ReelSighting(
    val pkg: String,
    val fingerprint: String,
    val creator: String?,
)

/** Looks at an app's current screen and returns the reel being shown, or null if this is not a reels screen. */
interface ReelDetector {
    fun detect(root: AccessibilityNodeInfo, pkg: String, screen: Rect): ReelSighting?
}

/** Helpers shared by the detectors for reading on-screen text. */
internal object NodeText {
    private const val MAX_NODES = 200
    private const val FINGERPRINT_CAPTION_CHARS = 40

    private val countLike = Regex("""^[\d.,\s]+[KkMmBbLl]?$|^\d+:\d+$""")
    private val handleLike = Regex("""^@?[A-Za-z0-9._]{3,30}$""")
    private val uiWords = setOf(
        "follow", "following", "like", "likes", "comment", "comments", "share", "send", "remix",
        "more", "reels", "reel", "shorts", "subscribe", "subscribed", "audio", "original audio",
        "search", "home", "camera", "spotlight", "save", "more options", "back", "sound",
    )

    /** Visible text and content descriptions under [node], in screen order, without counters and timers. */
    fun collect(node: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { addLast(node) }
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val n = stack.removeLast()
            visited++
            if (!n.isVisibleToUser) continue
            listOfNotNull(n.text, n.contentDescription).forEach { cs ->
                val s = cs.toString().trim()
                if (s.isNotEmpty() && s.length <= 300 && !countLike.matches(s)) out += s
            }
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let(stack::addLast)
        }
        return out
    }

    /** Best guess at the channel name: an @handle if present, else the first handle-shaped text. */
    fun guessCreator(texts: List<String>): String? {
        val candidates = texts.filter { handleLike.matches(it) && it.any(Char::isLetter) && it.lowercase() !in uiWords }
        return (candidates.firstOrNull { it.startsWith("@") } ?: candidates.firstOrNull())?.removePrefix("@")
    }

    /** Creator plus the start of the longest caption-like text. Null when there is nothing to tell reels apart. */
    fun fingerprint(creator: String?, texts: List<String>): String? {
        val caption = texts
            .filter { it.removePrefix("@") != creator && it.lowercase() !in uiWords }
            .maxByOrNull { it.length }
            ?.take(FINGERPRINT_CAPTION_CHARS)
        if (creator == null && caption == null) return null
        return "${creator.orEmpty()}|${caption.orEmpty()}"
    }
}
