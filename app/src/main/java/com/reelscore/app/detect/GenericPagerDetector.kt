package com.reelscore.app.detect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Fallback for apps without specific rules. A reels screen is a scrollable container that fills
 * the screen and whose visible child fills the container: one video per page. A normal feed also
 * fills the screen, but its posts are shorter than the screen, so it doesn't match.
 */
class GenericPagerDetector : ReelDetector {

    override fun detect(root: AccessibilityNodeInfo, pkg: String, screen: Rect): ReelSighting? {
        val pager = findFullScreenPager(root, screen) ?: return null
        val page = dominantChild(pager) ?: return null
        val texts = NodeText.collect(page)
        val creator = NodeText.guessCreator(texts)
        val fingerprint = NodeText.fingerprint(creator, texts) ?: return null
        return ReelSighting(pkg, fingerprint, creator)
    }

    companion object {
        private const val MAX_NODES = 400

        fun findFullScreenPager(root: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { addLast(root) }
            val bounds = Rect()
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES) {
                val n = queue.removeFirst()
                visited++
                if (!n.isVisibleToUser) continue
                n.getBoundsInScreen(bounds)
                val fillsScreen = bounds.width() >= screen.width() * 0.9 && bounds.height() >= screen.height() * 0.7
                if (n.isScrollable && fillsScreen && dominantChild(n) != null) return n
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::addLast)
            }
            return null
        }

        /** The visible child that covers at least 85% of [pager]'s height, if any. */
        fun dominantChild(pager: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val pagerBounds = Rect().also(pager::getBoundsInScreen)
            val childBounds = Rect()
            for (i in 0 until pager.childCount) {
                val child = pager.getChild(i) ?: continue
                if (!child.isVisibleToUser) continue
                child.getBoundsInScreen(childBounds)
                if (childBounds.height() >= pagerBounds.height() * 0.85) return child
            }
            return null
        }
    }
}
