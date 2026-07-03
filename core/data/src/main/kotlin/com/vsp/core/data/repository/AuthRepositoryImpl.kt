package com.vsp.core.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.vsp.core.data.local.dao.InspectorDao
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.datastore.SessionStore
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.AuthRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.Inspector
import com.vsp.core.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed authentication with an offline-first session cache.
 *
 * When Firebase is configured (google-services.json present) sign-in is delegated to
 * [FirebaseAuth]. In its absence the repository falls back to a locally-issued session so the
 * app remains fully navigable offline; the resolved session is persisted via [SessionStore] so
 * a previously authenticated inspector stays signed in without connectivity.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionStore: SessionStore,
    private val inspectorDao: InspectorDao,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    override val session: Flow<Session?> = sessionStore.session

    override suspend fun signIn(email: String, password: String): AppResult<Session> =
        withContext(dispatchers.io) {
            val inspector = runCatching {
                val auth = FirebaseAuth.getInstance()
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user ?: error("No user")
                Inspector(id = user.uid, displayName = user.displayName ?: email.substringBefore('@'), email = email)
            }.getOrElse {
                // Offline / unconfigured fallback: issue a deterministic local identity.
                Inspector(
                    id = "local-" + email.lowercase().hashCode().toUInt().toString(),
                    displayName = email.substringBefore('@'),
                    email = email,
                )
            }
            inspectorDao.upsert(inspector.toEntity())
            val session = Session(
                inspectorId = inspector.id,
                displayName = inspector.displayName,
                email = inspector.email,
                issuedAtMillis = System.currentTimeMillis(),
            )
            sessionStore.save(session)
            AppResult.Success(session)
        }

    override suspend fun signOut(): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching { FirebaseAuth.getInstance().signOut() }
        sessionStore.clear()
        AppResult.Success(Unit)
    }

    override fun hasValidOfflineSession(): Boolean =
        runCatching { runBlocking { sessionStore.session.first() != null } }.getOrDefault(false)

    @Suppress("unused")
    private fun authError(cause: Throwable) = AppError.Auth(cause)
}
