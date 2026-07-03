package com.vsp.inspection.feature.olddocs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.StepProgress
import com.vsp.core.ui.components.VspScaffold
import com.vsp.core.ui.components.VspTextField
import com.vsp.inspection.feature.capture.CameraCapture

@Composable
fun OldVehicleDocsScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: OldVehicleDocsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) { if (state.done) onContinue() }

    VspScaffold(title = "Old-vehicle documents", onBack = onBack) { padding ->
        if (!state.loaded) {
            LoadingOverlay()
            return@VspScaffold
        }

        val slot = state.currentSlot
        if (slot != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "Capture: ${slot.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        StepProgress(
                            current = state.capturedTypes.size + 1,
                            total = state.slots.size,
                            label = "Documents",
                        )
                        state.error?.let {
                            ErrorBanner(message = it, modifier = Modifier.padding(top = 8.dp)) { viewModel.clearError() }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    CameraCapture(instruction = "", onImageCaptured = viewModel::captureDocument)
                    if (state.isBusy) LoadingOverlay(message = "Checking quality…")
                }
            }
            return@VspScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("All documents captured", style = MaterialTheme.typography.headlineSmall)
            state.error?.let { ErrorBanner(message = it) }
            VspTextField(
                value = state.numberOfOwnerships,
                onValueChange = viewModel::onOwnershipsChange,
                label = "Number of previous ownerships",
                keyboardType = KeyboardType.Number,
            )
            VspTextField(
                value = state.numberOfKeys,
                onValueChange = viewModel::onKeysChange,
                label = "Number of keys",
                keyboardType = KeyboardType.Number,
            )
            PrimaryButton(
                text = if (state.isBusy) "Saving…" else "Continue to exterior",
                onClick = viewModel::submit,
                enabled = state.canSubmit && !state.isBusy,
            )
        }
    }
}
