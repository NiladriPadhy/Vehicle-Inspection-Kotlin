package com.vsp.inspection.feature.checklist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.VspScaffold
import com.vsp.inspection.feature.capture.CameraCapture

/**
 * Free-form, continuous capture of photos that belong to a single checklist item/section. The
 * user keeps capturing until they press back; a confirmation then lets them keep or discard the
 * photos taken during this visit.
 */
@Composable
fun SectionCaptureScreen(
    onExit: () -> Unit,
    viewModel: SectionCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) { if (state.done) onExit() }

    BackHandler { viewModel.onBackRequested() }

    VspScaffold(title = "Add photos", onBack = viewModel::onBackRequested) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            CameraCapture(
                instruction = if (state.limitReached) {
                    "Limit reached (${state.max}). Press back to finish."
                } else {
                    "Frame the area and capture. Keep capturing, then press back when done."
                },
                onImageCaptured = viewModel::capture,
            )

            CaptureCountBanner(
                total = state.totalCount,
                max = state.max,
                remaining = state.remaining,
                sessionCount = state.sessionCount,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (state.isBusy) LoadingOverlay(message = "Saving photo…")
            state.error?.let { ErrorBanner(message = it, onRetry = viewModel::clearError) }
        }
    }

    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscardDialog,
            title = { Text("Keep captured photos?") },
            text = {
                Text(
                    "You captured ${state.sessionCount} photo(s) in this session. " +
                        "Keep them or discard before leaving?",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::keepAndExit) { Text("Keep") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardAndExit) { Text("Discard") }
            },
        )
    }
}

@Composable
private fun CaptureCountBanner(
    total: Int,
    max: Int,
    remaining: Int,
    sessionCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BannerStat(label = "Total", value = "$total / $max")
            BannerStat(label = "Remaining", value = "$remaining")
            BannerStat(label = "This session", value = "$sessionCount")
        }
    }
}

@Composable
private fun BannerStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}
