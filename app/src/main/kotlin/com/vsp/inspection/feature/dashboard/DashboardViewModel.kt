package com.vsp.inspection.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsp.core.domain.usecase.DeleteInspectionUseCase
import com.vsp.core.domain.usecase.ObserveInspectionsUseCase
import com.vsp.core.domain.usecase.ObserveSessionUseCase
import com.vsp.core.domain.usecase.SignOutUseCase
import com.vsp.core.model.Inspection
import com.vsp.core.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val displayName: String = "",
    val inspections: List<Inspection> = emptyList(),
    val loading: Boolean = true,
    val signedOut: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    observeInspections: ObserveInspectionsUseCase,
    private val signOut: SignOutUseCase,
    private val deleteInspection: DeleteInspectionUseCase,
) : ViewModel() {

    private val sessionFlow = observeSession()

    val state: StateFlow<DashboardUiState> =
        sessionFlow.flatMapLatest { session: Session? ->
            if (session == null) {
                flowOf(DashboardUiState(loading = false, signedOut = true))
            } else {
                observeInspections(session.inspectorId).combine(flowOf(session)) { list, s ->
                    DashboardUiState(
                        displayName = s.displayName,
                        inspections = list,
                        loading = false,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun signOut() {
        viewModelScope.launch { signOut.invoke() }
    }

    fun delete(inspectionId: String) {
        viewModelScope.launch { deleteInspection(inspectionId) }
    }
}
