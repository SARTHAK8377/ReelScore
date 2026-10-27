package com.reelscore.app.account

import android.content.Context
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.reelscore.app.R
import com.reelscore.app.sync.ScoreUpload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class Privacy(val key: String, val label: String, val detail: String) {
    PUBLIC("public", "Public", "Anyone on ReelScore can see your scores"),
    FRIENDS("friends", "Friends only", "Only your friends can see your scores"),
    PRIVATE("private", "Private", "Only you can see your scores");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: FRIENDS
    }
}

/** A shared score card. [today] and [week] only count when their date matches the reader's. */
data class Profile(
    val uid: String,
    val username: String,
    val privacy: Privacy,
    val today: Long,
    val todayDate: String,
    val week: Long,
    val weekStart: String,
    val allTime: Long,
    val gapToday: Boolean,
)

class UsernameTakenException : Exception("That username is taken")

/**
 * Optional Google sign-in through Firebase. Online, an account holds only a username, a privacy
 * choice, friends and three totals; every reel and app stays on the phone. All access is checked
 * by the Firestore rules in firestore.rules, not just by this code.
 */
object Account {
    private const val PREFS = "account"
    private const val KEY_USERNAME = "username"
    private const val KEY_PRIVACY = "privacy"

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    val USERNAME = Regex("^[a-z0-9_.]{3,20}$")

    fun user(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Your own profile, live. Null until a username is picked. */
    fun profile(uid: String): Flow<Profile?> = callbackFlow {
        val reg = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.takeIf { it.exists() }?.let { it.toProfile() })
        }
        awaitClose { reg.remove() }
    }

    /** Shows Google's account picker, then signs in to Firebase. Needs an Activity context. */
    suspend fun signIn(context: Context) {
        auth.signInWithCredential(googleCredential(context)).await()
    }

    /** What to tell the user when [signIn] fails, or null when they simply closed the picker. */
    fun signInFailureMessage(error: Throwable): String? = when (error) {
        is androidx.credentials.exceptions.GetCredentialCancellationException -> null
        is androidx.credentials.exceptions.NoCredentialException -> "Add a Google account to this phone, then try again"
        is androidx.credentials.exceptions.GetCredentialProviderConfigurationException ->
            "Sign-in needs Google Play services, which this phone doesn't have"
        else -> "Sign-in didn't finish. Check your connection."
    }

    private suspend fun googleCredential(context: Context) = run {
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val result = CredentialManager.create(context)
            .getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
    }

    suspend fun isTaken(name: String): Boolean =
        db.collection("usernames").document(name).get().await().let { it.exists() && it.getString("uid") != uid }

    /** Claims [name] and creates the profile in one transaction, so two people can't share a name. */
    suspend fun claimUsername(context: Context, name: String, privacy: Privacy) {
        val me = uid ?: return
        val nameRef = db.collection("usernames").document(name)
        val userRef = db.collection("users").document(me)
        db.runTransaction { tx ->
            val existing = tx.get(nameRef)
            if (existing.exists() && existing.getString("uid") != me) throw UsernameTakenException()
            tx.set(nameRef, mapOf("uid" to me))
            tx.set(userRef, ScoreUpload.emptyScores() + mapOf("username" to name, "privacy" to privacy.key))
        }.await()
        remember(context, name, privacy)
        ScoreUpload.now(context)
    }

    /** Outlives any screen, so a save keeps going after the user leaves Settings. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val pending = MutableStateFlow<Privacy?>(null)

    /** The privacy being saved right now, or null. Screens show it in place of the online value. */
    val pendingPrivacy: StateFlow<Privacy?> = pending.asStateFlow()

    private val failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a privacy change couldn't be saved and was undone. */
    val privacyFailures: SharedFlow<Unit> = failures.asSharedFlow()

    /**
     * Saves [privacy] together with fresh totals, so the public copy is added or removed in the
     * same write. The choice is kept on the phone at once; if the write fails, it goes back.
     * Runs outside any screen, so leaving Settings doesn't stop it.
     */
    fun changePrivacy(context: Context, privacy: Privacy) {
        val app = context.applicationContext
        val before = privacy(app)
        prefs(app).edit { putString(KEY_PRIVACY, privacy.key) }
        pending.value = privacy
        appScope.launch {
            try {
                ScoreUpload.writePrivacy(app, privacy)
            } catch (e: Exception) {
                // Only undo if no newer choice has been made meanwhile.
                if (privacy(app) == privacy) {
                    prefs(app).edit { putString(KEY_PRIVACY, before.key) }
                    failures.tryEmit(Unit)
                }
            } finally {
                if (pending.value == privacy) pending.value = null
            }
        }
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        prefs(context).edit { clear() }
        ScoreUpload.forget(context)
        // Drops the offline copy of friends' scores, so the next account on this phone can't see it.
        runCatching {
            val old = db
            old.terminate().await()
            old.clearPersistence().await()
        }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /**
     * Removes everything this account put online, then the login itself. Asks Google again first,
     * because Firebase only deletes a login that signed in recently.
     */
    suspend fun deleteAccount(context: Context) {
        val user = auth.currentUser ?: return
        user.reauthenticate(googleCredential(context)).await()
        val me = user.uid
        val friends = db.collection("users").document(me).collection("friends").get().await()
        val sent = db.collection("requests").whereEqualTo("from", me).get().await()
        val received = db.collection("requests").whereEqualTo("to", me).get().await()
        val name = db.collection("users").document(me).get().await().getString("username")
        db.runBatch { batch ->
            friends.documents.forEach {
                batch.delete(it.reference)
                batch.delete(db.collection("users").document(it.id).collection("friends").document(me))
            }
            (sent.documents + received.documents).forEach { batch.delete(it.reference) }
            batch.delete(db.collection("public").document(me))
            if (name != null) batch.delete(db.collection("usernames").document(name))
            batch.delete(db.collection("users").document(me))
        }.await()
        user.delete().await()
        signOut(context)
    }

    fun username(context: Context): String? = prefs(context).getString(KEY_USERNAME, null)

    fun privacy(context: Context): Privacy = Privacy.of(prefs(context).getString(KEY_PRIVACY, null))

    /** Keeps the username and privacy on the phone so uploads don't need an extra read. */
    fun remember(context: Context, name: String, privacy: Privacy) = prefs(context).edit {
        putString(KEY_USERNAME, name)
        putString(KEY_PRIVACY, privacy.key)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal fun com.google.firebase.firestore.DocumentSnapshot.toProfile() = Profile(
    uid = id,
    username = getString("username").orEmpty(),
    privacy = Privacy.of(getString("privacy")),
    today = getLong("today") ?: 0,
    todayDate = getString("todayDate").orEmpty(),
    week = getLong("week") ?: 0,
    weekStart = getString("weekStart").orEmpty(),
    allTime = getLong("allTime") ?: 0,
    gapToday = getBoolean("gapToday") ?: false,
)
