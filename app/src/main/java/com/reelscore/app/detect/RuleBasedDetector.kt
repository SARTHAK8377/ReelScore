package com.reelscore.app.detect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Uses an app's known view IDs; falls back to [GenericPagerDetector] when they aren't on screen. */
class RuleBasedDetector(
    private val rules: AppRules,
    private val fallback: ReelDetector = GenericPagerDetector(),
) : ReelDetector {

    override fun detect(root: AccessibilityNodeInfo, pkg: String, screen: Rect): ReelSighting? {
        val pager = rules.pagerIds.firstNotNullOfOrNull { largestVisible(root, it) }
            ?: return fallback.detect(root, pkg, screen)
        val page = GenericPagerDetector.dominantChild(pager) ?: pager
        val texts = NodeText.collect(page)
        val creator = rules.creatorIds
            .firstNotNullOfOrNull { id -> largestVisible(page, id)?.text?.toString()?.trim()?.removePrefix("@")?.takeIf(String::isNotEmpty) }
            ?: NodeText.guessCreator(texts)
        val fingerprint = NodeText.fingerprint(creator, texts) ?: return null
        return ReelSighting(pkg, fingerprint, creator)
    }

    /** Preloaded neighbouring reels share view IDs, so pick the match that is actually on screen. */
    private fun largestVisible(node: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val bounds = Rect()
        return node.findAccessibilityNodeInfosByViewId(viewId)
            .filter { it.isVisibleToUser }
            .maxByOrNull { it.getBoundsInScreen(bounds); bounds.width() * bounds.height() }
    }
}
