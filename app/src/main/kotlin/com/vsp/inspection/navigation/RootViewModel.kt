package com.vsp.inspection.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsp.core.domain.usecase.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Resolves the persisted session so the app can skip the login screen when already signed in. */
sealed interface StartState {
    data object Loading : StartState
    data class Ready(val loggedIn: Boolean) : StartState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
) : ViewModel() {

    val startState: StateFlow<StartState> =
        observeSession()
            .map { StartState.Ready(loggedIn = it != null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, StartState.Loading)
}
