package com.vsp.inspection.feature.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold

@Composable
fun ChecklistHubScreen(
    onBack: () -> Unit,
    onOpenSection: (sectionId: String) -> Unit,
    onContinue: () -> Unit,
    viewModel: ChecklistHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VspScaffold(
        title = "Inspection Checklist",
        onBack = onBack,
        bottomBar = {
            PrimaryButton(
                text = "Continue to review",
                onClick = onContinue,
                modifier = Modifier.padding(16.dp),
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingOverlay()
            return@VspScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.sections, key = { it.section.id }) { row ->
                SectionCard(row = row, onClick = { onOpenSection(row.section.id) })
            }
        }
    }
}

@Composable
private fun SectionCard(row: SectionRow, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.section.title, style = MaterialTheme.typography.titleMedium)
                if (row.complete) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Section complete",
                        tint = Color(0xFF2E7D32),
                    )
                } else {
                    Text(
                        "${row.answered}/${row.total}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val fraction = if (row.total > 0) row.answered.toFloat() / row.total else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}
