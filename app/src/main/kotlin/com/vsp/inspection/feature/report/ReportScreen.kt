package com.vsp.inspection.feature.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vsp.core.model.Valuation
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
    val content by viewModel.content.collectAsStateWithLifecycle()
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.message?.let { ErrorBanner(message = it) { viewModel.consumeMessage() } }

            content?.let { c ->
                val brandColor = parseHexColor(c.branding.primaryColor, MaterialTheme.colorScheme.primary)
                VehicleDetailsCard(c, brandColor)
                AtAGlanceCard(c, brandColor)
                c.valuation?.let { ValuationCard(it, brandColor) }
                InspectionSummaryCard(c, brandColor)

                PrimaryButton(
                    text = if (state.exportingPdf) "Preparing PDF…" else "Generate Comprehensive Report in PDF",
                    onClick = viewModel::exportPdf,
                    enabled = !state.exportingPdf,
                )
                PrimaryButton(
                    text = "Generate Comprehensive Report in JSON",
                    onClick = {
                        val json = report?.json ?: return@PrimaryButton
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "Vehicle Inspection Report")
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Json Report"))
                    },
                )
            } ?: PrimaryButton(text = "Generate report", onClick = viewModel::generate)
        }
    }
}

@Composable
private fun SectionCard(title: String, titleColor: Color, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = titleColor)
            content()
        }
    }
}

@Composable
private fun VehicleDetailsCard(content: ReportContent, brandColor: Color) {
    SectionCard(title = "Inspected Vehicle Details", titleColor = brandColor) {
        Text(content.vehicleTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (content.subtitle.isNotBlank()) {
            Text(
                content.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content.vehicleDetails.forEach { DetailRowView(it) }
    }
}

@Composable
private fun AtAGlanceCard(content: ReportContent, brandColor: Color) {
    SectionCard(title = "At a glance", titleColor = brandColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(content.vehicleTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content.recommendation?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content.overallRating?.let { RatingGauge(it) }
        }
        content.glanceDetails.forEach { DetailRowView(it) }
    }
}

@Composable
private fun ValuationCard(v: Valuation, brandColor: Color) {
    val scoreColor = when {
        v.overallScore >= 70 -> Color(0xFF2E7D32)
        v.overallScore >= 50 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }
    val positionColor = when (v.marketPosition) {
        "Above typical" -> Color(0xFF2E7D32)
        "Below typical" -> Color(0xFFC62828)
        else -> Color(0xFFF9A825)
    }
    SectionCard(title = "Valuation & market position", titleColor = brandColor) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${v.overallScore}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scoreColor,
            )
            Column {
                Text("/ 100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = scoreColor.copy(alpha = 0.12f), shape = CircleShape) {
                    Text(
                        "${v.conditionBand} condition",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { v.overallScore / 100f },
            color = scoreColor,
            trackColor = scoreColor.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape),
        )
        val deltaText = if (v.deltaVsTypical >= 0) "+${v.deltaVsTypical}" else "${v.deltaVsTypical}"
        Text(
            "This vehicle: ${v.overallScore}/100   •   Typical: ${v.benchmarkScore}/100 ($deltaText vs typical)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Market position", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Surface(color = positionColor.copy(alpha = 0.12f), shape = CircleShape) {
                Text(
                    v.marketPosition,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = positionColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(v.verdict, style = MaterialTheme.typography.bodyMedium)
        Text(
            v.priceGuidance,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InspectionSummaryCard(content: ReportContent, brandColor: Color) {
    SectionCard(title = "Inspection summary", titleColor = brandColor) {
        if (content.categoryRatings.isEmpty()) {
            Text(
                "No category ratings recorded for this inspection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        content.categoryRatings.forEach { CategoryRatingRow(it) }
    }
}

@Composable
private fun CategoryRatingRow(item: CategoryRating) {
    val color = ratingColor(item.rating)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            RatingBadge(item.rating)
        }
        LinearProgressIndicator(
            progress = { item.rating / 5f },
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape),
        )
        Text(
            conditionSentence(item.label, item.rating),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RatingBadge(rating: Int) {
    val color = ratingColor(rating)
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Text(
            "$rating/5  ${ratingWord(rating)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RatingGauge(rating: Int) {
    val color = ratingColor(rating)
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$rating/5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                Text(ratingWord(rating), style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun DetailRowView(row: DetailRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun ratingColor(rating: Int): Color = when {
    rating >= 4 -> Color(0xFF2E7D32)
    rating == 3 -> Color(0xFFF9A825)
    else -> Color(0xFFC62828)
}

private fun parseHexColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

private fun ratingWord(rating: Int): String = when (rating) {
    5 -> "Excellent"
    4 -> "Very good"
    3 -> "Good"
    2 -> "Fair"
    else -> "Poor"
}

private fun conditionSentence(label: String, rating: Int): String {
    val phrase = when (rating) {
        5, 4 -> "great condition"
        3 -> "good condition"
        2 -> "fair condition"
        else -> "poor condition and needs attention"
    }
    return "The $label is in $phrase compared to other similar cars."
}
