package com.vsp.core.data.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.text.TextPaint
import com.vsp.core.data.BuildConfig
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.DamageType
import com.vsp.core.model.Inspection
import com.vsp.core.model.Inspector
import com.vsp.core.model.RepairRecommendation
import com.vsp.core.model.Section
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.catalog.Applicability
import com.vsp.core.model.catalog.ChecklistCatalog
import com.vsp.core.model.catalog.ChecklistResponseType
import com.vsp.core.model.catalog.ChecklistSection
import com.vsp.core.model.catalog.ChecklistStatus
import com.vsp.core.model.catalog.DocumentCatalog
import com.vsp.core.model.catalog.PositionCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Renders a polished, branded, multi-page inspection report (cover, contents, at-a-glance,
 * category ratings summary, per-category parameter tables with perfect/imperfect counts, a photo
 * gallery, and detailed damage evidence with AI/manual marks highlighted).
 */
@Singleton
class PdfReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Bundle of an image with its AI findings and manual annotations. */
    data class ImageBundle(
        val image: com.vsp.core.model.InspectionImage,
        val annotations: List<Annotation>,
        val findings: List<AIFinding>,
    )

    private data class SectionStat(
        val section: ChecklistSection,
        val perfect: Int,
        val imperfect: Int,
        val rating: Int?,
        val rows: List<ParamRow>,
    )

    private data class ParamRow(val label: String, val result: String, val verdict: Int)

    fun generate(
        inspection: Inspection,
        vehicle: Vehicle,
        inspector: Inspector,
        bundles: List<ImageBundle>,
        reportId: String,
        generatedAt: Long,
        checklist: List<ChecklistResponse> = emptyList(),
    ): File {
        val document = PdfDocument()
        val renderer = PageRenderer(document)

        val applies = if (vehicle.category == VehicleCategory.OLD) Applicability.OLD else Applicability.NEW
        val byItem = checklist.associateBy { it.itemId }
        val sections = ChecklistCatalog.forCategory(applies).filter { it.id != "final_assessment" }
        val stats = sections.map { computeStat(it, byItem) }.filter { it.rows.isNotEmpty() }
        val summary = categorySummary(byItem, stats)
        val overall = overallRating(summary, stats)
        val qualityChecks = sections.sumOf { it.allItems.size }

        // Split photos so each is embedded at most once: marked photos go to the damage-evidence
        // section (larger, with overlays); the rest populate the gallery. A global cap bounds size.
        val validPhotos = bundles
            .filter { it.image.localFilePath.isNotBlank() && File(it.image.localFilePath).exists() }
            .sortedWith(bundleComparator())
        val marked = validPhotos.filter { it.findings.isNotEmpty() || it.annotations.isNotEmpty() }
        val unmarked = validPhotos.filter { it.findings.isEmpty() && it.annotations.isEmpty() }
        val cap = if (BuildConfig.PDF_MAX_IMAGES <= 0) Int.MAX_VALUE else BuildConfig.PDF_MAX_IMAGES
        val damagePhotos = marked.take(cap)
        val galleryPhotos = unmarked.take((cap - damagePhotos.size).coerceAtLeast(0))

        drawCover(renderer, vehicle, generatedAt, qualityChecks)
        drawContents(renderer, stats)
        drawAtAGlance(renderer, inspection, vehicle, generatedAt, overall)
        drawSummary(renderer, summary)
        drawGallery(renderer, galleryPhotos)
        stats.forEachIndexed { index, stat -> drawSectionDetail(renderer, index + 1, stat) }
        drawDamageEvidence(renderer, damagePhotos)
        drawClosing(renderer, inspection, checklist)

        renderer.finish()

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "inspection-report-${inspection.id}.pdf")
        FileOutputStream(out).use { document.writeTo(it) }
        document.close()
        return out
    }

    // ---- Cover --------------------------------------------------------------

    private fun drawCover(r: PageRenderer, vehicle: Vehicle, generatedAt: Long, qualityChecks: Int) {
        r.startPage(header = false)
        r.brandBand()
        r.gap(60f)
        r.coverTitle(vehicleTitle(vehicle))
        val subtitle = listOfNotNull(
            vehicle.variant ?: vehicle.trim,
            vehicle.year?.toString(),
            vehicle.transmission,
            vehicle.fuelType,
        ).joinToString("  |  ")
        if (subtitle.isNotBlank()) r.coverSubtitle(subtitle)
        r.gap(10f)
        r.coverSubtitle("Report generated on: ${formatDate(generatedAt)}")
        r.gap(40f)
        r.coverHeadline("Comprehensive Car Inspection Report")
        r.gap(6f)
        r.coverTagline("Thorough inspection with $qualityChecks+ quality checks")
        r.coverTagline("Precise insights from experienced technicians")
        r.gap(30f)
        r.statBand(
            listOf(
                "$qualityChecks+" to "Quality checks",
                "AI + Manual" to "Damage detection",
                "Offline-first" to "Secure & private",
            ),
        )
    }

    // ---- Contents -----------------------------------------------------------

    private fun drawContents(r: PageRenderer, stats: List<SectionStat>) {
        r.startPage()
        r.title("Contents of report")
        r.gap(8f)
        r.tocEntry("01", "Your report at a glance", "Overview of your overall car condition")
        r.tocEntry("02", "Inspection summary", "Category-wise ratings and condition")
        r.tocEntry("03", "Detailed evaluation of each category", "")
        stats.forEach { r.tocSub(it.section.title) }
        r.tocEntry("04", "Photos & damage evidence", "Captured images with highlighted damage marks")
    }

    // ---- At a glance --------------------------------------------------------

    private fun drawAtAGlance(
        r: PageRenderer,
        inspection: Inspection,
        vehicle: Vehicle,
        generatedAt: Long,
        overall: Int?,
    ) {
        r.startPage()
        r.title("At a glance")
        r.gap(6f)
        r.heading(vehicleTitle(vehicle))
        val subtitle = listOfNotNull(
            vehicle.variant ?: vehicle.trim,
            vehicle.year?.toString(),
            vehicle.transmission,
            vehicle.fuelType,
        ).joinToString("  |  ")
        if (subtitle.isNotBlank()) r.body(subtitle)
        r.gap(14f)

        if (overall != null) r.overallRatingBlock(overall)
        r.gap(14f)

        r.heading("Details")
        r.keyValue("Inspection date", formatDate(generatedAt))
        vehicle.vin?.let { r.keyValue("VIN", it) }
        vehicle.chassisNumber?.let { r.keyValue("Chassis number", it) }
        vehicle.engineNumber?.let { r.keyValue("Engine number", it) }
        vehicle.registrationNumber?.let { r.keyValue("Registration", it) }
        vehicle.color?.let { r.keyValue("Color", it) }
        vehicle.odometerKm?.let { r.keyValue("Odometer", "$it km") }
        vehicle.numberOfOwnerships?.let { r.keyValue("Ownerships", it.toString()) }
        vehicle.numberOfKeys?.let { r.keyValue("Keys", it.toString()) }
        r.keyValue("Category", vehicle.category.name)
        inspection.finalRecommendation?.takeIf { it.isNotBlank() }?.let {
            r.keyValue("Recommendation", recommendationLabel(it))
        }
    }

    // ---- Category summary ---------------------------------------------------

    private fun drawSummary(r: PageRenderer, summary: List<Pair<String, Int>>) {
        if (summary.isEmpty()) return
        r.startPage()
        r.title("Inspection summary")
        r.gap(4f)
        r.body(
            "This section provides a comprehensive evaluation of the inspection, divided into key " +
                "categories to offer an in-depth and accurate overview.",
        )
        r.gap(10f)
        summary.forEach { (label, rating) ->
            r.summaryRow(label, conditionSentence(label, rating), rating, ratingWord(rating), ratingColor(rating))
        }
    }

    // ---- Photo gallery ------------------------------------------------------

    private fun drawGallery(r: PageRenderer, photos: List<ImageBundle>) {
        if (photos.isEmpty()) return
        r.startPage()
        r.title("Vehicle images")
        r.gap(6f)
        photos.chunked(2).forEach { pair ->
            val cells = pair.mapNotNull { b ->
                loadBitmap(b.image.localFilePath, BuildConfig.PDF_GALLERY_IMAGE_WIDTH)?.let { it to imageLabel(b.image) }
            }
            if (cells.isNotEmpty()) {
                r.galleryRow(cells)
                cells.forEach { it.first.recycle() }
            }
        }
    }

    // ---- Section detail (parameter table) -----------------------------------

    private fun drawSectionDetail(r: PageRenderer, number: Int, stat: SectionStat) {
        r.startPage()
        r.sectionHeader(String.format(Locale.US, "%02d", number), stat.section.title, stat.rating)
        r.countLine(stat.perfect, stat.imperfect)
        r.gap(4f)
        r.paramHeader()
        stat.rows.forEach { r.paramRow(it.label, it.result, it.verdict) }
    }

    // ---- Damage evidence (photos with marks) --------------------------------

    private fun drawDamageEvidence(r: PageRenderer, marked: List<ImageBundle>) {
        if (marked.isEmpty()) return
        marked.forEach { drawImageSection(r, it) }
    }

    private fun drawImageSection(r: PageRenderer, bundle: ImageBundle) {
        val image = bundle.image
        val bitmap = loadBitmap(image.localFilePath, BuildConfig.PDF_DAMAGE_IMAGE_WIDTH) ?: return

        r.startPage()
        r.heading(imageLabel(image))
        r.smallMuted("Green box = AI-detected damage   •   Blue pin = manual annotation")
        r.gap(6f)
        r.drawImageWithMarks(bitmap, bundle.findings, bundle.annotations)
        bitmap.recycle()
        r.gap(10f)

        if (bundle.findings.isNotEmpty()) {
            r.subheading("AI-detected damage (${bundle.findings.size})")
            bundle.findings.forEachIndexed { index, f -> r.findingLine(index + 1, f) }
            r.gap(4f)
        }
        if (bundle.annotations.isNotEmpty()) {
            r.subheading("Manual annotations (${bundle.annotations.size})")
            bundle.annotations.forEachIndexed { index, a -> r.annotationLine(index + 1, a) }
        }
    }

    // ---- Closing ------------------------------------------------------------

    private fun drawClosing(r: PageRenderer, inspection: Inspection, checklist: List<ChecklistResponse>) {
        val byItem = checklist.associateBy { it.itemId }
        val remarks = byItem["fa_remarks"]?.textValue
        val recommendation = byItem[ChecklistCatalog.RECOMMENDATION_ITEM_ID]?.textValue
            ?: inspection.finalRecommendation

        r.startPage()
        r.title("Thank you")
        r.gap(6f)
        r.body(
            "We truly appreciate your decision to prioritize the safety and maintenance of your vehicle. " +
                "By opting for a thorough inspection, you're ensuring not only the longevity of your car " +
                "but also peace of mind for you and your loved ones.",
        )
        r.gap(10f)
        recommendation?.takeIf { it.isNotBlank() }?.let {
            r.heading("Final recommendation")
            r.body(recommendationLabel(it))
            r.gap(8f)
        }
        remarks?.takeIf { it.isNotBlank() }?.let {
            r.heading("Inspector remarks")
            r.body(it)
            r.gap(8f)
        }
        r.gap(10f)
        r.smallMuted(
            "Disclaimer: This inspection is conducted to the best of our ability and knowledge at the time " +
                "of inspection. Findings reflect the vehicle's observable condition and do not constitute a warranty.",
        )
    }

    // ---- Stats computation --------------------------------------------------

    private fun computeStat(section: ChecklistSection, byItem: Map<String, ChecklistResponse>): SectionStat {
        var perfect = 0
        var imperfect = 0
        val rows = section.allItems.mapNotNull { item ->
            val response = byItem[item.id]?.takeIf { it.isAnswered } ?: return@mapNotNull null
            val v = verdict(response.status)
            when (v) {
                1 -> perfect++
                -1 -> imperfect++
            }
            ParamRow(item.label, resultText(response, item.unit), v)
        }
        return SectionStat(section, perfect, imperfect, deriveRating(perfect, imperfect), rows)
    }

    private fun categorySummary(
        byItem: Map<String, ChecklistResponse>,
        stats: List<SectionStat>,
    ): List<Pair<String, Int>> {
        val statById = stats.associateBy { it.section.id }
        val faToSections = mapOf(
            "fa_exterior" to listOf("exterior"),
            "fa_interior" to listOf("interior"),
            "fa_engine" to listOf("engine_bay", "mechanical"),
            "fa_electrical" to listOf("electrical"),
            "fa_tyres" to listOf("wheels"),
            "fa_suspension" to listOf("road_test"),
            "fa_safety" to listOf("safety"),
            "fa_documentation" to listOf("documents"),
        )
        val finalSection = ChecklistCatalog.section("final_assessment")
        val labels = finalSection?.allItems
            ?.filter { it.responseType == ChecklistResponseType.RATING_1_5 }
            ?.associate { it.id to it.label }
            .orEmpty()

        return faToSections.mapNotNull { (faId, sectionIds) ->
            val label = labels[faId] ?: return@mapNotNull null
            val explicit = byItem[faId]?.rating
            val derived = sectionIds.mapNotNull { statById[it]?.rating }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val rating = explicit ?: derived ?: return@mapNotNull null
            label to rating.coerceIn(1, 5)
        }
    }

    private fun overallRating(summary: List<Pair<String, Int>>, stats: List<SectionStat>): Int? {
        summary.map { it.second }.takeIf { it.isNotEmpty() }?.let { return it.average().roundToInt().coerceIn(1, 5) }
        val perfect = stats.sumOf { it.perfect }
        val imperfect = stats.sumOf { it.imperfect }
        return deriveRating(perfect, imperfect)
    }

    private fun deriveRating(perfect: Int, imperfect: Int): Int? {
        val total = perfect + imperfect
        if (total == 0) return null
        return (perfect.toDouble() / total * 5).roundToInt().coerceIn(1, 5)
    }

    /** 1 = perfect, -1 = imperfect, 0 = informational/neutral. */
    private fun verdict(status: ChecklistStatus?): Int = when (status) {
        ChecklistStatus.OK, ChecklistStatus.YES, ChecklistStatus.PASS, ChecklistStatus.GOOD -> 1
        ChecklistStatus.NOT_OK, ChecklistStatus.NO, ChecklistStatus.FAIL,
        ChecklistStatus.MINOR_SCRATCHES, ChecklistStatus.MAJOR_SCRATCHES, ChecklistStatus.DAMAGE,
        -> -1
        ChecklistStatus.NA, null -> 0
    }

    private fun resultText(response: ChecklistResponse, unit: String?): String {
        val parts = buildList {
            response.status?.let { add(prettyStatus(it.name)) }
            response.rating?.let { add("$it / 5") }
            response.numericValue?.let { value ->
                val num = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
                add(if (unit != null) "$num $unit" else num)
            }
            response.textValue?.takeIf { it.isNotBlank() }?.let { add(recommendationLabel(it)) }
            if (response.damageTypes.isNotEmpty()) add(response.damageTypes.joinToString(", ") { prettyType(it) })
        }
        return parts.joinToString(" • ").ifBlank { "—" }
    }

    // ---- Labels & formatting ------------------------------------------------

    private fun vehicleTitle(vehicle: Vehicle): String =
        listOfNotNull(vehicle.make, vehicle.model).joinToString(" ").ifBlank {
            vehicle.vin ?: vehicle.registrationNumber ?: "Vehicle Inspection"
        }.uppercase(Locale.getDefault())

    private fun ratingWord(rating: Int): String = when (rating) {
        5 -> "Excellent"
        4 -> "Very good"
        3 -> "Good"
        2 -> "Fair"
        else -> "Poor"
    }

    private fun ratingColor(rating: Int): Int = when {
        rating >= 4 -> Color.parseColor("#2E7D32")
        rating == 3 -> Color.parseColor("#F9A825")
        else -> Color.parseColor("#C62828")
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

    private fun recommendationLabel(value: String): String =
        RepairRecommendation.entries.firstOrNull { it.name == value }?.label ?: value

    private fun prettyStatus(name: String): String = when (name) {
        "OK" -> "OK"
        "NOT_OK" -> "Not OK"
        "NA" -> "N/A"
        else -> name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun prettyType(type: DamageType): String =
        type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun imageLabel(image: com.vsp.core.model.InspectionImage): String {
        image.checklistItemId?.let { itemId ->
            val item = ChecklistCatalog.item(itemId)
            val section = ChecklistCatalog.sectionForItem(itemId)
            if (item != null && section != null) {
                val group = section.groups.firstOrNull { g -> g.items.any { it.id == itemId } }
                val parts = listOfNotNull(
                    section.title,
                    group?.title?.takeIf { it != item.label && it != section.title },
                    item.label,
                )
                return parts.joinToString(" \u2192 ")
            }
        }
        val sectionName = when (image.section) {
            Section.EXTERIOR -> "Exterior"
            Section.INTERIOR -> "Interior"
            Section.DOCUMENT -> "Document"
        }
        val detail = when (image.section) {
            Section.DOCUMENT -> DocumentCatalog.oldVehicleDocuments.firstOrNull { it.type == image.documentType }?.displayName
                ?: image.documentType?.name?.replace('_', ' ')
            else -> PositionCatalog.forSection(image.section).firstOrNull { it.id == image.position }?.displayName
        } ?: image.position.substringBeforeLast('_').replace('_', ' ').ifBlank { image.position }
        return "$sectionName \u2192 $detail"
    }

    private fun bundleComparator(): Comparator<ImageBundle> {
        val exterior = PositionCatalog.exterior.associate { it.id to it.order }
        val interior = PositionCatalog.interior.associate { it.id to it.order }
        val docs = DocumentCatalog.oldVehicleDocuments.associate { it.type to it.order }
        fun itemRank(b: ImageBundle): Int =
            b.image.checklistItemId?.let { ChecklistCatalog.itemOrder[it] } ?: Int.MAX_VALUE
        fun sectionRank(s: Section) = when (s) {
            Section.DOCUMENT -> 0
            Section.EXTERIOR -> 1
            Section.INTERIOR -> 2
        }
        fun positionRank(b: ImageBundle): Int = when (b.image.section) {
            Section.DOCUMENT -> b.image.documentType?.let { docs[it] } ?: Int.MAX_VALUE
            Section.EXTERIOR -> exterior[b.image.position] ?: Int.MAX_VALUE
            Section.INTERIOR -> interior[b.image.position] ?: Int.MAX_VALUE
        }
        return compareBy(
            { itemRank(it) },
            { sectionRank(it.image.section) },
            { positionRank(it) },
            { it.image.capturedAt ?: Long.MAX_VALUE },
        )
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

    /**
     * Loads a photo sized for the PDF: downsampled near [targetWidth], EXIF-rotated, scaled to
     * exactly [targetWidth], then round-tripped through JPEG at [BuildConfig.PDF_IMAGE_QUALITY].
     * The JPEG pass lowers entropy so the PDF's internal image encoding stays small.
     */
    private fun loadBitmap(path: String, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        val rotated = applyExifRotation(path, decoded)
        val scaled = scaleToWidth(rotated, targetWidth)
        return compressToJpeg(scaled, BuildConfig.PDF_IMAGE_QUALITY)
    }

    private fun scaleToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val height = (targetWidth.toFloat() / bitmap.width * bitmap.height).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, height, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun compressToJpeg(bitmap: Bitmap, quality: Int): Bitmap {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bitmap
        if (result != bitmap) bitmap.recycle()
        return result
    }

    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /** Stateful page renderer that lays out content top-to-bottom, starting new A4 pages as needed. */
    private inner class PageRenderer(private val document: PdfDocument) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 40f
        private val contentWidth = pageWidth - margin * 2

        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = margin
        private var pageNumber = 0
        private var showHeader = true

        private val brand = Color.parseColor("#0D47A1")
        private val brandDark = Color.parseColor("#1B5E20")

        private val coverTitlePaint = TextPaint().apply { color = Color.WHITE; textSize = 30f; isFakeBoldText = true; isAntiAlias = true }
        private val coverSubPaint = TextPaint().apply { color = Color.WHITE; textSize = 13f; isAntiAlias = true }
        private val headlinePaint = TextPaint().apply { color = brand; textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        private val taglinePaint = TextPaint().apply { color = Color.parseColor("#444444"); textSize = 12f; isAntiAlias = true }
        private val statNumPaint = TextPaint().apply { color = brand; textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        private val statLabelPaint = TextPaint().apply { color = Color.parseColor("#666666"); textSize = 9.5f; isAntiAlias = true }

        private val titlePaint = TextPaint().apply { color = Color.BLACK; textSize = 22f; isFakeBoldText = true; isAntiAlias = true }
        private val headingPaint = TextPaint().apply { color = brandDark; textSize = 15f; isFakeBoldText = true; isAntiAlias = true }
        private val subheadingPaint = TextPaint().apply { color = Color.BLACK; textSize = 12.5f; isFakeBoldText = true; isAntiAlias = true }
        private val bodyPaint = TextPaint().apply { color = Color.parseColor("#222222"); textSize = 11f; isAntiAlias = true }
        private val boldPaint = TextPaint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        private val labelPaint = TextPaint().apply { color = Color.parseColor("#666666"); textSize = 11f; isAntiAlias = true }
        private val mutedPaint = TextPaint().apply { color = Color.parseColor("#888888"); textSize = 9.5f; isAntiAlias = true }
        private val headerPaint = TextPaint().apply { color = Color.parseColor("#AAAAAA"); textSize = 9f; isAntiAlias = true }

        private val bandPaint = Paint().apply { color = brand; style = Paint.Style.FILL; isAntiAlias = true }
        private val chipPaint = Paint().apply { color = Color.parseColor("#F1F5FB"); style = Paint.Style.FILL; isAntiAlias = true }
        private val borderPaint = Paint().apply { color = Color.parseColor("#DDDDDD"); style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
        private val rowLinePaint = Paint().apply { color = Color.parseColor("#EEEEEE"); style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }

        private val aiBoxStroke = Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
        private val aiBoxFill = Paint().apply { color = Color.parseColor("#332E7D32"); style = Paint.Style.FILL; isAntiAlias = true }
        private val aiLabelBg = Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL; isAntiAlias = true }
        private val aiLabelText = Paint().apply { color = Color.WHITE; textSize = 9f; isAntiAlias = true; isFakeBoldText = true }
        private val pinFill = Paint().apply { color = Color.parseColor("#1976D2"); style = Paint.Style.FILL; isAntiAlias = true }
        private val pinInner = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }

        fun startPage(header: Boolean = true) {
            finish()
            pageNumber += 1
            showHeader = header
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(info)
            canvas = page!!.canvas
            y = margin
            if (header) {
                canvas!!.drawText("Inspection report", margin, y, headerPaint)
                y += 14f
            }
        }

        fun finish() {
            page?.let {
                drawFooter(it.canvas)
                document.finishPage(it)
            }
            page = null
            canvas = null
        }

        private fun drawFooter(c: Canvas) {
            c.drawText("Vehicle Inspection Report", margin, pageHeight - 20f, mutedPaint)
            c.drawText("Page $pageNumber", pageWidth - margin - 46f, pageHeight - 20f, mutedPaint)
        }

        private fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin - 24f) startPage(showHeader)
        }

        fun gap(amount: Float = 12f) { y += amount }

        // -- Cover primitives --
        fun brandBand() {
            canvas!!.drawRect(0f, 0f, pageWidth.toFloat(), 150f, bandPaint)
            canvas!!.drawText("VEHICLE INSPECTION", margin, 60f, coverSubPaint)
        }

        fun coverTitle(text: String) {
            // title sits inside the brand band region
            canvas!!.drawText(ellipsize(text, contentWidth, coverTitlePaint), margin, 105f, coverTitlePaint)
        }

        fun coverSubtitle(text: String) {
            val paint = if (y < 150f) coverSubPaint else taglinePaint
            canvas!!.drawText(ellipsize(text, contentWidth, paint), margin, y + 12f, paint)
            y += 18f
        }

        fun coverHeadline(text: String) {
            ensureSpace(26f)
            canvas!!.drawText(text, margin, y + 20f, headlinePaint)
            y += 28f
        }

        fun coverTagline(text: String) {
            ensureSpace(18f)
            canvas!!.drawText(text, margin, y + 12f, taglinePaint)
            y += 18f
        }

        fun statBand(stats: List<Pair<String, String>>) {
            val gap = 12f
            val cellW = (contentWidth - gap * (stats.size - 1)) / stats.size
            val top = y
            val h = 56f
            stats.forEachIndexed { index, (num, label) ->
                val left = margin + index * (cellW + gap)
                canvas!!.drawRoundRect(RectF(left, top, left + cellW, top + h), 8f, 8f, chipPaint)
                canvas!!.drawText(num, left + centerOffset(num, cellW, statNumPaint), top + 26f, statNumPaint)
                canvas!!.drawText(label, left + centerOffset(label, cellW, statLabelPaint), top + 44f, statLabelPaint)
            }
            y = top + h
        }

        private fun centerOffset(text: String, w: Float, paint: TextPaint): Float =
            (w - paint.measureText(text)).coerceAtLeast(0f) / 2f

        // -- Contents primitives --
        fun tocEntry(number: String, title: String, subtitle: String) {
            ensureSpace(30f)
            canvas!!.drawText(number, margin, y + 14f, headingPaint)
            canvas!!.drawText(title, margin + 34f, y + 14f, boldPaint)
            y += 18f
            if (subtitle.isNotBlank()) {
                canvas!!.drawText(subtitle, margin + 34f, y + 10f, labelPaint)
                y += 16f
            }
            y += 4f
        }

        fun tocSub(title: String) {
            ensureSpace(16f)
            canvas!!.drawText("•  $title", margin + 44f, y + 11f, bodyPaint)
            y += 16f
        }

        // -- Text primitives --
        fun title(text: String) {
            ensureSpace(30f)
            canvas!!.drawText(text, margin, y + 22f, titlePaint)
            y += 32f
        }

        fun heading(text: String) {
            ensureSpace(24f)
            y += 6f
            canvas!!.drawText(text, margin, y + 15f, headingPaint)
            y += 22f
        }

        fun subheading(text: String) {
            ensureSpace(20f)
            canvas!!.drawText(text, margin, y + 13f, subheadingPaint)
            y += 18f
        }

        fun keyValue(key: String, value: String) {
            ensureSpace(16f)
            canvas!!.drawText("$key:", margin, y + 12f, labelPaint)
            drawWrapped(value, margin + 150f, contentWidth - 150f, bodyPaint, firstLineBaseline = y + 12f)
        }

        fun body(text: String) {
            drawWrapped(text, margin, contentWidth, bodyPaint, firstLineBaseline = y + 12f)
        }

        fun smallMuted(text: String) {
            drawWrapped(text, margin, contentWidth, mutedPaint, firstLineBaseline = y + 10f)
        }

        // -- Summary primitives --
        fun overallRatingBlock(rating: Int) {
            ensureSpace(56f)
            val h = 50f
            val top = y
            canvas!!.drawRoundRect(RectF(margin, top, margin + contentWidth, top + h), 8f, 8f, chipPaint)
            canvas!!.drawText("Overall rating", margin + 16f, top + 22f, labelPaint)
            val badge = "$rating/5  ${ratingWord(rating)}"
            val paint = TextPaint(titlePaint).apply { color = ratingColor(rating); textSize = 20f }
            canvas!!.drawText(badge, margin + 16f, top + 42f, paint)
            y = top + h
        }

        fun summaryRow(label: String, sentence: String, rating: Int, word: String, color: Int) {
            ensureSpace(44f)
            val top = y
            canvas!!.drawText(label, margin, top + 13f, boldPaint)
            val badge = "$rating/5 $word"
            val badgePaint = TextPaint(boldPaint).apply { this.color = color }
            canvas!!.drawText(badge, margin + contentWidth - badgePaint.measureText(badge), top + 13f, badgePaint)
            y += 18f
            drawWrapped(sentence, margin, contentWidth, labelPaint, firstLineBaseline = y + 10f)
            y += 6f
            canvas!!.drawLine(margin, y, margin + contentWidth, y, rowLinePaint)
            y += 8f
        }

        // -- Section detail primitives --
        fun sectionHeader(number: String, title: String, rating: Int?) {
            ensureSpace(30f)
            y += 4f
            canvas!!.drawText("$number. $title", margin, y + 18f, titlePaint)
            if (rating != null) {
                val badge = "$rating/5 ${ratingWord(rating)}"
                val bp = TextPaint(boldPaint).apply { color = ratingColor(rating) }
                canvas!!.drawText(badge, margin + contentWidth - bp.measureText(badge), y + 16f, bp)
            }
            y += 26f
        }

        fun countLine(perfect: Int, imperfect: Int) {
            ensureSpace(16f)
            val greenTxt = "Perfect: $perfect"
            val redTxt = "   |   Imperfect: $imperfect"
            val gp = TextPaint(boldPaint).apply { color = Color.parseColor("#2E7D32") }
            val rp = TextPaint(boldPaint).apply { color = Color.parseColor("#C62828") }
            canvas!!.drawText(greenTxt, margin, y + 12f, gp)
            canvas!!.drawText(redTxt, margin + gp.measureText(greenTxt), y + 12f, rp)
            y += 18f
        }

        fun paramHeader() {
            ensureSpace(18f)
            canvas!!.drawText("Parameter", margin, y + 12f, subheadingPaint)
            val res = "Result"
            canvas!!.drawText(res, margin + contentWidth - subheadingPaint.measureText(res), y + 12f, subheadingPaint)
            y += 16f
            canvas!!.drawLine(margin, y - 2f, margin + contentWidth, y - 2f, borderPaint)
            y += 2f
        }

        fun paramRow(label: String, result: String, verdict: Int) {
            ensureSpace(18f)
            val resultColor = when (verdict) {
                1 -> Color.parseColor("#2E7D32")
                -1 -> Color.parseColor("#C62828")
                else -> Color.parseColor("#555555")
            }
            val rp = TextPaint(bodyPaint).apply { color = resultColor; isFakeBoldText = verdict != 0 }
            val resultWidth = rp.measureText(result)
            val labelText = ellipsize(label, contentWidth - resultWidth - 16f, bodyPaint)
            canvas!!.drawText(labelText, margin, y + 12f, bodyPaint)
            canvas!!.drawText(result, margin + contentWidth - resultWidth, y + 12f, rp)
            y += 17f
            canvas!!.drawLine(margin, y - 3f, margin + contentWidth, y - 3f, rowLinePaint)
        }

        // -- Gallery --
        fun galleryRow(cells: List<Pair<Bitmap, String>>) {
            val gap = 12f
            val cellW = (contentWidth - gap) / 2f
            val cellH = cellW * 0.72f
            ensureSpace(cellH + 24f)
            val top = y
            cells.forEachIndexed { index, (bmp, caption) ->
                val left = margin + index * (cellW + gap)
                canvas!!.drawRect(left, top, left + cellW, top + cellH, borderPaint)
                val dst = fitRect(bmp, left, top, cellW, cellH)
                canvas!!.drawBitmap(bmp, null, dst, null)
                canvas!!.drawText(ellipsize(caption, cellW, mutedPaint), left, top + cellH + 13f, labelPaint)
            }
            y = top + cellH + 24f
        }

        private fun fitRect(bmp: Bitmap, left: Float, top: Float, w: Float, h: Float): RectF {
            var drawW = w
            var drawH = drawW * bmp.height / bmp.width
            if (drawH > h) {
                drawH = h
                drawW = drawH * bmp.width / bmp.height
            }
            val ox = left + (w - drawW) / 2f
            val oy = top + (h - drawH) / 2f
            return RectF(ox, oy, ox + drawW, oy + drawH)
        }

        // -- Damage detail --
        fun findingLine(index: Int, f: AIFinding) {
            val confidence = (f.confidence * 100).toInt()
            val review = if (f.reviewRequired) " (review)" else ""
            val header = "$index. ${prettyType(f.damageType)} • ${f.severity.name} • $confidence% confidence$review"
            val recommendation = f.repairRecommendation.takeIf { it.isNotBlank() }?.let { "   Repair: $it" } ?: ""
            drawWrapped(header + recommendation, margin + 8f, contentWidth - 8f, bodyPaint, firstLineBaseline = y + 12f)
            y += 4f
        }

        fun annotationLine(index: Int, a: Annotation) {
            val cost = a.estimatedCost?.let { "   Est. cost: ${it.toInt()}" } ?: ""
            val repair = when (a.repairRequired) {
                true -> "   Repair: Required"
                false -> "   Repair: No"
                null -> ""
            }
            val comment = a.comment?.takeIf { it.isNotBlank() }?.let { "   Note: $it" } ?: ""
            drawWrapped(
                "$index. ${prettyType(a.damageType)} • ${a.severity.name}$repair$cost$comment",
                margin + 8f, contentWidth - 8f, bodyPaint, firstLineBaseline = y + 12f,
            )
            y += 4f
        }

        fun drawImageWithMarks(bitmap: Bitmap, findings: List<AIFinding>, annotations: List<Annotation>): RectF {
            val maxImageHeight = 420f
            var drawW = contentWidth
            var drawH = drawW * bitmap.height / bitmap.width
            if (drawH > maxImageHeight) {
                drawH = maxImageHeight
                drawW = drawH * bitmap.width / bitmap.height
            }
            ensureSpace(drawH + 8f)
            val left = margin + (contentWidth - drawW) / 2f
            val top = y
            val dst = RectF(left, top, left + drawW, top + drawH)
            canvas!!.drawBitmap(bitmap, null, dst, null)

            findings.forEach { f ->
                val box = f.boundingBox
                val bl = left + box.x * drawW
                val bt = top + box.y * drawH
                val br = bl + box.w * drawW
                val bb = bt + box.h * drawH
                canvas!!.drawRect(bl, bt, br, bb, aiBoxFill)
                canvas!!.drawRect(bl, bt, br, bb, aiBoxStroke)
                val label = prettyType(f.damageType)
                val tw = aiLabelText.measureText(label) + 8f
                canvas!!.drawRect(bl, (bt - 12f).coerceAtLeast(top), bl + tw, bt, aiLabelBg)
                canvas!!.drawText(label, bl + 4f, (bt - 3f).coerceAtLeast(top + 9f), aiLabelText)
            }
            annotations.forEach { a ->
                val point = runCatching {
                    val obj = JSONObject(a.geometryJson)
                    Pair(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat())
                }.getOrNull() ?: return@forEach
                val cx = left + point.first * drawW
                val cy = top + point.second * drawH
                canvas!!.drawCircle(cx, cy, 7f, pinFill)
                canvas!!.drawCircle(cx, cy, 3f, pinInner)
            }
            y = top + drawH
            return dst
        }

        private fun ellipsize(text: String, maxWidth: Float, paint: TextPaint): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end).trimEnd() + "…"
        }

        private fun drawWrapped(text: String, x: Float, maxWidth: Float, paint: TextPaint, firstLineBaseline: Float) {
            val lineHeight = paint.textSize + 4f
            var start = 0
            var baseline = firstLineBaseline
            var firstLine = true
            while (start < text.length) {
                val count = paint.breakText(text, start, text.length, true, maxWidth, null)
                if (count <= 0) break
                var end = start + count
                if (end < text.length) {
                    val lastSpace = text.lastIndexOf(' ', end - 1)
                    if (lastSpace > start) end = lastSpace + 1
                }
                if (!firstLine) {
                    ensureSpace(lineHeight)
                    baseline = y + paint.textSize
                }
                canvas!!.drawText(text.substring(start, end).trimEnd(), x, baseline, paint)
                y = baseline - paint.textSize + lineHeight
                start = end
                firstLine = false
            }
            if (firstLine) y = firstLineBaseline - paint.textSize + lineHeight
        }
    }
}
