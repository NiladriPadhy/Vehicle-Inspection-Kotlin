package com.vsp.inspection.feature.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.DeleteImageUseCase
import com.vsp.core.domain.usecase.GetInspectionQuestionnaireUseCase
import com.vsp.core.domain.usecase.ObserveChecklistUseCase
import com.vsp.core.domain.usecase.ObserveImagesUseCase
import com.vsp.core.domain.usecase.ResumeInspectionUseCase
import com.vsp.core.domain.usecase.SaveChecklistItemUseCase
import com.vsp.core.model.CaptureState
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.catalog.Applicability
import com.vsp.core.model.catalog.ChecklistSection
import com.vsp.core.model.catalog.ChecklistStatus
import com.vsp.core.model.config.QuestionnaireCatalog
import com.vsp.inspection.feature.common.sortedByCaptureSequence
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChecklistSectionUiState(
    val section: ChecklistSection? = null,
    val responses: Map<String, ChecklistResponse> = emptyMap(),
    /** All captured images for the inspection, keyed per component by [InspectionImage.checklistItemId]. */
    val imagesByItem: Map<String, List<InspectionImage>> = emptyMap(),
    /** Per-item maximum photo count from the questionnaire config (item id -> max). */
    val maxImagesByItem: Map<String, Int> = emptyMap(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChecklistSectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    resumeInspection: ResumeInspectionUseCase,
    observeChecklist: ObserveChecklistUseCase,
    observeImages: ObserveImagesUseCase,
    getInspectionQuestionnaire: GetInspectionQuestionnaireUseCase,
    private val saveChecklistItem: SaveChecklistItemUseCase,
    private val deleteImageUseCase: DeleteImageUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<VspRoute.ChecklistSection>()
    val inspectionId: String = route.inspectionId
    private val sectionId: String = route.sectionId

    // The questionnaire is pinned at inspection creation, so it is fetched once per subscription.
    private val questionnaire = flow { emit(getInspectionQuestionnaire(inspectionId)) }

    val state: StateFlow<ChecklistSectionUiState> = combine(
        resumeInspection(inspectionId),
        observeChecklist(inspectionId),
        observeImages(inspectionId),
        questionnaire,
    ) { inspection, responses, images, config ->
        val applies = when (inspection?.vehicleCategory) {
            VehicleCategory.OLD -> Applicability.OLD
            else -> Applicability.NEW
        }
        val section = QuestionnaireCatalog.section(config, sectionId, applies)
        val imagesByItem = images
            .filter { it.captureState == CaptureState.CAPTURED && it.checklistItemId != null }
            .sortedByCaptureSequence()
            .groupBy { it.checklistItemId!! }
        ChecklistSectionUiState(
            section = section,
            responses = responses.associateBy { it.itemId },
            imagesByItem = imagesByItem,
            maxImagesByItem = QuestionnaireCatalog.maxImagesById(config),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistSectionUiState())

    private fun base(itemId: String): ChecklistResponse =
        state.value.responses[itemId] ?: ChecklistResponse(
            id = "${inspectionId}_$itemId",
            inspectionId = inspectionId,
            itemId = itemId,
            updatedAt = System.currentTimeMillis(),
        )

    private fun persist(response: ChecklistResponse) {
        viewModelScope.launch { saveChecklistItem(response.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun setStatus(itemId: String, status: ChecklistStatus) =
        persist(base(itemId).copy(status = status))

    fun setRating(itemId: String, rating: Int) =
        persist(base(itemId).copy(rating = rating))

    fun setNumber(itemId: String, value: Double?) =
        persist(base(itemId).copy(numericValue = value))

    fun setText(itemId: String, text: String) =
        persist(base(itemId).copy(textValue = text))

    fun deleteImage(imageId: String) {
        viewModelScope.launch { deleteImageUseCase(imageId) }
    }
}
