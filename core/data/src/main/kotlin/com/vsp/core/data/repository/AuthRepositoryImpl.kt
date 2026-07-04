package com.vsp.core.data.repository

import android.util.Log
import com.vsp.core.data.local.dao.AppUserDao
import com.vsp.core.data.local.dao.InspectorDao
import com.vsp.core.data.local.entity.AppUserEntity
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.data.remote.rtdb.RtdbUserSource
import com.vsp.core.data.security.PasswordHasher
import com.vsp.core.datastore.SessionStore
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.AuthRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.Inspector
import com.vsp.core.model.Session
import com.vsp.core.model.auth.AppUser
import com.vsp.core.model.auth.Credentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom account model backed by Firebase RTDB ([RtdbUserSource]) with a local, offline-capable
 * credential cache ([AppUserDao]).
 *
 * - **Sign-up** derives a salted PBKDF2 hash on-device, writes the user to RTDB, and caches it
 *   locally for offline re-login.
 * - **Sign-in** verifies against RTDB when reachable, else against the local cache.
 * - When no vendor RTDB is configured the repository degrades to a local-only identity so the app
 *   remains fully usable in development/offline (mirrors the previous behaviour).
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionStore: SessionStore,
    private val inspectorDao: InspectorDao,
    private val appUserDao: AppUserDao,
    private val userSource: RtdbUserSource,
    private val passwordHasher: PasswordHasher,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    override val session: Flow<Session?> = sessionStore.session

    override suspend fun signUp(displayName: String, email: String, password: String): AppResult<Session> =
        withContext(dispatchers.io) {
            val normalizedEmail = email.trim().lowercase()
            val credentials = passwordHasher.hash(password)
            val now = System.currentTimeMillis()

            val available = userSource.isAvailable()
            Log.i(TAG, "signUp: RTDB available=$available email=$normalizedEmail")
            if (available) {
                val user = AppUser(
                    uid = UUID.randomUUID().toString(),
                    displayName = displayName,
                    email = normalizedEmail,
                    createdAt = now,
                )
                val created = userSource.createUser(user, credentials)
                Log.i(TAG, "signUp: RTDB createUser=$created uid=${user.uid}")
                if (!created) {
                    val taken = userSource.findUidByEmail(normalizedEmail) != null
                    return@withContext AppResult.Failure(
                        if (taken) AppError.Validation("An account with this email already exists")
                        else AppError.Network(retryable = true),
                    )
                }
                return@withContext persist(user, credentials, now)
            }

            // No vendor RTDB configured → local-only account (offline/dev).
            if (appUserDao.getByEmail(normalizedEmail) != null) {
                return@withContext AppResult.Failure(AppError.Validation("An account with this email already exists"))
            }
            val localUser = AppUser(uid = localUid(normalizedEmail), displayName = displayName, email = normalizedEmail, createdAt = now)
            persist(localUser, credentials, now)
        }

    override suspend fun signIn(email: String, password: String): AppResult<Session> =
        withContext(dispatchers.io) {
            val normalizedEmail = email.trim().lowercase()
            val now = System.currentTimeMillis()

            // 1) Online verification against RTDB.
            if (userSource.isAvailable()) {
                val uid = userSource.findUidByEmail(normalizedEmail)
                val remote = uid?.let { userSource.fetchUser(it) }
                if (remote != null) {
                    return@withContext if (passwordHasher.verify(password, remote.credentials)) {
                        persist(remote.user, remote.credentials, now)
                    } else {
                        AppResult.Failure(AppError.Auth())
                    }
                }
                // Fall through to local cache when the account isn't found remotely.
            }

            // 2) Offline verification against the local credential cache.
            appUserDao.getByEmail(normalizedEmail)?.let { cached ->
                return@withContext if (passwordHasher.verify(password, cached.toCredentials())) {
                    persist(cached.toAppUser(), cached.toCredentials(), now)
                } else {
                    AppResult.Failure(AppError.Auth())
                }
            }

            // 3) No vendor RTDB configured and no cached user → local-only identity (offline/dev).
            if (!userSource.isAvailable()) {
                val credentials = passwordHasher.hash(password)
                val localUser = AppUser(uid = localUid(normalizedEmail), displayName = normalizedEmail.substringBefore('@'), email = normalizedEmail, createdAt = now)
                return@withContext persist(localUser, credentials, now)
            }

            AppResult.Failure(AppError.Auth())
        }

    override suspend fun signOut(): AppResult<Unit> = withContext(dispatchers.io) {
        sessionStore.clear()
        AppResult.Success(Unit)
    }

    override fun hasValidOfflineSession(): Boolean =
        runCatching { runBlocking { sessionStore.session.first() != null } }.getOrDefault(false)

    private suspend fun persist(user: AppUser, credentials: Credentials, now: Long): AppResult<Session> {
        appUserDao.upsert(
            AppUserEntity(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                vendorId = user.vendorId,
                createdAt = user.createdAt,
                algo = credentials.algo,
                iterations = credentials.iterations,
                salt = credentials.salt,
                hash = credentials.hash,
                cachedAt = now,
            ),
        )
        inspectorDao.upsert(Inspector(user.uid, user.displayName, user.email).toEntity())
        val session = Session(
            inspectorId = user.uid,
            displayName = user.displayName,
            email = user.email,
            issuedAtMillis = now,
        )
        sessionStore.save(session)
        return AppResult.Success(session)
    }

    private fun localUid(email: String): String = "local-" + email.hashCode().toUInt().toString()

    private fun AppUserEntity.toCredentials() = Credentials(algo, iterations, salt, hash)
    private fun AppUserEntity.toAppUser() = AppUser(uid, displayName, email, vendorId, createdAt)

    private companion object {
        const val TAG = "VspAuth"
    }
}
