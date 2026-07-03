package com.vsp.inspection.feature.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.model.FinalVerification
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold

@Composable
fun VerificationScreen(
    onBack: () -> Unit,
    onFinalized: () -> Unit,
    viewModel: VerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finalized) { if (state.finalized) onFinalized() }

    VspScaffold(title = "Final verification", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("AI integrity & scoring", style = MaterialTheme.typography.headlineSmall)
            state.message?.let { ErrorBanner(message = it) { viewModel.consumeMessage() } }

            state.verification?.let { ScoreCard(it) }

            OutlinedButton(
                onClick = viewModel::runVerification,
                enabled = !state.running,
            ) { Text(if (state.running) "Running AI verification…" else "Run AI final verification") }

            PrimaryButton(
                text = if (state.finalizing) "Finalizing…" else "Finalize & generate report",
                onClick = viewModel::finalize,
                enabled = !state.finalizing,
            )
        }
    }
}

@Composable
private fun ScoreCard(verification: FinalVerification) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Overall: ${verification.overallCondition}", style = MaterialTheme.typography.titleMedium)
            Text("Exterior: ${verification.scores.exterior}")
            Text("Interior: ${verification.scores.interior}")
            Text("Safety: ${verification.scores.safety}")
            Text("Cosmetic: ${verification.scores.cosmetic}")
            Text("Confidence: ${verification.scores.confidence}")
            if (verification.summary.isNotBlank()) Text(verification.summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
