package com.reelscore.app.account

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class Friend(val uid: String, val name: String)

data class FriendRequest(val from: String, val to: String, val fromName: String, val toName: String) {
    val id get() = "${from}_$to"
}

enum class Period(val label: String) { TODAY("Today"), WEEK("This week"), ALL_TIME("All time") }

/** One leaderboard line. [score] is null when that person keeps their scores private. */
data class Standing(val uid: String, val name: String, val score: Long?, val gapToday: Boolean, val isYou: Boolean)

sealed interface AddResult {
    data object Sent : AddResult
    data object Accepted : AddResult
    data object NotFound : AddResult
    data object Yourself : AddResult
    data object AlreadyFriends : AddResult
}

/**
 * Friend requests and leaderboards. A friendship is a pair of docs, `users/{a}/friends/{b}` and
 * `users/{b}/friends/{a}`, written when the request is accepted; the rules only let the person
 * who was asked write the other side.
 */
object Friends {
    private val db get() = Account.db
    private const val EVERYONE_LIMIT = 30L

    fun friends(uid: String): Flow<List<Friend>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("friends").addSnapshotListener { snap, _ ->
            trySend(snap?.documents.orEmpty().map { Friend(it.id, it.getString("name").orEmpty()) }.sortedBy { it.name })
        }
        awaitClose { reg.remove() }
    }

    fun incoming(uid: String): Flow<List<FriendRequest>> = requests("to", uid)

    fun outgoing(uid: String): Flow<List<FriendRequest>> = requests("from", uid)

    private fun requests(field: String, uid: String): Flow<List<FriendRequest>> = callbackFlow {
        val reg = db.collection("requests").whereEqualTo(field, uid).addSnapshotListener { snap, _ ->
            trySend(
                snap?.documents.orEmpty().map {
                    FriendRequest(it.getString("from").orEmpty(), it.getString("to").orEmpty(), it.getString("fromName").orEmpty(), it.getString("toName").orEmpty())
                },
            )
        }
        awaitClose { reg.remove() }
    }

    /** Sends a request to [username], or accepts theirs if they already asked you. */
    suspend fun add(me: String, myName: String, username: String): AddResult {
        val name = username.trim().lowercase()
        // Also keeps "/" and other path characters out of the document path.
        if (!Account.USERNAME.matches(name)) return AddResult.NotFound
        if (name == myName) return AddResult.Yourself
        val them = db.collection("usernames").document(name).get().await().getString("uid") ?: return AddResult.NotFound
        if (db.collection("users").document(me).collection("friends").document(them).get().await().exists()) return AddResult.AlreadyFriends
        val theirs = db.collection("requests").document("${them}_$me").get().await()
        if (theirs.exists()) {
            accept(me, myName, FriendRequest(them, me, name, myName))
            return AddResult.Accepted
        }
        db.collection("requests").document("${me}_$them").set(
            mapOf("from" to me, "to" to them, "fromName" to myName, "toName" to name, "createdAt" to FieldValue.serverTimestamp()),
        ).await()
        return AddResult.Sent
    }

    suspend fun accept(me: String, myName: String, request: FriendRequest) {
        val since = FieldValue.serverTimestamp()
        db.runBatch { batch ->
            batch.set(db.collection("users").document(me).collection("friends").document(request.from), mapOf("name" to request.fromName, "since" to since))
            batch.set(db.collection("users").document(request.from).collection("friends").document(me), mapOf("name" to myName, "since" to since))
            batch.delete(db.collection("requests").document(request.id))
        }.await()
    }

    /** Declines a request you got, or cancels one you sent. */
    suspend fun dismiss(request: FriendRequest) {
        db.collection("requests").document(request.id).delete().await()
    }

    suspend fun remove(me: String, friend: String) {
        db.runBatch { batch ->
            batch.delete(db.collection("users").document(me).collection("friends").document(friend))
            batch.delete(db.collection("users").document(friend).collection("friends").document(me))
        }.await()
    }

    /** Your friends' profiles. Null for a friend who chose Private (the rules hide their scores). */
    suspend fun friendProfiles(friends: List<Friend>): Map<Friend, Profile?> = coroutineScope {
        friends.map { friend ->
            async { friend to runCatching { db.collection("users").document(friend.uid).get().await().toProfile() }.getOrNull() }
        }.awaitAll().toMap()
    }

    /** You and your friends. Friends who chose Private still appear, without a score. */
    fun friendsBoard(me: Profile, friends: Map<Friend, Profile?>, period: Period, highFirst: Boolean): List<Standing> {
        val others = friends.map { (friend, profile) ->
            profile?.standing(period, isYou = false) ?: Standing(friend.uid, friend.name, null, false, false)
        }
        return sort(others + me.standing(period, isYou = true), highFirst)
    }

    fun thisWeek(): String = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    /** Public accounts only, from the `public` mirror. */
    suspend fun everyoneBoard(me: String, period: Period, highFirst: Boolean): List<Standing> {
        val field = when (period) {
            Period.TODAY -> "today"
            Period.WEEK -> "week"
            Period.ALL_TIME -> "allTime"
        }
        val direction = if (highFirst) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        // Fetch extra for Today and This week: stale days from people who haven't synced drop out.
        val limit = if (period == Period.ALL_TIME) EVERYONE_LIMIT else EVERYONE_LIMIT * 3
        val docs = db.collection("public").orderBy(field, direction).limit(limit).get().await().documents
        val rows = docs.map { it.toProfile() }.filter { it.isCurrent(period) }.map { it.standing(period, isYou = it.uid == me) }
        return sort(rows, highFirst).take(EVERYONE_LIMIT.toInt())
    }

    private fun sort(rows: List<Standing>, highFirst: Boolean): List<Standing> {
        val scored = rows.filter { it.score != null }.sortedBy { it.score }
        return (if (highFirst) scored.reversed() else scored) + rows.filter { it.score == null }.sortedBy { it.name }
    }

    private fun Profile.isCurrent(period: Period) = when (period) {
        Period.TODAY -> todayDate == LocalDate.now().toString()
        Period.WEEK -> weekStart == thisWeek()
        Period.ALL_TIME -> true
    }

    private fun Profile.standing(period: Period, isYou: Boolean): Standing {
        val score = when (period) {
            Period.TODAY -> if (isCurrent(period)) today else 0
            Period.WEEK -> if (isCurrent(period)) week else 0
            Period.ALL_TIME -> allTime
        }
        return Standing(uid, username, score, gapToday && period == Period.TODAY && isCurrent(period), isYou)
    }
}
