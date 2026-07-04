package com.vsp.inspection.feature.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.GetInspectionQuestionnaireUseCase
import com.vsp.core.domain.usecase.ObserveChecklistUseCase
import com.vsp.core.domain.usecase.ResumeInspectionUseCase
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.catalog.Applicability
import com.vsp.core.model.catalog.ChecklistSection
import com.vsp.core.model.config.QuestionnaireCatalog
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SectionRow(
    val section: ChecklistSection,
    val answered: Int,
    val total: Int,
) {
    val complete: Boolean get() = total > 0 && answered >= total
}

data class ChecklistHubUiState(
    val sections: List<SectionRow> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChecklistHubViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    resumeInspection: ResumeInspectionUseCase,
    observeChecklist: ObserveChecklistUseCase,
    getInspectionQuestionnaire: GetInspectionQuestionnaireUseCase,
) : ViewModel() {

    val inspectionId: String = savedStateHandle.toRoute<VspRoute.ChecklistHub>().inspectionId

    // The questionnaire is pinned at inspection creation, so it is fetched once per subscription.
    private val questionnaire = flow { emit(getInspectionQuestionnaire(inspectionId)) }

    val state: StateFlow<ChecklistHubUiState> = combine(
        resumeInspection(inspectionId),
        observeChecklist(inspectionId),
        questionnaire,
    ) { inspection, responses, config ->
        val applies = when (inspection?.vehicleCategory) {
            VehicleCategory.OLD -> Applicability.OLD
            else -> Applicability.NEW
        }
        val answeredIds = responses.filter { it.isAnswered }.map { it.itemId }.toSet()
        val rows = QuestionnaireCatalog.sections(config, applies).map { section ->
            val ids = section.allItems.map { it.id }
            SectionRow(
                section = section,
                answered = ids.count { it in answeredIds },
                total = ids.size,
            )
        }
        ChecklistHubUiState(sections = rows, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistHubUiState())
}
