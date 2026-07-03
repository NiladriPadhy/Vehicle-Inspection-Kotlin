package com.vsp.inspection.feature.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.vsp.core.model.InspectionContext
import com.vsp.core.model.VehicleCategory
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold
import com.vsp.core.ui.components.VspTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartInspectionScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: StartInspectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdInspectionId) {
        state.createdInspectionId?.let(onCreated)
    }

    VspScaffold(title = "Start inspection", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { ErrorBanner(message = it) }

            Text("Inspection context", style = MaterialTheme.typography.labelLarge)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = state.context.name.replace('_', ' '),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Context") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    InspectionContext.entries.forEach { ctx ->
                        DropdownMenuItem(
                            text = { Text(ctx.name.replace('_', ' ')) },
                            onClick = {
                                viewModel.onContextChange(ctx)
                                expanded = false
                            },
                        )
                    }
                }
            }

            Text("Vehicle category", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VehicleCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text(if (category == VehicleCategory.NEW) "New" else "Old (used)") },
                    )
                }
            }

            VspTextField(
                value = state.vin,
                onValueChange = viewModel::onVinChange,
                label = "VIN (17 characters)",
                keyboardType = KeyboardType.Text,
            )

            if (state.registrationRequired) {
                VspTextField(
                    value = state.registrationNumber,
                    onValueChange = viewModel::onRegistrationChange,
                    label = "Registration number (required for Old)",
                )
            }

            PrimaryButton(
                text = if (state.isSubmitting) "Creating…" else "Continue",
                onClick = viewModel::submit,
                enabled = !state.isSubmitting && state.canSubmit,
            )
        }
    }
}
