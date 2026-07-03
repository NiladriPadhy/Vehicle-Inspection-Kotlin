package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.AnnotationRepository
import com.vsp.core.model.Annotation
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAnnotationsUseCase @Inject constructor(
    private val annotationRepository: AnnotationRepository,
) {
    operator fun invoke(imageId: String): Flow<List<Annotation>> =
        annotationRepository.observeAnnotations(imageId)
}

class AddAnnotationUseCase @Inject constructor(
    private val annotationRepository: AnnotationRepository,
) {
    suspend operator fun invoke(annotation: Annotation): AppResult<Annotation> {
        if (annotation.geometryJson.isBlank()) {
            return AppResult.Failure(AppError.Validation("Annotation geometry is required"))
        }
        return annotationRepository.add(annotation)
    }
}

class UpdateAnnotationUseCase @Inject constructor(
    private val annotationRepository: AnnotationRepository,
) {
    suspend operator fun invoke(annotation: Annotation): AppResult<Unit> =
        annotationRepository.update(annotation)
}

class DeleteAnnotationUseCase @Inject constructor(
    private val annotationRepository: AnnotationRepository,
) {
    suspend operator fun invoke(annotationId: String): AppResult<Unit> =
        annotationRepository.delete(annotationId)
}
