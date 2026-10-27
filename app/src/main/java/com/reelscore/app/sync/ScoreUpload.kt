package com.reelscore.app.sync

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.reelscore.app.account.Account
import com.reelscore.app.account.Privacy
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.settings.AppSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Uploads three totals (today, this week, all time) for a signed-in account: every 6 hours and
 * when the app opens, only on a network connection, and only when something changed. Public
 * accounts also get a copy in `public/{uid}` for the Everyone leaderboard.
 */
object ScoreUpload {
    private const val PERIODIC = "score_upload"
    private const val ONCE = "score_upload_now"
    private const val PREFS = "score_upload"
    private const val KEY_LAST = "last"
    private const val KEY_LAST_AT = "last_at"

    /** The rules' 10-second spacing plus a margin for clock differences. */
    private const val SPACING_MS = 11_500L

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(6, TimeUnit.HOURS).setConstraints(online).build(),
        )
        now(context)
    }

    fun now(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<Worker>().setConstraints(online).build(),
        )
    }

    /** Clears the "already uploaded" memory, so the next account starts fresh. */
    fun forget(context: Context) = prefs(context).edit { clear() }

    fun emptyScores(): Map<String, Any> = scores(0, 0, 0, false)

    private fun scores(today: Long, week: Long, allTime: Long, gap: Boolean): Map<String, Any> = mapOf(
        "today" to today,
        "todayDate" to LocalDate.now().toString(),
        "week" to week,
        "weekStart" to LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
        "allTime" to allTime,
        "gapToday" to gap,
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Only one write at a time, so queued writes can be spaced and merged. */
    private val writing = Mutex()

    /**
     * Writes the current totals and the privacy wanted on this phone ([Account.privacy]) in one
     * batch, and keeps the public copy in step: set while Public, deleted otherwise.
     *
     * The rules allow one write per account every [SPACING_MS], so a write that comes too soon
     * after the last one waits here instead of being refused. Writes queue behind each other, and
     * each reads the latest wanted privacy when its turn comes, so rapid changes merge into one.
     * Skips the write when nothing changed. Throws if the write fails.
     */
    suspend fun write(context: Context) = writing.withLock {
        val uid = Account.uid ?: return@withLock
        val name = Account.username(context) ?: return@withLock
        val wait = prefs(context).getLong(KEY_LAST_AT, 0L) + SPACING_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait.coerceAtMost(SPACING_MS))
        val privacy = Account.privacy(context)
        val repo = ReelRepository.get(context)
        val data = scores(
            today = repo.countSince(ReelRepository.Period.TODAY.start()).toLong(),
            week = repo.countSince(ReelRepository.Period.WEEK.start()).toLong(),
            allTime = repo.countSince(ReelRepository.Period.ALL_TIME.start()).toLong(),
            gap = AppSettings.hadGapToday(context),
        )
        val fingerprint = "$data|${privacy.key}"
        if (prefs(context).getString(KEY_LAST, null) == fingerprint) return@withLock
        val stamped = data + ("updatedAt" to FieldValue.serverTimestamp())
        val db = Account.db
        db.runBatch { batch ->
            batch.update(db.collection("users").document(uid), stamped + ("privacy" to privacy.key))
            val public = db.collection("public").document(uid)
            if (privacy == Privacy.PUBLIC) batch.set(public, stamped + ("username" to name)) else batch.delete(public)
        }.await()
        prefs(context).edit {
            putString(KEY_LAST, fingerprint)
            putLong(KEY_LAST_AT, System.currentTimeMillis())
        }
    }

    /**
     * Saves a privacy change at once, without waiting for the score spacing: the rules let a
     * change of privacy alone through. Going Public creates the public copy from this phone's
     * totals; leaving Public deletes it. Throws if the write fails.
     */
    suspend fun writePrivacy(context: Context, privacy: Privacy) = writing.withLock {
        val uid = Account.uid ?: return@withLock
        val name = Account.username(context) ?: return@withLock
        val db = Account.db
        val public = db.collection("public").document(uid)
        val copy = if (privacy == Privacy.PUBLIC) {
            val repo = ReelRepository.get(context)
            scores(
                today = repo.countSince(ReelRepository.Period.TODAY.start()).toLong(),
                week = repo.countSince(ReelRepository.Period.WEEK.start()).toLong(),
                allTime = repo.countSince(ReelRepository.Period.ALL_TIME.start()).toLong(),
                gap = AppSettings.hadGapToday(context),
            ) + mapOf("username" to name, "updatedAt" to FieldValue.serverTimestamp())
        } else {
            null
        }
        db.runBatch { batch ->
            batch.update(db.collection("users").document(uid), "privacy", privacy.key)
            if (copy != null) batch.set(public, copy) else batch.delete(public)
        }.await()
        // The public copy was just stamped, so the next score write waits its turn after it.
        if (copy != null) prefs(context).edit { putLong(KEY_LAST_AT, System.currentTimeMillis()) }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            runCatching { write(applicationContext) }.fold({ Result.success() }, { Result.retry() })
    }
}
