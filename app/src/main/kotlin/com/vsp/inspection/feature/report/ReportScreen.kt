package com.vsp.inspection.feature.report

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold
import java.io.File

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val sync by viewModel.syncStatus.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.pdfPath) {
        val path = state.pdfPath ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Vehicle Inspection Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF report"))
        viewModel.consumePdfPath()
    }

    VspScaffold(
        title = "Report",
        onBack = onBack,
        bottomBar = {
            PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.padding(16.dp))
        },
    ) { padding ->
        if (state.generating && report == null) {
            LoadingOverlay(message = "Generating report…")
            return@VspScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { ErrorBanner(message = it) { viewModel.consumeMessage() } }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sync status", style = MaterialTheme.typography.titleMedium)
                    Text("Synced: ${sync.synced} • Pending: ${sync.pending} • Failed: ${sync.failed}")
                    if (sync.failed > 0) {
                        OutlinedButton(onClick = viewModel::retrySync) { Text("Retry failed uploads") }
                    }
                }
            }

            report?.let { r ->
                PrimaryButton(
                    text = if (state.exportingPdf) "Preparing PDF…" else "Export PDF report",
                    onClick = viewModel::exportPdf,
                    enabled = !state.exportingPdf,
                )

                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, r.json)
                        putExtra(Intent.EXTRA_SUBJECT, "Vehicle Inspection Report")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share report"))
                }) { Text("Share report JSON") }

                Text("Report preview", style = MaterialTheme.typography.titleMedium)
                Text(r.json, style = MaterialTheme.typography.bodySmall)
            } ?: OutlinedButton(onClick = viewModel::generate) { Text("Generate report") }
        }
    }
}
