package com.vsp.inspection.feature.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.model.Section
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.StepProgress
import com.vsp.core.ui.components.VspScaffold

@Composable
fun CaptureScreen(
    inspectionId: String,
    section: Section,
    onBack: () -> Unit,
    onSectionComplete: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    LaunchedEffect(inspectionId, section) { viewModel.initialize(inspectionId, section) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSkip by remember { mutableStateOf(false) }
    var skipReason by remember { mutableStateOf("") }

    LaunchedEffect(state.sectionComplete) { if (state.sectionComplete) onSectionComplete() }

    val title = if (section == Section.EXTERIOR) "Exterior capture" else "Interior capture"

    VspScaffold(
        title = title,
        onBack = onBack,
        actions = {
            val current = state.current
            if (current != null) {
                TextButton(onClick = { showSkip = true }) { Text("Skip") }
            }
        },
    ) { padding ->
        val current = state.current
        if (state.positions.isEmpty() || current == null) {
            LoadingOverlay(message = "Preparing capture…")
            return@VspScaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = current.displayName + if (!current.mandatory) " (optional)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    StepProgress(
                        current = state.currentIndex + 1,
                        total = state.total,
                        label = title,
                    )
                    state.error?.let {
                        ErrorBanner(message = it, modifier = Modifier.padding(top = 8.dp)) { viewModel.clearError() }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                CameraCapture(
                    instruction = "",
                    onImageCaptured = viewModel::capture,
                )
                if (state.isBusy) LoadingOverlay(message = "Checking quality…")
            }
        }
    }

    if (showSkip) {
        AlertDialog(
            onDismissRequest = { showSkip = false },
            title = { Text("Skip ${state.current?.displayName}") },
            text = {
                OutlinedTextField(
                    value = skipReason,
                    onValueChange = { skipReason = it },
                    label = { Text("Reason for skipping") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = skipReason.isNotBlank(),
                    onClick = {
                        viewModel.skip(skipReason)
                        skipReason = ""
                        showSkip = false
                    },
                ) { Text("Skip position") }
            },
            dismissButton = { TextButton(onClick = { showSkip = false }) { Text("Cancel") } },
        )
    }
}
