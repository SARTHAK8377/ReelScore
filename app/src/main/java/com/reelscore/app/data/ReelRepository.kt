package com.reelscore.app.data

import android.content.Context
import com.reelscore.app.detect.ReelSighting
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReelRepository private constructor(private val dao: ReelDao) {

    /** Counts [reel] unless this exact reel was counted before, ever. Returns whether it counted. */
    suspend fun recordReelOnce(reel: ReelSighting): Boolean {
        val now = System.currentTimeMillis()
        if (dao.insertSeen(SeenReel(key = seenKey(reel), firstSeen = now)) == -1L) return false
        dao.insertReel(ReelEvent(pkg = reel.pkg, creator = reel.creator, timestamp = now))
        return true
    }

    suspend fun todayCount(): Int = dao.countSince(Period.TODAY.start())

    suspend fun countSince(timestamp: Long): Int = dao.countSince(timestamp)

    fun totalSince(timestamp: Long): Flow<Int> = dao.total(timestamp)

    suspend fun saveCompletedJourney(fromName: String, toName: String, meters: Double, startedAt: Long) =
        dao.insertCompletedJourney(
            CompletedJourney(fromName = fromName, toName = toName, meters = meters, startedAt = startedAt, finishedAt = System.currentTimeMillis()),
        )

    private fun seenKey(reel: ReelSighting): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${reel.pkg}\n${reel.fingerprint}".toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun ping() = dao.insertPing(TrackingPing(timestamp = System.currentTimeMillis()))

    suspend fun prunePings() =
        dao.deletePingsBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PING_RETENTION_DAYS))

    fun total(period: Period): Flow<Int> = dao.total(period.start())

    fun totalForApp(pkg: String, period: Period): Flow<Int> = dao.totalForApp(pkg, period.start())

    fun countsByApp(period: Period): Flow<List<AppCount>> = dao.countsByApp(period.start())

    fun topCreators(pkg: String): Flow<List<CreatorCount>> = dao.topCreators(pkg)

    fun lastPing(): Flow<Long?> = dao.lastPing()

    fun yesterdayTotal(): Flow<Int> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return dao.totalBetween(today.minusDays(1).startMillis(zone), today.startMillis(zone))
    }

    /**
     * One entry per day for the last [days] days, oldest first, with empty days as 0. Reels before
     * [notBefore] are left out (used for a journey's share of each day).
     */
    fun dailyCounts(days: Int, notBefore: Long = 0L): Flow<List<DayCount>> {
        val zone = ZoneId.systemDefault()
        val first = LocalDate.now(zone).minusDays(days - 1L)
        return dao.dailyCounts(maxOf(first.startMillis(zone), notBefore)).map { rows ->
            val byDay = rows.associate { it.day to it.count }
            (0 until days).map { offset ->
                val day = first.plusDays(offset.toLong()).toString()
                DayCount(day, byDay[day] ?: 0)
            }
        }
    }

    /** One app's reels per day for the last [days] days, oldest first, empty days as 0. */
    fun dailyCountsForApp(pkg: String, days: Int): Flow<List<DayCount>> {
        val zone = ZoneId.systemDefault()
        val first = LocalDate.now(zone).minusDays(days - 1L)
        return dao.dailyCountsForApp(pkg, first.startMillis(zone)).map { rows ->
            val byDay = rows.associate { it.day to it.count }
            (0 until days).map { offset -> first.plusDays(offset.toLong()).toString().let { DayCount(it, byDay[it] ?: 0) } }
        }
    }

    /** Reels per app on one local [day]. */
    fun countsByAppOn(day: LocalDate): Flow<List<AppCount>> {
        val zone = ZoneId.systemDefault()
        return dao.countsByAppBetween(day.startMillis(zone), day.plusDays(1).startMillis(zone))
    }

    /** Today's reels per clock hour, index 0–23. */
    fun todayByHour(): Flow<List<Int>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return dao.hourlyCounts(today.startMillis(zone), today.plusDays(1).startMillis(zone)).map { rows ->
            val byHour = rows.associate { it.hour to it.count }
            List(24) { byHour[it] ?: 0 }
        }
    }

    /**
     * Reels per weekday and hour over the last [weeks] weeks: 7 rows, Monday first, of 24 hours each.
     */
    fun weekHourGrid(weeks: Int = 4): Flow<List<List<Int>>> {
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now(zone).minusWeeks(weeks.toLong()).plusDays(1).startMillis(zone)
        return dao.weekdayHourCounts(since).map { rows ->
            val cells = rows.associate { (it.weekday to it.hour) to it.count }
            // SQLite numbers Sunday 0; rows here run Monday (1) to Sunday (0).
            listOf(1, 2, 3, 4, 5, 6, 0).map { day -> List(24) { hour -> cells[day to hour] ?: 0 } }
        }
    }

    private fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    enum class Period {
        TODAY, WEEK, ALL_TIME;

        fun start(): Long {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            return when (this) {
                TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
                WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
                ALL_TIME -> 0L
            }
        }
    }

    companion object {
        private const val PING_RETENTION_DAYS = 35L

        @Volatile private var instance: ReelRepository? = null

        fun get(context: Context): ReelRepository =
            instance ?: synchronized(this) {
                instance ?: ReelRepository(AppDatabase.build(context).reelDao()).also { instance = it }
            }
    }
}
