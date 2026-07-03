package com.vsp.core.data.repository

import com.vsp.core.data.image.ImageQualityAnalyzer
import com.vsp.core.data.io.FileStore
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.mapper.toDomain
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.ImageRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.CaptureState
import com.vsp.core.model.DocumentType
import com.vsp.core.model.ImageQuality
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val imageDao: InspectionImageDao,
    private val fileStore: FileStore,
    private val qualityAnalyzer: ImageQualityAnalyzer,
    private val dispatchers: DispatcherProvider,
) : ImageRepository {

    override fun observeImages(inspectionId: String): Flow<List<InspectionImage>> =
        imageDao.observeForInspection(inspectionId).map { list -> list.map { it.toDomain() } }

    override fun observeImage(imageId: String): Flow<InspectionImage?> =
        imageDao.observeById(imageId).map { it?.toDomain() }

    override suspend fun captureImage(
        inspectionId: String,
        section: Section,
        position: String,
        rawImagePath: String,
    ): AppResult<InspectionImage> = withContext(dispatchers.io) {
        val quality = qualityAnalyzer.analyze(rawImagePath)
        if (quality != ImageQuality.OK) {
            return@withContext AppResult.Failure(AppError.ImageQualityError(quality))
        }
        val source = File(rawImagePath)
        if (!source.exists()) {
            return@withContext AppResult.Failure(AppError.Storage("Captured file missing"))
        }
        val existing = imageDao.findSlot(inspectionId, section.name, position)
        val imageId = existing?.id ?: UUID.randomUUID().toString()
        val storedPath = fileStore.copyImageFrom(inspectionId, imageId, source)
        source.delete()

        val image = InspectionImage(
            id = imageId,
            inspectionId = inspectionId,
            section = section,
            position = position,
            documentType = if (section == Section.DOCUMENT) runCatching { DocumentType.valueOf(position) }.getOrNull() else null,
            captureState = CaptureState.CAPTURED,
            skipReason = null,
            localFilePath = storedPath,
            sizeBytes = File(storedPath).length(),
            capturedAt = System.currentTimeMillis(),
            quality = quality,
            aiState = SyncState.PENDING,
            syncState = SyncState.PENDING,
        )
        imageDao.upsert(image.toEntity())
        AppResult.Success(image)
    }

    override suspend fun captureSectionImage(
        inspectionId: String,
        section: Section,
        checklistSectionId: String,
        checklistItemId: String?,
        rawImagePath: String,
    ): AppResult<InspectionImage> = withContext(dispatchers.io) {
        val quality = qualityAnalyzer.analyze(rawImagePath)
        if (quality != ImageQuality.OK) {
            return@withContext AppResult.Failure(AppError.ImageQualityError(quality))
        }
        val source = File(rawImagePath)
        if (!source.exists()) {
            return@withContext AppResult.Failure(AppError.Storage("Captured file missing"))
        }
        val imageId = UUID.randomUUID().toString()
        // Unique free-form position per checklist item/section so each keeps its own image set.
        val position = "${checklistItemId ?: checklistSectionId}_$imageId"
        val storedPath = fileStore.copyImageFrom(inspectionId, imageId, source)
        source.delete()

        val image = InspectionImage(
            id = imageId,
            inspectionId = inspectionId,
            section = section,
            position = position,
            documentType = null,
            checklistSectionId = checklistSectionId,
            checklistItemId = checklistItemId,
            captureState = CaptureState.CAPTURED,
            skipReason = null,
            localFilePath = storedPath,
            sizeBytes = File(storedPath).length(),
            capturedAt = System.currentTimeMillis(),
            quality = quality,
            aiState = SyncState.PENDING,
            syncState = SyncState.PENDING,
        )
        imageDao.upsert(image.toEntity())
        AppResult.Success(image)
    }

    override suspend fun skipPosition(
        inspectionId: String,
        section: Section,
        position: String,
        reason: String,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        if (reason.isBlank()) {
            return@withContext AppResult.Failure(AppError.Validation("A skip reason is required"))
        }
        val existing = imageDao.findSlot(inspectionId, section.name, position)
        val imageId = existing?.id ?: UUID.randomUUID().toString()
        val image = (existing?.toDomain() ?: InspectionImage(
            id = imageId,
            inspectionId = inspectionId,
            section = section,
            position = position,
        )).copy(captureState = CaptureState.SKIPPED, skipReason = reason)
        imageDao.upsert(image.toEntity())
        AppResult.Success(Unit)
    }

    override suspend fun deleteImage(imageId: String): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            imageDao.getById(imageId)?.localFilePath?.takeIf { it.isNotBlank() }?.let { fileStore.delete(it) }
            imageDao.deleteById(imageId)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(AppError.Storage(it.message ?: "Failed to delete image")) },
        )
    }

    override suspend fun validateQuality(imagePath: String): AppResult<ImageQuality> =
        withContext(dispatchers.io) {
            val quality = qualityAnalyzer.analyze(imagePath)
            if (quality == ImageQuality.OK) AppResult.Success(quality)
            else AppResult.Failure(AppError.ImageQualityError(quality))
        }
}
