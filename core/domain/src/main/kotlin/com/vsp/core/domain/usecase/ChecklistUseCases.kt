package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.ChecklistRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.ChecklistResponse
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveChecklistUseCase @Inject constructor(
    private val checklistRepository: ChecklistRepository,
) {
    operator fun invoke(inspectionId: String): Flow<List<ChecklistResponse>> =
        checklistRepository.observeResponses(inspectionId)
}

class SaveChecklistItemUseCase @Inject constructor(
    private val checklistRepository: ChecklistRepository,
) {
    suspend operator fun invoke(response: ChecklistResponse): AppResult<Unit> =
        checklistRepository.save(response)
}
