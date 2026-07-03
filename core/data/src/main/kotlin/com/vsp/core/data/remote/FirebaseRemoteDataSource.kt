package com.vsp.core.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over Firestore + Storage used by the sync layer. Firebase instances are resolved
 * lazily and every call is guarded so the app remains functional offline / when
 * google-services.json is absent (see contracts/firestore-contract.md, storage-contract.md).
 */
@Singleton
class FirebaseRemoteDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore? get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    private val storage: FirebaseStorage? get() = runCatching { FirebaseStorage.getInstance() }.getOrNull()

    suspend fun uploadImage(remotePath: String, localFilePath: String): AppResult<String> {
        val ref = storage?.reference?.child(remotePath)
            ?: return AppResult.Failure(AppError.Network(retryable = true))
        return runCatching {
            val file = File(localFilePath)
            ref.putStream(file.inputStream()).await()
            ref.downloadUrl.await().toString()
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(AppError.Network(retryable = true, cause = it)) },
        )
    }

    suspend fun upsertDocument(path: String, data: Map<String, Any?>): AppResult<Unit> {
        val db = firestore ?: return AppResult.Failure(AppError.Network(retryable = true))
        return runCatching { db.document(path).set(data).await() }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(AppError.Network(retryable = true, cause = it)) },
        )
    }
}
