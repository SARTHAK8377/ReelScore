package com.reelscore.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One reel watched. Never leaves the phone. */
@Entity(tableName = "reel_event", indices = [Index("timestamp"), Index("pkg", "timestamp")])
data class ReelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pkg: String,
    val creator: String?,
    val timestamp: Long,
)

/**
 * Every reel ever counted, so a rewatch never counts again. [key] is a SHA-256 of the app and
 * the reel's fingerprint: the caption text itself is never stored.
 */
@Entity(tableName = "seen_reel")
data class SeenReel(
    @PrimaryKey val key: String,
    val firstSeen: Long,
)

/** A finished journey, kept for future achievements (for example "completed a 100 km journey"). */
@Entity(tableName = "completed_journey")
data class CompletedJourney(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromName: String,
    val toName: String,
    val meters: Double,
    val startedAt: Long,
    val finishedAt: Long,
)

/** Heartbeat written while the counter runs; gaps between pings are periods tracking was off. */
@Entity(tableName = "tracking_ping", indices = [Index("timestamp")])
data class TrackingPing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
)

data class AppCount(val pkg: String, val count: Int)

data class CreatorCount(val creator: String, val count: Int)

/** Reels on one local calendar day; [day] is ISO `yyyy-MM-dd`. */
data class DayCount(val day: String, val count: Int)

/** Reels in one local clock hour (0–23). */
data class HourCount(val hour: Int, val count: Int)

/** Reels in one local clock hour on one weekday (0 = Sunday, as SQLite's `%w`). */
data class WeekHourCount(val weekday: Int, val hour: Int, val count: Int)
