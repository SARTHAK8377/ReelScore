package com.reelscore.app.detect

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug tool: writes the structure of the current screen (class names, view IDs, bounds, flags)
 * to a file so detector rules can be written for each app. It never writes on-screen text,
 * only whether a node has text.
 * Files go to Android/data/com.reelscore.app/files/calibration/ (pull with `adb pull`).
 *
 * Walking the tree is one IPC call per node, so it runs on its own thread: on the main thread
 * it stalled event handling for seconds at a time and broke counting while calibration was on.
 */
object CalibrationDumper {
    private const val MIN_INTERVAL_MS = 4_000L
    private const val MAX_DUMPS = 60
    private const val MAX_NODES = 800

    @Volatile var enabled = false
    private var lastDumpAt = 0L
    private var dumps = 0
    private val busy = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "calibration").apply { isDaemon = true } }

    fun maybeDump(context: Context, pkg: String, root: AccessibilityNodeInfo, sighting: ReelSighting?) {
        val now = System.currentTimeMillis()
        if (!enabled || dumps >= MAX_DUMPS || now - lastDumpAt < MIN_INTERVAL_MS) return
        if (!busy.compareAndSet(false, true)) return
        lastDumpAt = now
        dumps++

        val dir = File(context.getExternalFilesDir(null), "calibration")
        val detected = if (sighting == null) "none" else "reel creatorFound=${sighting.creator != null}"
        worker.execute {
            try {
                val text = buildString {
                    appendLine("# pkg=$pkg time=$now detected=$detected")
                    appendTree(root, 0, intArrayOf(0))
                }
                dir.mkdirs()
                File(dir, "${pkg}_$now.txt").writeText(text)
            } catch (_: Exception) {
                // The screen changed mid-walk; skip this dump.
            } finally {
                busy.set(false)
            }
        }
    }

    fun clear(context: Context) {
        File(context.getExternalFilesDir(null), "calibration").deleteRecursively()
        dumps = 0
    }

    private fun StringBuilder.appendTree(node: AccessibilityNodeInfo, depth: Int, count: IntArray) {
        if (count[0]++ >= MAX_NODES) return
        val b = Rect().also(node::getBoundsInScreen)
        append("  ".repeat(depth))
        append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.let { append(" id=").append(it.substringAfter(":id/")) }
        append(" [${b.left},${b.top},${b.right},${b.bottom}]")
        if (node.isScrollable) append(" scrollable")
        if (!node.isVisibleToUser) append(" hidden")
        if (!node.text.isNullOrEmpty()) append(" hasText(${node.text.length})")
        if (!node.contentDescription.isNullOrEmpty()) append(" hasDesc")
        appendLine()
        for (i in 0 until node.childCount) node.getChild(i)?.let { appendTree(it, depth + 1, count) }
    }
}
