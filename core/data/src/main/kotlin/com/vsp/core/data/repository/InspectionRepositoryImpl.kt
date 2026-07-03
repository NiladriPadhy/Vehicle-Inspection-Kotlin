package com.vsp.core.data.repository

import com.vsp.core.data.io.FileStore
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.VehicleDao
import com.vsp.core.data.mapper.toDomain
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.data.sync.SyncScheduler
import com.vsp.core.domain.completeness.CompletenessCalculator
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.InspectionRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.CaptureState
import com.vsp.core.model.Completeness
import com.vsp.core.model.Inspection
import com.vsp.core.model.InspectionContext
import com.vsp.core.model.InspectionStatus
import com.vsp.core.model.SyncState
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InspectionRepositoryImpl @Inject constructor(
    private val inspectionDao: InspectionDao,
    private val vehicleDao: VehicleDao,
    private val imageDao: InspectionImageDao,
    private val completenessCalculator: CompletenessCalculator,
    private val syncScheduler: SyncScheduler,
    private val fileStore: FileStore,
    private val dispatchers: DispatcherProvider,
) : InspectionRepository {

    override fun observeInspections(inspectorId: String): Flow<List<Inspection>> =
        inspectionDao.observeForInspector(inspectorId).map { list -> list.map { it.toDomain() } }

    override fun observeInspection(id: String): Flow<Inspection?> =
        inspectionDao.observe(id).map { it?.toDomain() }

    override suspend fun startInspection(
        inspectorId: String,
        context: InspectionContext,
        category: VehicleCategory,
    ): AppResult<Inspection> = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        val vehicleId = UUID.randomUUID().toString()
        vehicleDao.upsert(Vehicle(id = vehicleId, category = category).toEntity())
        val inspection = Inspection(
            id = UUID.randomUUID().toString(),
            inspectorId = inspectorId,
            vehicleId = vehicleId,
            context = context,
            vehicleCategory = category,
            status = InspectionStatus.IN_PROGRESS,
            currentStep = STEP_IDENTIFY,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING,
        )
        inspectionDao.upsert(inspection.toEntity())
        AppResult.Success(inspection)
    }

    override suspend fun updateStep(id: String, step: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            inspectionDao.updateStep(id, step, System.currentTimeMillis())
            AppResult.Success(Unit)
        }

    override suspend fun getCompleteness(id: String): AppResult<Completeness> =
        withContext(dispatchers.io) {
            val inspection = inspectionDao.getById(id)
                ?: return@withContext AppResult.Failure(AppError.Validation("Inspection not found"))
            val images = imageDao.getForInspection(id).map { it.toDomain() }
            AppResult.Success(
                completenessCalculator.calculate(
                    VehicleCategory.valueOf(inspection.vehicleCategory),
                    images,
                ),
            )
        }

    override suspend fun finalize(id: String): AppResult<Unit> = withContext(dispatchers.io) {
        val inspection = inspectionDao.getById(id)?.toDomain()
            ?: return@withContext AppResult.Failure(AppError.Validation("Inspection not found"))
        val images = imageDao.getForInspection(id).map { it.toDomain() }
        // Checklist-first flow: capture is inspector-driven per component, so finalizing only
        // requires at least one captured photo rather than the legacy mandatory-position set.
        val hasCapture = images.any { it.captureState == CaptureState.CAPTURED }
        if (!hasCapture) {
            return@withContext AppResult.Failure(
                AppError.Validation("Capture at least one photo before finalizing"),
            )
        }
        inspectionDao.upsert(
            inspection.copy(
                status = InspectionStatus.COMPLETED,
                completedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ).toEntity(),
        )
        syncScheduler.requestSync()
        AppResult.Success(Unit)
    }

    override suspend fun deleteInspection(id: String): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            // Cascading FKs remove images, findings, annotations, checklist responses, report rows.
            inspectionDao.deleteById(id)
            fileStore.deleteInspection(id)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(AppError.Storage(it.message ?: "Failed to delete inspection")) },
        )
    }

    companion object {
        const val STEP_IDENTIFY = "IDENTIFY"
    }
}
