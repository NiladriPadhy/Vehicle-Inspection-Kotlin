package com.vsp.core.data.repository

import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.ReportDao
import com.vsp.core.data.local.dao.SyncTaskDao
import com.vsp.core.data.local.entity.SyncTaskEntity
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.SyncRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.SyncState
import com.vsp.core.model.SyncSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val imageDao: InspectionImageDao,
    private val reportDao: ReportDao,
    private val syncTaskDao: SyncTaskDao,
    private val dispatchers: DispatcherProvider,
) : SyncRepository {

    override fun observeSyncStatus(inspectionId: String): Flow<SyncSummary> =
        combine(
            imageDao.observeForInspection(inspectionId),
            reportDao.observe(inspectionId),
        ) { images, report ->
            val states = images.map { SyncState.valueOf(it.syncState) }.toMutableList()
            report?.let { states.add(SyncState.valueOf(it.syncState)) }
            SyncSummary(
                pending = states.count { it == SyncState.PENDING },
                uploading = states.count { it == SyncState.UPLOADING },
                synced = states.count { it == SyncState.SYNCED },
                failed = states.count { it == SyncState.FAILED },
            )
        }

    override suspend fun enqueue(entityType: String, entityId: String, op: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            syncTaskDao.upsert(
                SyncTaskEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = entityType,
                    entityId = entityId,
                    operation = op,
                    status = SyncState.PENDING.name,
                    attemptCount = 0,
                    lastAttemptAt = null,
                    lastError = null,
                ),
            )
            AppResult.Success(Unit)
        }

    override suspend fun retryFailed(inspectionId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            syncTaskDao.getByStatus(SyncState.FAILED.name).forEach { task ->
                syncTaskDao.upsert(task.copy(status = SyncState.PENDING.name, lastError = null))
            }
            AppResult.Success(Unit)
        }
}
