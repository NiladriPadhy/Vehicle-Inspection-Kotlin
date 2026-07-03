package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.AiAnalysisRepository
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.AppResult
import com.vsp.core.model.FinalVerification
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.ReverifyResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFindingsUseCase @Inject constructor(
    private val aiAnalysisRepository: AiAnalysisRepository,
) {
    operator fun invoke(imageId: String): Flow<List<AIFinding>> =
        aiAnalysisRepository.observeFindings(imageId)
}

class AnalyzeImageUseCase @Inject constructor(
    private val aiAnalysisRepository: AiAnalysisRepository,
) {
    suspend operator fun invoke(image: InspectionImage): AppResult<List<AIFinding>> =
        aiAnalysisRepository.analyzeImage(image)
}

class ReverifyAnnotationUseCase @Inject constructor(
    private val aiAnalysisRepository: AiAnalysisRepository,
) {
    suspend operator fun invoke(
        image: InspectionImage,
        annotation: Annotation,
    ): AppResult<ReverifyResult> = aiAnalysisRepository.reverifyAnnotation(image, annotation)
}

class RunFinalVerificationUseCase @Inject constructor(
    private val aiAnalysisRepository: AiAnalysisRepository,
) {
    suspend operator fun invoke(inspectionId: String): AppResult<FinalVerification> =
        aiAnalysisRepository.runFinalVerification(inspectionId)
}
