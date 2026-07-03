package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.ImageRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
) {
    operator fun invoke(inspectionId: String): Flow<List<InspectionImage>> =
        imageRepository.observeImages(inspectionId)
}

class ObserveImageUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
) {
    operator fun invoke(imageId: String): Flow<InspectionImage?> =
        imageRepository.observeImage(imageId)
}

class CaptureImageUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
) {
    suspend operator fun invoke(
        inspectionId: String,
        section: Section,
        position: String,
        rawImagePath: String,
    ): AppResult<InspectionImage> =
        imageRepository.captureImage(inspectionId, section, position, rawImagePath)
}

class CaptureSectionImageUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
) {
    suspend operator fun invoke(
        inspectionId: String,
        section: Section,
        checklistSectionId: String,
        checklistItemId: String?,
        rawImagePath: String,
    ): AppResult<InspectionImage> =
        imageRepository.captureSectionImage(inspectionId, section, checklistSectionId, checklistItemId, rawImagePath)
}

class DeleteImageUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
) {
    suspend operator fun invoke(imageId: String): AppResult<Unit> = imageRepository.deleteImage(imageId)
}

class SkipPositionUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
) {
    suspend operator fun invoke(
        inspectionId: String,
        section: Section,
        position: String,
        reason: String,
    ): AppResult<Unit> = imageRepository.skipPosition(inspectionId, section, position, reason)
}
