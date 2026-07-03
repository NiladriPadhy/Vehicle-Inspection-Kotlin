package com.vsp.inspection.feature.identify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold
import com.vsp.core.ui.components.VspTextField
import com.vsp.inspection.feature.capture.CameraCapture

@Composable
fun IdentifyScreen(
    onBack: () -> Unit,
    onContinue: (com.vsp.core.model.VehicleCategory) -> Unit,
    viewModel: IdentifyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onContinue(state.category) }

    if (scanning) {
        VspScaffold(title = "Scan VIN", onBack = { scanning = false }) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CameraCapture(
                    instruction = "Frame the VIN plate clearly",
                    onImageCaptured = {
                        viewModel.onVinScanned(it)
                        scanning = false
                    },
                )
            }
        }
        return
    }

    VspScaffold(title = "Identify vehicle", onBack = onBack) { padding ->
        if (!state.loaded) {
            LoadingOverlay()
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
            state.error?.let { ErrorBanner(message = it) }
            Text("Vehicle details", style = MaterialTheme.typography.headlineSmall)

            VspTextField(state.vin, viewModel::onVinChange, "VIN")
            OutlinedButton(onClick = { scanning = true }, enabled = !state.scanning) {
                Text(if (state.scanning) "Scanning…" else "Scan VIN with camera")
            }

            VspTextField(state.make, viewModel::onMakeChange, "Make / manufacturer")
            VspTextField(state.model, viewModel::onModelChange, "Model")
            VspTextField(state.year, viewModel::onYearChange, "Year", keyboardType = KeyboardType.Number)
            VspTextField(state.color, viewModel::onColorChange, "Color")
            if (state.category == com.vsp.core.model.VehicleCategory.OLD) {
                VspTextField(
                    state.odometer,
                    viewModel::onOdometerChange,
                    "Odometer (km)",
                    keyboardType = KeyboardType.Number,
                )
            }

            PrimaryButton(
                text = if (state.saving) "Saving…" else "Continue",
                onClick = viewModel::save,
                enabled = !state.saving,
            )
        }
    }
}
