package com.vsp.core.data.remote.rtdb

import android.util.Log
import com.vsp.core.model.auth.AppUser
import com.vsp.core.model.auth.Credentials
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A user record as stored in RTDB (profile + salted credential material). */
data class RemoteUser(val user: AppUser, val credentials: Credentials)

/**
 * Custom user store backed by Firebase RTDB (no Firebase Auth for credentials).
 *
 *   /users/{uid}          = { profile:{displayName,email,createdAt}, auth:{algo,iterations,salt,hash} }
 *   /usersByEmail/{key}   = uid
 *
 * where `key` is the email with RTDB-illegal characters escaped. Passwords are never stored; only
 * the PBKDF2 hash + salt are persisted. Reads/writes are gated by anonymous auth.
 */
@Singleton
class RtdbUserSource @Inject constructor(
    private val firebase: FirebaseInitializer,
) {
    /**
     * True when a vendor RTDB is configured. Anonymous auth is attempted best-effort (to satisfy
     * rules that require `auth != null`) but is not required — with open rules, writes still succeed
     * without it, and genuine permission failures surface via [createUser]'s logged error.
     */
    suspend fun isAvailable(): Boolean {
        firebase.database() ?: return false
        firebase.ensureAuth()
        return true
    }

    suspend fun findUidByEmail(email: String): String? {
        val db = firebase.database() ?: return null
        firebase.ensureAuth()
        return runCatching {
            db.getReference("usersByEmail").child(emailKey(email)).get().await().getValue(String::class.java)
        }.onFailure { Log.w(TAG, "findUidByEmail failed", it) }.getOrNull()
    }

    suspend fun fetchUser(uid: String): RemoteUser? {
        val db = firebase.database() ?: return null
        firebase.ensureAuth()
        return runCatching {
            val snap = db.getReference("users").child(uid).get().await()
            val profile = snap.child("profile")
            val auth = snap.child("auth")
            val email = profile.child("email").getValue(String::class.java) ?: return null
            RemoteUser(
                user = AppUser(
                    uid = uid,
                    displayName = profile.child("displayName").getValue(String::class.java) ?: email.substringBefore('@'),
                    email = email,
                    createdAt = profile.child("createdAt").getValue(Long::class.java) ?: 0L,
                ),
                credentials = Credentials(
                    algo = auth.child("algo").getValue(String::class.java) ?: "",
                    iterations = (auth.child("iterations").getValue(Long::class.java) ?: 0L).toInt(),
                    salt = auth.child("salt").getValue(String::class.java) ?: "",
                    hash = auth.child("hash").getValue(String::class.java) ?: "",
                ),
            )
        }.onFailure { Log.w(TAG, "fetchUser failed", it) }.getOrNull()
    }

    /** Creates a new user; returns false if the email is already taken or the write fails. */
    suspend fun createUser(user: AppUser, credentials: Credentials): Boolean {
        val db = firebase.database() ?: return false
        firebase.ensureAuth()
        return runCatching {
            val key = emailKey(user.email)
            val existing = db.getReference("usersByEmail").child(key).get().await().getValue(String::class.java)
            if (existing != null) return false
            val payload = mapOf(
                "profile" to mapOf(
                    "displayName" to user.displayName,
                    "email" to user.email,
                    "createdAt" to user.createdAt,
                ),
                "auth" to mapOf(
                    "algo" to credentials.algo,
                    "iterations" to credentials.iterations,
                    "salt" to credentials.salt,
                    "hash" to credentials.hash,
                ),
            )
            db.getReference("users").child(user.uid).setValue(payload).await()
            db.getReference("usersByEmail").child(key).setValue(user.uid).await()
            true
        }.onFailure { Log.w(TAG, "createUser failed", it) }.getOrDefault(false)
    }

    private fun emailKey(email: String): String =
        email.trim().lowercase().replace(Regex("[.#$\\[\\]/]"), ",")

    companion object {
        private const val TAG = "RtdbUserSource"
    }
}
