package com.reelscore.app.detect

/**
 * Per-app detection rules, kept as plain data so they can later be loaded from Remote Config.
 * The view IDs below are unverified guesses until calibration dumps from a real phone confirm them;
 * when none are found on screen, the generic detector takes over.
 * Keep the package list in sync with res/xml/reel_counter_service.xml.
 */
data class AppRules(
    val pkg: String,
    val label: String,
    val pagerIds: List<String> = emptyList(),
    val creatorIds: List<String> = emptyList(),
)

object DetectorRules {
    val apps = listOf(
        AppRules(
            pkg = "com.instagram.android",
            label = "Instagram",
            pagerIds = listOf("com.instagram.android:id/clips_viewer_view_pager"),
            creatorIds = listOf("com.instagram.android:id/clips_author_username"),
        ),
        // Calibrated 2026-10-21: reel_recycler holds one page per Short. The channel handle has no
        // view ID, so the creator comes from the "@handle" text the generic guess already prefers.
        AppRules(
            pkg = "com.google.android.youtube",
            label = "YouTube Shorts",
            pagerIds = listOf("com.google.android.youtube:id/reel_recycler"),
        ),
        AppRules(pkg = "com.facebook.katana", label = "Facebook"),
        AppRules(pkg = "com.snapchat.android", label = "Snapchat"),
        AppRules(pkg = "in.mohalla.video", label = "Moj"),
        AppRules(pkg = "com.eterno.shortvideos", label = "Josh"),
    )

    private val byPkg = apps.associateBy { it.pkg }

    fun forPackage(pkg: String): AppRules? = byPkg[pkg]

    fun labelFor(pkg: String): String = byPkg[pkg]?.label ?: pkg
}
