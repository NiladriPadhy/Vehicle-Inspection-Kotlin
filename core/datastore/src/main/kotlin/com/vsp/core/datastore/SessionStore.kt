package com.vsp.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vsp.core.model.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "vsp_session")

/** Persists the authenticated session for offline access after a prior successful login. */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val inspectorId = stringPreferencesKey("inspector_id")
        val displayName = stringPreferencesKey("display_name")
        val email = stringPreferencesKey("email")
        val issuedAt = longPreferencesKey("issued_at")
    }

    val session: Flow<Session?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.inspectorId] ?: return@map null
        Session(
            inspectorId = id,
            displayName = prefs[Keys.displayName].orEmpty(),
            email = prefs[Keys.email].orEmpty(),
            issuedAtMillis = prefs[Keys.issuedAt] ?: 0L,
        )
    }

    suspend fun save(session: Session) {
        context.dataStore.edit { prefs ->
            prefs[Keys.inspectorId] = session.inspectorId
            prefs[Keys.displayName] = session.displayName
            prefs[Keys.email] = session.email
            prefs[Keys.issuedAt] = session.issuedAtMillis
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
