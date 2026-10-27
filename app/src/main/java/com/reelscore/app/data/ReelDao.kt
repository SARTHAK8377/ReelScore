package com.reelscore.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    @Insert
    suspend fun insertReel(event: ReelEvent)

    @Insert
    suspend fun insertPing(ping: TrackingPing)

    @Insert
    suspend fun insertCompletedJourney(journey: CompletedJourney)

    /** Returns -1 when the reel was already seen. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeen(seen: SeenReel): Long

    @Query("DELETE FROM tracking_ping WHERE timestamp < :before")
    suspend fun deletePingsBefore(before: Long)

    @Query("SELECT COUNT(*) FROM reel_event WHERE timestamp >= :since")
    fun total(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM reel_event WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM reel_event WHERE timestamp >= :start AND timestamp < :end")
    fun totalBetween(start: Long, end: Long): Flow<Int>

    @Query(
        """SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
           FROM reel_event WHERE timestamp >= :since GROUP BY day ORDER BY day""",
    )
    fun dailyCounts(since: Long): Flow<List<DayCount>>

    @Query(
        """SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS count
           FROM reel_event WHERE pkg = :pkg AND timestamp >= :since GROUP BY day ORDER BY day""",
    )
    fun dailyCountsForApp(pkg: String, since: Long): Flow<List<DayCount>>

    @Query(
        """SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, COUNT(*) AS count
           FROM reel_event WHERE timestamp >= :start AND timestamp < :end GROUP BY hour""",
    )
    fun hourlyCounts(start: Long, end: Long): Flow<List<HourCount>>

    @Query(
        """SELECT CAST(strftime('%w', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday,
                  CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, COUNT(*) AS count
           FROM reel_event WHERE timestamp >= :since GROUP BY weekday, hour""",
    )
    fun weekdayHourCounts(since: Long): Flow<List<WeekHourCount>>

    @Query("SELECT COUNT(*) FROM reel_event WHERE pkg = :pkg AND timestamp >= :since")
    fun totalForApp(pkg: String, since: Long): Flow<Int>

    @Query("SELECT pkg, COUNT(*) AS count FROM reel_event WHERE timestamp >= :since GROUP BY pkg")
    fun countsByApp(since: Long): Flow<List<AppCount>>

    @Query("SELECT pkg, COUNT(*) AS count FROM reel_event WHERE timestamp >= :start AND timestamp < :end GROUP BY pkg")
    fun countsByAppBetween(start: Long, end: Long): Flow<List<AppCount>>

    @Query(
        """SELECT creator, COUNT(*) AS count FROM reel_event
           WHERE pkg = :pkg AND creator IS NOT NULL
           GROUP BY creator ORDER BY count DESC LIMIT 3""",
    )
    fun topCreators(pkg: String): Flow<List<CreatorCount>>

    @Query("SELECT MAX(timestamp) FROM tracking_ping")
    fun lastPing(): Flow<Long?>
}
