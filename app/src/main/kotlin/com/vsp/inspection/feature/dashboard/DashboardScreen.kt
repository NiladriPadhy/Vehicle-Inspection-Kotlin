package com.vsp.inspection.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.model.Inspection
import com.vsp.core.ui.components.EmptyState
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.VspScaffold

@Composable
fun DashboardScreen(
    onNewInspection: () -> Unit,
    onResume: (Inspection) -> Unit,
    onOpenChecklist: (Inspection) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    VspScaffold(
        title = "Inspections",
        actions = {
            IconButton(onClick = viewModel::signOut) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New inspection") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onNewInspection,
                modifier = Modifier.semantics { contentDescription = "Start a new inspection" },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingOverlay()
            state.inspections.isEmpty() -> EmptyState(
                title = "No inspections yet",
                subtitle = "Tap the button below to start a new vehicle inspection.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.inspections, key = { it.id }) { inspection ->
                    InspectionCard(
                        inspection = inspection,
                        onClick = { onResume(inspection) },
                        onOpenChecklist = { onOpenChecklist(inspection) },
                        onDelete = { viewModel.delete(inspection.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectionCard(
    inspection: Inspection,
    onClick: () -> Unit,
    onOpenChecklist: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Inspection ${inspection.id}, status ${inspection.status.name}" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${inspection.context.name.replace('_', ' ')} • ${inspection.vehicleCategory.name}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Status: ${inspection.status.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Step: ${inspection.currentStep}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onOpenChecklist) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Open checklist")
            }
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete inspection")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete inspection?") },
            text = { Text("This permanently removes the inspection, its photos, and checklist data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}
