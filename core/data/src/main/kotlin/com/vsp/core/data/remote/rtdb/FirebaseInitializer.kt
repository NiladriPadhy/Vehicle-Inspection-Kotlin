package com.vsp.core.data.remote.rtdb

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily initializes a dedicated [FirebaseApp] for the configured vendor RTDB and exposes the
 * [FirebaseDatabase] plus an anonymous-auth gate.
 *
 * The custom user model lives in RTDB (not Firebase Auth), but the RTDB security rules still require
 * `auth != null`; [ensureAuth] performs an anonymous sign-in purely to satisfy those rules so the
 * credential store is never world-readable (see plan §8 O-2). Everything is guarded so a missing or
 * unreachable configuration degrades gracefully to offline baseline mode.
 */
@Singleton
class FirebaseInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: FirebaseConfig,
) {
    @Volatile private var app: FirebaseApp? = null

    private fun appOrNull(): FirebaseApp? {
        if (!config.isConfigured) {
            Log.w(TAG, "RTDB disabled — config incomplete (dbUrl='${config.databaseUrl}', appId blank=${config.applicationId.isBlank()}, apiKey blank=${config.apiKey.isBlank()})")
            return null
        }
        app?.let { return it }
        return synchronized(this) {
            app ?: runCatching {
                try {
                    FirebaseApp.getInstance(APP_NAME)
                } catch (_: IllegalStateException) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId(config.applicationId)
                        .setApiKey(config.apiKey)
                        .setProjectId(config.projectId.ifBlank { null })
                        .setDatabaseUrl(config.databaseUrl)
                        .build()
                    FirebaseApp.initializeApp(context, options, APP_NAME)
                }
            }.getOrNull()?.also { app = it }
        }
    }

    fun database(): FirebaseDatabase? = appOrNull()?.let { fbApp ->
        runCatching { FirebaseDatabase.getInstance(fbApp, config.databaseUrl) }.getOrNull()
    }

    /** Ensures an anonymous Firebase session exists so RTDB `auth != null` rules pass. */
    suspend fun ensureAuth(): Boolean {
        val fbApp = appOrNull() ?: return false
        val auth = runCatching { FirebaseAuth.getInstance(fbApp) }.getOrNull() ?: return false
        if (auth.currentUser != null) return true
        return runCatching { auth.signInAnonymously().await(); Log.i(TAG, "Anonymous auth OK"); true }
            .getOrElse {
                Log.w(TAG, "Anonymous auth failed — enable Anonymous sign-in in Firebase console", it)
                false
            }
    }

    companion object {
        private const val APP_NAME = "vsp-vendor"
        private const val TAG = "FirebaseInitializer"
    }
}
