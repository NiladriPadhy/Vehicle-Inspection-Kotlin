package com.vsp.inspection.feature.imagedetail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.DamageType
import com.vsp.core.model.Severity
import com.vsp.core.ui.components.PrimaryButton
import com.vsp.core.ui.components.VspScaffold
import com.vsp.inspection.feature.common.typeLabel
import org.json.JSONObject

@Composable
fun ImageDetailScreen(
    onBack: () -> Unit,
    viewModel: ImageDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()

    var pendingPin by remember { mutableStateOf<Offset?>(null) }

    val pagerState = rememberPagerState(pageCount = { images.size })

    // Jump to the deep-linked image once the sibling list is available.
    var didInitialScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(images) {
        if (!didInitialScroll && images.isNotEmpty()) {
            val index = images.indexOfFirst { it.id == viewModel.imageId }.coerceAtLeast(0)
            pagerState.scrollToPage(index)
            didInitialScroll = true
        }
    }
    // Sync the ViewModel's current image with the settled swipe page.
    LaunchedEffect(pagerState.settledPage, images) {
        images.getOrNull(pagerState.settledPage)?.let { viewModel.onImageSelected(it.id) }
    }

    VspScaffold(title = "Image detail", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.image?.let { image ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = image.typeLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (images.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${images.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (images.isEmpty()) {
                ImageCanvas(
                    imagePath = state.image?.localFilePath,
                    contentDescription = "Inspection image ${state.image?.position.orEmpty()}",
                    findings = state.findings,
                    annotations = state.annotations,
                    onTapNorm = { pendingPin = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 360.dp),
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 360.dp),
                ) { page ->
                    val pageImage = images[page]
                    val isCurrent = page == pagerState.currentPage
                    ImageCanvas(
                        imagePath = pageImage.localFilePath,
                        contentDescription = "Inspection image ${pageImage.position}",
                        findings = if (isCurrent) state.findings else emptyList(),
                        annotations = if (isCurrent) state.annotations else emptyList(),
                        onTapNorm = { if (isCurrent) pendingPin = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            events.message?.let { msg ->
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(msg, modifier = Modifier.padding(end = 8.dp))
                        TextButton(onClick = viewModel::consumeMessage) { Text("OK") }
                    }
                }
            }

            PrimaryButton(
                text = if (events.analyzing) "Analyzing…" else "Analyze with AI",
                onClick = viewModel::analyze,
                enabled = !events.analyzing,
                modifier = Modifier.padding(12.dp),
            )

            Text(
                "Annotations (${state.annotations.size}) • AI findings (${state.findings.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            var editingAssessment by remember { mutableStateOf<Annotation?>(null) }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(state.annotations, key = { it.id }) { annotation ->
                    AnnotationRow(
                        annotation = annotation,
                        reverifying = events.reverifying,
                        onReverify = { viewModel.reverify(annotation) },
                        onDelete = { viewModel.delete(annotation) },
                        onEditAssessment = { editingAssessment = annotation },
                    )
                }
            }

            editingAssessment?.let { target ->
                DamageAssessmentDialog(
                    annotation = target,
                    onDismiss = { editingAssessment = null },
                    onConfirm = { repairRequired, cost, size, verified ->
                        viewModel.updateAssessment(target, repairRequired, cost, size, verified)
                        editingAssessment = null
                    },
                )
            }
        }
    }

    pendingPin?.let { pin ->
        AddAnnotationDialog(
            onDismiss = { pendingPin = null },
            onConfirm = { type, severity, comment ->
                viewModel.addPin(pin.x, pin.y, type, severity, comment)
                pendingPin = null
            },
        )
    }
}

@Composable
private fun ImageCanvas(
    imagePath: String?,
    contentDescription: String,
    findings: List<AIFinding>,
    annotations: List<Annotation>,
    onTapNorm: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pointerInput(imagePath) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    onTapNorm(Offset(offset.x / w, offset.y / h))
                }
            }
            .drawWithContent {
                drawContent()
                findings.forEach { drawFinding(it) }
                annotations.forEach { drawPin(it) }
            },
    ) {
        AsyncImage(
            model = imagePath,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AnnotationRow(
    annotation: Annotation,
    reverifying: Boolean,
    onReverify: () -> Unit,
    onDelete: () -> Unit,
    onEditAssessment: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${annotation.damageType.name} • ${annotation.severity.name}", style = MaterialTheme.typography.titleSmall)
            annotation.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val assessment = buildList {
                annotation.repairRequired?.let { add(if (it) "Repair required" else "No repair") }
                annotation.estimatedCost?.let { add("Cost: ${it.toInt()}") }
                annotation.estimatedSize?.takeIf { it.isNotBlank() }?.let { add("Size: $it") }
                if (annotation.manualVerified) add("Verified")
            }
            if (assessment.isNotEmpty()) {
                Text(
                    assessment.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReverify, enabled = !reverifying) { Text("Re-verify with AI") }
                TextButton(onClick = onEditAssessment) { Text("Assessment") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun DamageAssessmentDialog(
    annotation: Annotation,
    onDismiss: () -> Unit,
    onConfirm: (repairRequired: Boolean?, cost: Double?, size: String?, verified: Boolean) -> Unit,
) {
    var repairRequired by remember { mutableStateOf(annotation.repairRequired) }
    var cost by remember { mutableStateOf(annotation.estimatedCost?.let { it.toInt().toString() } ?: "") }
    var size by remember { mutableStateOf(annotation.estimatedSize.orEmpty()) }
    var verified by remember { mutableStateOf(annotation.manualVerified) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Damage assessment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Repair required?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterToggle("Yes", repairRequired == true) { repairRequired = true }
                    FilterToggle("No", repairRequired == false) { repairRequired = false }
                }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { text -> cost = text.filter(Char::isDigit) },
                    label = { Text("Estimated cost") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Estimated size (e.g. 5cm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Manually verified")
                    Switch(checked = verified, onCheckedChange = { verified = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(repairRequired, cost.toDoubleOrNull(), size.ifBlank { null }, verified)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FilterToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = if (selected) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) { Text(text) }
}

private val AiGreen = Color(0xFF2E7D32)
private val ManualBlue = Color(0xFF1976D2)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFinding(finding: AIFinding) {
    val box = finding.boundingBox
    val left = box.x * size.width
    val top = box.y * size.height
    val w = box.w * size.width
    val h = box.h * size.height

    // Enlarged green marking area for the detected region.
    drawRect(
        color = AiGreen.copy(alpha = 0.18f),
        topLeft = Offset(left, top),
        size = Size(w, h),
    )
    drawRect(
        color = AiGreen,
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = 6f),
    )

    // AI-assisted point rendered like a manual pin, at the region center.
    val center = Offset(left + w / 2f, top + h / 2f)
    drawMarker(center, AiGreen)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPin(annotation: Annotation) {
    val point = runCatching {
        val obj = JSONObject(annotation.geometryJson)
        Offset(obj.getDouble("x").toFloat() * size.width, obj.getDouble("y").toFloat() * size.height)
    }.getOrNull() ?: return
    drawMarker(point, ManualBlue)
}

/** Shared, enlarged pin marker used for both AI findings and manual annotations. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarker(center: Offset, color: Color) {
    drawCircle(color = color.copy(alpha = 0.25f), radius = 40f, center = center)
    drawCircle(color = color, radius = 26f, center = center)
    drawCircle(color = Color.White, radius = 11f, center = center)
}
