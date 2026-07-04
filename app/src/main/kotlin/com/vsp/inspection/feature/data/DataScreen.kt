package com.vsp.inspection.feature.data

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.ui.components.ErrorBanner
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold
import java.io.File

@Composable
fun DataScreen(
    onBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Share the exported zip as soon as it is produced.
    LaunchedEffect(state.exportedZipPath) {
        val path = state.exportedZipPath ?: return@LaunchedEffect
        runCatching {
            val file = File(path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Share inspection export"))
        }
        viewModel.consumeExportPath()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Copy the picked bundle into the cache so the repository can work with a plain file path.
        val dest = File(context.cacheDir, "imports").apply { mkdirs() }.let { File(it, "import_${System.currentTimeMillis()}.zip") }
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.exists()
        }.getOrDefault(false)
        if (ok) viewModel.import(dest.absolutePath)
    }

    VspScaffold(title = "Backup & transfer", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { ErrorBanner(message = it) }
            state.message?.let {
                Card(modifier = Modifier.fillMaxSize()) {
                    Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Export", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bundle every inspection (photos + data) into a shareable .zip file for backup or moving to another device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = if (state.busy) "Working…" else "Export all inspections",
                onClick = viewModel::export,
                enabled = !state.busy,
            )

            Text("Import", style = MaterialTheme.typography.titleMedium)
            Text(
                "Restore inspections from a previously exported .zip. Import is blocked if the questionnaire no longer matches the current configuration.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = "Import from .zip",
                onClick = { importLauncher.launch("application/zip") },
                enabled = !state.busy,
            )
        }
    }
}
