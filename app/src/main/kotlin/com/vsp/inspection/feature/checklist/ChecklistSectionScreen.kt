package com.vsp.inspection.feature.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.RepairRecommendation
import com.vsp.core.model.catalog.ChecklistCatalog
import com.vsp.core.model.catalog.ChecklistItem
import com.vsp.core.model.catalog.ChecklistResponseType
import com.vsp.core.model.catalog.ChecklistStatus
import com.vsp.core.ui.components.EmptyState
import com.vsp.core.ui.components.LoadingOverlay
import com.vsp.core.ui.components.VspScaffold
import com.vsp.inspection.BuildConfig

@Composable
fun ChecklistSectionScreen(
    onBack: () -> Unit,
    onCaptureItem: (itemId: String) -> Unit,
    onOpenImage: (imageId: String, itemId: String) -> Unit,
    viewModel: ChecklistSectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VspScaffold(title = state.section?.title ?: "Checklist", onBack = onBack) { padding ->
        when {
            state.loading -> LoadingOverlay()
            state.section == null -> EmptyState("Section unavailable", "This section does not apply to the vehicle.")
            else -> {
                val section = state.section!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    section.groups.forEach { group ->
                        item(key = "group_${group.id}") {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            )
                        }
                        group.items.forEach { checklistItem ->
                            item(key = checklistItem.id) {
                                ItemCard(
                                    item = checklistItem,
                                    sectionId = section.id,
                                    response = state.responses[checklistItem.id],
                                    images = state.imagesByItem[checklistItem.id].orEmpty(),
                                    maxImages = state.maxImagesByItem[checklistItem.id]
                                        ?: BuildConfig.MAX_IMAGES_PER_ITEM,
                                    viewModel = viewModel,
                                    onCaptureItem = onCaptureItem,
                                    onOpenImage = onOpenImage,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: ChecklistItem,
    sectionId: String,
    response: ChecklistResponse?,
    images: List<InspectionImage>,
    maxImages: Int,
    viewModel: ChecklistSectionViewModel,
    onCaptureItem: (String) -> Unit,
    onOpenImage: (String, String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge)
            when (item.responseType) {
                ChecklistResponseType.STATUS_OK -> StatusRow(
                    options = listOf(ChecklistStatus.OK, ChecklistStatus.NOT_OK, ChecklistStatus.NA),
                    labels = listOf("OK", "Not OK", "N/A"),
                    selected = response?.status,
                    onSelect = { viewModel.setStatus(item.id, it) },
                )
                ChecklistResponseType.YES_NO -> StatusRow(
                    options = listOf(ChecklistStatus.YES, ChecklistStatus.NO),
                    labels = listOf("Yes", "No"),
                    selected = response?.status,
                    onSelect = { viewModel.setStatus(item.id, it) },
                )
                ChecklistResponseType.PASS_FAIL -> StatusRow(
                    options = listOf(ChecklistStatus.PASS, ChecklistStatus.FAIL),
                    labels = listOf("Pass", "Fail"),
                    selected = response?.status,
                    onSelect = { viewModel.setStatus(item.id, it) },
                )
                ChecklistResponseType.RATING_1_5 -> RatingRow(
                    selected = response?.rating,
                    onSelect = { viewModel.setRating(item.id, it) },
                )
                ChecklistResponseType.NUMBER -> NumberField(
                    value = response?.numericValue,
                    unit = item.unit,
                    onChange = { viewModel.setNumber(item.id, it) },
                )
                ChecklistResponseType.TEXT -> if (item.id == ChecklistCatalog.RECOMMENDATION_ITEM_ID) {
                    RecommendationRow(
                        selected = response?.textValue,
                        onSelect = { viewModel.setText(item.id, it.name) },
                    )
                } else {
                    TextFieldRow(
                        value = response?.textValue.orEmpty(),
                        onChange = { viewModel.setText(item.id, it) },
                    )
                }
                ChecklistResponseType.COMPONENT -> StatusRow(
                    options = conditionGrades(sectionId).map { it.first },
                    labels = conditionGrades(sectionId).map { it.second },
                    selected = response?.status,
                    onSelect = { viewModel.setStatus(item.id, it) },
                )
            }
            if (item.photoCapable) {
                ImageGrid(
                    images = images,
                    max = maxImages,
                    onCapture = { onCaptureItem(item.id) },
                    onOpenImage = { imageId -> onOpenImage(imageId, item.id) },
                    onDeleteImage = { imageId -> viewModel.deleteImage(imageId) },
                )
            }
        }
    }
}

/** Condition grades for a component, varying by inspection area (exterior vs interior wear). */
private fun conditionGrades(sectionId: String): List<Pair<ChecklistStatus, String>> {
    val damageWord = if (sectionId == "interior") "wear" else "scratches"
    return listOf(
        ChecklistStatus.GOOD to "Good",
        ChecklistStatus.MINOR_SCRATCHES to "Minor $damageWord",
        ChecklistStatus.MAJOR_SCRATCHES to "Major $damageWord",
        ChecklistStatus.DAMAGE to "Damage",
        ChecklistStatus.NA to "N/A",
    )
}

@Composable
private fun ImageGrid(
    images: List<InspectionImage>,
    max: Int,
    onCapture: () -> Unit,
    onOpenImage: (String) -> Unit,
    onDeleteImage: (String) -> Unit,
) {
    val canAdd = images.size < max
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Photos ${images.size}/$max",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            images.forEach { image ->
                Box {
                    AsyncImage(
                        model = image.localFilePath,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenImage(image.id) },
                    )
                    Surface(
                        onClick = { pendingDelete = image.id },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(22.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Delete photo",
                            modifier = Modifier.padding(3.dp),
                        )
                    }
                }
            }
            if (canAdd) {
                AddPhotoTile(onClick = onCapture)
            }
        }
        if (!canAdd) {
            Text(
                "Maximum photos reached.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingDelete?.let { imageId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete photo?") },
            text = { Text("This photo and its marks will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteImage(imageId)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(80.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo")
            Text("Add", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusRow(
    options: List<ChecklistStatus>,
    labels: List<String>,
    selected: ChecklistStatus?,
    onSelect: (ChecklistStatus) -> Unit,
) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            Chip(text = labels[index], selected = selected == option, onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun RatingRow(selected: Int?, onSelect: (Int) -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { value ->
            Chip(text = value.toString(), selected = selected == value, onClick = { onSelect(value) })
        }
    }
}

@Composable
private fun NumberField(value: Double?, unit: String?, onChange: (Double?) -> Unit) {
    OutlinedTextField(
        value = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "",
        onValueChange = { text -> onChange(text.trim().toDoubleOrNull()) },
        label = { Text(unit?.let { "Value ($it)" } ?: "Value") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecommendationRow(selected: String?, onSelect: (RepairRecommendation) -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RepairRecommendation.entries.forEach { rec ->
            Chip(text = rec.label, selected = selected == rec.name, onClick = { onSelect(rec) })
        }
    }
}

@Composable
private fun TextFieldRow(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Remarks") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
