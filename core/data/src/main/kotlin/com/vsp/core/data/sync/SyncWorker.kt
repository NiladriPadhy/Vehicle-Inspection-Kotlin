package com.vsp.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.ReportDao
import com.vsp.core.data.remote.FirebaseRemoteDataSource
import com.vsp.core.model.AppResult
import com.vsp.core.model.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Uploads locally-captured images and generated reports to Firebase when connectivity is
 * available. Offline-first: on failure items are marked FAILED and the work is retried later.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val imageDao: InspectionImageDao,
    private val reportDao: ReportDao,
    private val remote: FirebaseRemoteDataSource,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var anyFailure = false

        imageDao.getPendingUploads().forEach { image ->
            imageDao.upsert(image.copy(syncState = SyncState.UPLOADING.name))
            val remotePath = "inspections/${image.inspectionId}/images/${image.id}.jpg"
            when (val result = remote.uploadImage(remotePath, image.localFilePath)) {
                is AppResult.Success ->
                    imageDao.upsert(image.copy(remoteUrl = result.value, syncState = SyncState.SYNCED.name))
                is AppResult.Failure -> {
                    anyFailure = true
                    imageDao.upsert(image.copy(syncState = SyncState.FAILED.name))
                }
            }
        }

        reportDao.getPendingUploads().forEach { report ->
            reportDao.upsert(report.copy(syncState = SyncState.UPLOADING.name))
            val path = "inspections/${report.inspectionId}/report"
            when (remote.upsertDocument(path, mapOf("json" to report.json, "generatedAt" to report.generatedAt))) {
                is AppResult.Success -> reportDao.upsert(report.copy(syncState = SyncState.SYNCED.name))
                is AppResult.Failure -> {
                    anyFailure = true
                    reportDao.upsert(report.copy(syncState = SyncState.FAILED.name))
                }
            }
        }

        return if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "vsp-sync"
    }
}
