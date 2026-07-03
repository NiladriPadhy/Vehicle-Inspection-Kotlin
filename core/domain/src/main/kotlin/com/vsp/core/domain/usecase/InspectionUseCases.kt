package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.ImageRepository
import com.vsp.core.domain.repository.InspectionRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.Completeness
import com.vsp.core.model.Inspection
import com.vsp.core.model.InspectionContext
import com.vsp.core.model.VehicleCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInspectionsUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    operator fun invoke(inspectorId: String): Flow<List<Inspection>> =
        inspectionRepository.observeInspections(inspectorId)
}

class StartInspectionUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    suspend operator fun invoke(
        inspectorId: String,
        context: InspectionContext,
        category: VehicleCategory,
    ): AppResult<Inspection> = inspectionRepository.startInspection(inspectorId, context, category)
}

class ResumeInspectionUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    operator fun invoke(id: String): Flow<Inspection?> = inspectionRepository.observeInspection(id)
}

class GetInspectionCompletenessUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Completeness> =
        inspectionRepository.getCompleteness(id)
}

class FinalizeInspectionUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = inspectionRepository.finalize(id)
}

class DeleteInspectionUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    suspend operator fun invoke(id: String): AppResult<Unit> = inspectionRepository.deleteInspection(id)
}

class ValidateImageQualityUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
) {
    suspend operator fun invoke(imagePath: String) = imageRepository.validateQuality(imagePath)
}
