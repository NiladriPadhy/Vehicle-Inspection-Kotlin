package com.vsp.inspection.feature.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vsp.core.model.CaptureState
import com.vsp.core.model.InspectionImage
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenChecklist: () -> Unit,
    onFinalize: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VspScaffold(
        title = "Review",
        onBack = onBack,
        bottomBar = {
            val capturedCount = state.images.count { it.captureState == CaptureState.CAPTURED }
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(
                    onClick = onOpenChecklist,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open inspection checklist")
                }
                PrimaryButton(
                    text = if (capturedCount > 0) "Run final verification" else "Capture at least one photo",
                    onClick = onFinalize,
                    enabled = capturedCount > 0,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingOverlay()
            return@VspScaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val capturedCount = state.images.count { it.captureState == CaptureState.CAPTURED }
            Text(
                text = "$capturedCount photo(s) captured",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            ) {
                items(state.images.filter { it.captureState == CaptureState.CAPTURED }, key = { it.id }) { image ->
                    ImageThumb(image = image, onClick = { onOpenImage(image.id) })
                }
            }
        }
    }
}

@Composable
private fun ImageThumb(image: InspectionImage, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${image.section.name} ${image.position}" },
    ) {
        AsyncImage(
            model = image.localFilePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        )
    }
}
