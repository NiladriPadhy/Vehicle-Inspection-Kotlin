package com.vsp.core.data.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import com.vsp.core.data.BuildConfig
import com.vsp.core.data.remote.rtdb.FirebaseConfig
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.DamageType
import com.vsp.core.model.Inspection
import com.vsp.core.model.Inspector
import com.vsp.core.model.InspectionImage
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Builds a branded, template-driven HTML document for the inspection report. The HTML is rendered
 * to PDF offline by [WebViewPdfPrinter]; CSS handles the visual design (gradients, cards, gauges,
 * zebra tables, status pills) that a hand-drawn Canvas cannot express cheaply. Images are embedded
 * as base64 JPEG data URIs, bounded by the same size controls as the legacy generator.
 */
@Singleton
class HtmlReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseConfig: FirebaseConfig,
) {

    /** Vendor identity (from the per-vendor build config) shown on the report as the company name. */
    private val companyName: String?
        get() = firebaseConfig.vendorId
            .takeIf { it.isNotBlank() && !it.equals("default", ignoreCase = true) }
            ?.split('-', '_', ' ')
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    data class ImageBundle(
        val image: InspectionImage,
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

    fun buildHtml(
        inspection: Inspection,
        vehicle: Vehicle,
        inspector: Inspector,
        bundles: List<ImageBundle>,
        generatedAt: Long,
        checklist: List<ChecklistResponse> = emptyList(),
    ): String {
        val applies = if (vehicle.category == VehicleCategory.OLD) Applicability.OLD else Applicability.NEW
        val byItem = checklist.associateBy { it.itemId }
        val sections = ChecklistCatalog.forCategory(applies).filter { it.id != "final_assessment" }
        val stats = sections.map { computeStat(it, byItem) }.filter { it.rows.isNotEmpty() }
        val summary = categorySummary(byItem, stats)
        val overall = overallRating(summary, stats)
        val qualityChecks = sections.sumOf { it.allItems.size }

        val validPhotos = bundles
            .filter { it.image.localFilePath.isNotBlank() && File(it.image.localFilePath).exists() }
            .sortedWith(bundleComparator())
        val marked = validPhotos.filter { it.findings.isNotEmpty() || it.annotations.isNotEmpty() }
        val unmarked = validPhotos.filter { it.findings.isEmpty() && it.annotations.isEmpty() }
        val cap = if (BuildConfig.PDF_MAX_IMAGES <= 0) Int.MAX_VALUE else BuildConfig.PDF_MAX_IMAGES
        val damagePhotos = marked.take(cap)
        val galleryPhotos = unmarked.take((cap - damagePhotos.size).coerceAtLeast(0))

        return buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<style>").append(css()).append("</style></head><body>")
            append(coverPage(vehicle, generatedAt, qualityChecks))
            append(contentsPage(stats))
            append(atAGlancePage(inspection, vehicle, generatedAt, overall))
            append(summaryPage(summary))
            append(galleryPage(galleryPhotos))
            stats.forEachIndexed { i, stat -> append(sectionPage(i + 1, stat)) }
            append(damagePages(damagePhotos))
            append(closingPage(inspection, checklist, inspector))
            append("</body></html>")
        }
    }

    // ---- Pages --------------------------------------------------------------

    private fun coverPage(vehicle: Vehicle, generatedAt: Long, qualityChecks: Int): String {
        val subtitle = listOfNotNull(
            vehicle.variant ?: vehicle.trim,
            vehicle.year?.toString(),
            vehicle.transmission,
            vehicle.fuelType,
        ).joinToString("  |  ")
        return """
            <section class="page cover">
              <div class="cover-top">
                <div class="brand-eyebrow">VEHICLE INSPECTION</div>
                ${companyName?.let { "<div class=\"cover-company\">${esc(it)}</div>" } ?: ""}
                <h1 class="cover-title">${esc(vehicleTitle(vehicle))}</h1>
                ${if (subtitle.isNotBlank()) "<div class=\"cover-sub\">${esc(subtitle)}</div>" else ""}
                <div class="cover-date">Report generated on: ${esc(formatDate(generatedAt))}</div>
              </div>
              <div class="cover-hero">
                <h2>Comprehensive Car Inspection Report</h2>
                <p>Thorough inspection with $qualityChecks+ quality checks</p>
                <p>Precise insights backed by AI-assisted damage detection</p>
              </div>
              <div class="stat-band">
                ${statChip("$qualityChecks+", "Quality checks")}
                ${statChip("AI + Manual", "Damage detection")}
                ${statChip("Offline-first", "Secure &amp; private")}
              </div>
            </section>
        """.trimIndent()
    }

    private fun statChip(value: String, label: String) =
        """<div class="stat-chip"><div class="stat-num">${esc(value)}</div><div class="stat-label">$label</div></div>"""

    private fun contentsPage(stats: List<SectionStat>): String = buildString {
        append("<section class=\"page\">")
        append(pageHeader())
        append("<h2 class=\"title\">Contents of report</h2>")
        append(tocEntry("01", "Your report at a glance", "Overview of your overall car condition"))
        append(tocEntry("02", "Inspection summary", "Category-wise ratings and condition"))
        append(tocEntry("03", "Detailed evaluation of each category", ""))
        append("<ul class=\"toc-sub\">")
        stats.forEach { append("<li>${esc(it.section.title)}</li>") }
        append("</ul>")
        append(tocEntry("04", "Photos &amp; damage evidence", "Captured images with highlighted damage marks"))
        append("</section>")
    }

    private fun tocEntry(num: String, title: String, sub: String) =
        """<div class="toc-entry"><span class="toc-num">$num</span><div><div class="toc-title">$title</div>${if (sub.isNotBlank()) "<div class=\"toc-desc\">$sub</div>" else ""}</div></div>"""

    private fun atAGlancePage(
        inspection: Inspection,
        vehicle: Vehicle,
        generatedAt: Long,
        overall: Int?,
    ): String {
        val subtitle = listOfNotNull(
            vehicle.variant ?: vehicle.trim,
            vehicle.year?.toString(),
            vehicle.transmission,
            vehicle.fuelType,
        ).joinToString("  |  ")
        val details = buildString {
            companyName?.let { append(kv("Company Name", it)) }
            append(kv("Inspection date", formatDate(generatedAt)))
            vehicle.vin?.let { append(kv("VIN", it)) }
            vehicle.chassisNumber?.let { append(kv("Chassis number", it)) }
            vehicle.engineNumber?.let { append(kv("Engine number", it)) }
            vehicle.registrationNumber?.let { append(kv("Registration", it)) }
            vehicle.color?.let { append(kv("Color", it)) }
            vehicle.odometerKm?.let { append(kv("Odometer", "$it km")) }
            vehicle.numberOfOwnerships?.let { append(kv("Ownerships", it.toString())) }
            vehicle.numberOfKeys?.let { append(kv("Keys", it.toString())) }
            append(kv("Category", vehicle.category.name))
            inspection.finalRecommendation?.takeIf { it.isNotBlank() }?.let {
                append(kv("Recommendation", recommendationLabel(it)))
            }
        }
        return """
            <section class="page">
              ${pageHeader()}
              <h2 class="title">At a glance</h2>
              <div class="glance">
                <div class="glance-info">
                  <h3 class="veh-name">${esc(vehicleTitle(vehicle))}</h3>
                  ${if (subtitle.isNotBlank()) "<div class=\"muted\">${esc(subtitle)}</div>" else ""}
                </div>
                ${if (overall != null) gauge(overall) else ""}
              </div>
              <h3 class="section-h">Details</h3>
              <div class="kv-grid">$details</div>
            </section>
        """.trimIndent()
    }

    private fun gauge(rating: Int): String {
        val pct = (rating / 5.0 * 100).roundToInt()
        val color = ratingColor(rating)
        // conic-gradient ring with a white inner disc
        return """
            <div class="gauge" style="background: conic-gradient($color ${pct}%, #E8ECF3 ${pct}% 100%);">
              <div class="gauge-inner">
                <div class="gauge-score" style="color:$color;">$rating<span>/5</span></div>
                <div class="gauge-word" style="color:$color;">${ratingWord(rating)}</div>
              </div>
            </div>
        """.trimIndent()
    }

    private fun kv(k: String, v: String) =
        """<div class="kv"><span class="kv-k">${esc(k)}</span><span class="kv-v">${esc(v)}</span></div>"""

    private fun summaryPage(summary: List<Pair<String, Int>>): String {
        if (summary.isEmpty()) return ""
        return buildString {
            append("<section class=\"page\">")
            append(pageHeader())
            append("<h2 class=\"title\">Inspection summary</h2>")
            append("<p class=\"muted\">This section provides a comprehensive evaluation of the inspection, divided into key categories to offer an in-depth and accurate overview.</p>")
            summary.forEach { (label, rating) ->
                val color = ratingColor(rating)
                val pct = (rating / 5.0 * 100).roundToInt()
                append(
                    """
                    <div class="cat-card">
                      <div class="cat-row">
                        <div class="cat-name">${esc(label)}</div>
                        <div class="badge" style="background:${color}1A;color:$color;">$rating/5 ${ratingWord(rating)}</div>
                      </div>
                      <div class="bar"><div class="bar-fill" style="width:$pct%;background:$color;"></div></div>
                      <div class="cat-desc">${esc(conditionSentence(label, rating))}</div>
                    </div>
                    """.trimIndent(),
                )
            }
            append("</section>")
        }
    }

    private fun galleryPage(photos: List<ImageBundle>): String {
        if (photos.isEmpty()) return ""
        return buildString {
            append("<section class=\"page\">")
            append(pageHeader())
            append("<h2 class=\"title\">Vehicle images</h2>")
            append("<div class=\"gallery\">")
            photos.forEach { b ->
                val uri = imageDataUri(b.image.localFilePath, BuildConfig.PDF_GALLERY_IMAGE_WIDTH) ?: return@forEach
                append(
                    """<figure class="shot"><img src="$uri"/><figcaption>${esc(imageLabel(b.image))}</figcaption></figure>""",
                )
            }
            append("</div></section>")
        }
    }

    private fun sectionPage(number: Int, stat: SectionStat): String = buildString {
        append("<section class=\"page\">")
        append(pageHeader())
        val ratingBadge = stat.rating?.let {
            val c = ratingColor(it)
            """<span class="badge" style="background:${c}1A;color:$c;">$it/5 ${ratingWord(it)}</span>"""
        } ?: ""
        append(
            """<div class="sec-head"><h2 class="title">${String.format(Locale.US, "%02d", number)}. ${esc(stat.section.title)}</h2>$ratingBadge</div>""",
        )
        append(
            """<div class="count-line"><span class="ok">Perfect: ${stat.perfect}</span><span class="bad">Imperfect: ${stat.imperfect}</span></div>""",
        )
        append("<table class=\"params\"><thead><tr><th>Parameter</th><th class=\"res\">Result</th></tr></thead><tbody>")
        stat.rows.forEach { row ->
            val cls = when (row.verdict) {
                1 -> "res-ok"
                -1 -> "res-bad"
                else -> "res-neutral"
            }
            val icon = when (row.verdict) {
                1 -> "<span class=\"pill ok\">&#10003;</span>"
                -1 -> "<span class=\"pill bad\">&#10007;</span>"
                else -> ""
            }
            append("<tr><td>${esc(row.label)}</td><td class=\"res $cls\">$icon ${esc(row.result)}</td></tr>")
        }
        append("</tbody></table></section>")
    }

    private fun damagePages(marked: List<ImageBundle>): String = buildString {
        marked.forEach { bundle ->
            val uri = imageDataUri(bundle.image.localFilePath, BuildConfig.PDF_DAMAGE_IMAGE_WIDTH)
                ?: return@forEach
            append("<section class=\"page\">")
            append(pageHeader())
            append("<h3 class=\"section-h\">${esc(imageLabel(bundle.image))}</h3>")
            append("<div class=\"legend\">Green box = AI-detected damage &nbsp;&bull;&nbsp; Blue pin = manual annotation</div>")
            append("<div class=\"photo\"><img src=\"$uri\"/>")
            bundle.findings.forEach { f ->
                val b = f.boundingBox
                val l = (b.x * 100)
                val t = (b.y * 100)
                val w = (b.w * 100)
                val h = (b.h * 100)
                append(
                    """<div class="ai-box" style="left:$l%;top:$t%;width:$w%;height:$h%;"><span class="ai-tag">${esc(prettyType(f.damageType))}</span></div>""",
                )
            }
            bundle.annotations.forEach { a ->
                val p = runCatching {
                    val o = JSONObject(a.geometryJson)
                    o.getDouble("x") to o.getDouble("y")
                }.getOrNull() ?: return@forEach
                append("""<div class="pin" style="left:${p.first * 100}%;top:${p.second * 100}%;"></div>""")
            }
            append("</div>")
            if (bundle.findings.isNotEmpty()) {
                append("<h4 class=\"sub-h\">AI-detected damage (${bundle.findings.size})</h4><ul class=\"finding-list\">")
                bundle.findings.forEach { f ->
                    val conf = (f.confidence * 100).toInt()
                    val review = if (f.reviewRequired) " (review)" else ""
                    val rec = f.repairRecommendation.takeIf { it.isNotBlank() }?.let { " &mdash; Repair: ${esc(it)}" } ?: ""
                    append("<li>${esc(prettyType(f.damageType))} &bull; ${esc(f.severity.name)} &bull; $conf% confidence$review$rec</li>")
                }
                append("</ul>")
            }
            if (bundle.annotations.isNotEmpty()) {
                append("<h4 class=\"sub-h\">Manual annotations (${bundle.annotations.size})</h4><ul class=\"finding-list\">")
                bundle.annotations.forEach { a ->
                    val cost = a.estimatedCost?.let { " &bull; Est. cost: ${it.toInt()}" } ?: ""
                    val repair = when (a.repairRequired) {
                        true -> " &bull; Repair: Required"
                        false -> " &bull; Repair: No"
                        null -> ""
                    }
                    val note = a.comment?.takeIf { it.isNotBlank() }?.let { " &bull; Note: ${esc(it)}" } ?: ""
                    append("<li>${esc(prettyType(a.damageType))} &bull; ${esc(a.severity.name)}$repair$cost$note</li>")
                }
                append("</ul>")
            }
            append("</section>")
        }
    }

    private fun closingPage(inspection: Inspection, checklist: List<ChecklistResponse>, inspector: Inspector): String {
        val byItem = checklist.associateBy { it.itemId }
        val remarks = byItem["fa_remarks"]?.textValue
        val recommendation = byItem[ChecklistCatalog.RECOMMENDATION_ITEM_ID]?.textValue
            ?: inspection.finalRecommendation
        return buildString {
            append("<section class=\"page\">")
            append(pageHeader())
            append("<h2 class=\"title\">Thank you</h2>")
            append("<p class=\"muted\">We truly appreciate your decision to prioritize the safety and maintenance of your vehicle. By opting for a thorough inspection, you're ensuring not only the longevity of your car but also peace of mind for you and your loved ones.</p>")
            recommendation?.takeIf { it.isNotBlank() }?.let {
                append("<div class=\"note-card\"><h3 class=\"section-h\">Final recommendation</h3><p>${esc(recommendationLabel(it))}</p></div>")
            }
            remarks?.takeIf { it.isNotBlank() }?.let {
                append("<div class=\"note-card\"><h3 class=\"section-h\">Inspector remarks</h3><p>${esc(it)}</p></div>")
            }
            inspector.displayName.takeIf { it.isNotBlank() }?.let {
                append("<p class=\"muted\">Inspected by ${esc(it)}</p>")
            }
            append("<p class=\"disclaimer\">Disclaimer: This inspection is conducted to the best of our ability and knowledge at the time of inspection. Findings reflect the vehicle's observable condition and do not constitute a warranty.</p>")
            append("</section>")
        }
    }

    private fun pageHeader() = """<div class="pg-head">Inspection report</div>"""

    // ---- CSS ----------------------------------------------------------------

    private fun css(): String = """
        * { box-sizing: border-box; margin: 0; padding: 0; }
        @page { size: A4; margin: 0; }
        html, body { font-family: 'Helvetica Neue', Arial, sans-serif; color: #1f2933; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
        .page { position: relative; width: 210mm; min-height: 297mm; padding: 18mm 16mm 16mm; page-break-after: always; }
        .page:last-child { page-break-after: auto; }
        .pg-head { position: absolute; top: 8mm; left: 16mm; font-size: 9px; letter-spacing: .12em; color: #9aa5b1; text-transform: uppercase; }
        .title { font-size: 24px; font-weight: 800; color: #102a43; margin-bottom: 12px; }
        .section-h { font-size: 15px; font-weight: 700; color: #0b6e2e; margin: 14px 0 8px; }
        .sub-h { font-size: 13px; font-weight: 700; color: #243b53; margin: 12px 0 6px; }
        .muted { color: #627d98; font-size: 12px; line-height: 1.55; }
        .disclaimer { color: #9aa5b1; font-size: 10px; margin-top: 20px; line-height: 1.5; }

        /* Cover */
        .cover { padding: 0; color: #fff; background: linear-gradient(150deg, #0a2a66 0%, #0d47a1 55%, #1565c0 100%); }
        .cover-top { padding: 40mm 16mm 0; }
        .brand-eyebrow { font-size: 11px; letter-spacing: .28em; opacity: .8; }
        .cover-company { font-size: 22px; font-weight: 800; margin-top: 6px; letter-spacing: .01em; }
        .cover-title { font-size: 40px; font-weight: 800; margin-top: 10px; line-height: 1.05; }
        .cover-sub { font-size: 15px; opacity: .9; margin-top: 10px; }
        .cover-date { font-size: 12px; opacity: .75; margin-top: 8px; }
        .cover-hero { padding: 30mm 16mm 0; }
        .cover-hero h2 { font-size: 26px; font-weight: 800; }
        .cover-hero p { font-size: 13px; opacity: .9; margin-top: 6px; }
        .stat-band { display: flex; gap: 10px; padding: 22mm 16mm 0; }
        .stat-chip { flex: 1; background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.25); border-radius: 12px; padding: 16px; text-align: center; }
        .stat-num { font-size: 20px; font-weight: 800; }
        .stat-label { font-size: 10px; opacity: .85; margin-top: 4px; }

        /* TOC */
        .toc-entry { display: flex; gap: 14px; align-items: baseline; padding: 12px 0; border-bottom: 1px solid #eef2f7; }
        .toc-num { font-size: 18px; font-weight: 800; color: #0d47a1; }
        .toc-title { font-weight: 700; font-size: 14px; }
        .toc-desc { color: #627d98; font-size: 11px; margin-top: 3px; }
        .toc-sub { list-style: none; margin: 6px 0 6px 34px; }
        .toc-sub li { font-size: 12px; color: #334e68; padding: 4px 0 4px 16px; position: relative; }
        .toc-sub li:before { content: '\2022'; color: #0d47a1; position: absolute; left: 0; }

        /* At a glance */
        .glance { display: flex; align-items: center; justify-content: space-between; gap: 16px; background: #f7f9fc; border: 1px solid #e6ecf3; border-radius: 14px; padding: 20px; margin: 8px 0 4px; }
        .veh-name { font-size: 20px; font-weight: 800; color: #102a43; }
        .gauge { width: 118px; height: 118px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex: 0 0 auto; }
        .gauge-inner { width: 88px; height: 88px; background: #fff; border-radius: 50%; display: flex; flex-direction: column; align-items: center; justify-content: center; box-shadow: inset 0 0 0 1px #eef2f7; }
        .gauge-score { font-size: 26px; font-weight: 800; line-height: 1; }
        .gauge-score span { font-size: 13px; font-weight: 600; }
        .gauge-word { font-size: 11px; font-weight: 700; margin-top: 3px; }
        .kv-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 24px; margin-top: 6px; }
        .kv { display: flex; justify-content: space-between; border-bottom: 1px dashed #e6ecf3; padding: 7px 0; font-size: 12px; }
        .kv-k { color: #627d98; }
        .kv-v { font-weight: 600; color: #243b53; text-align: right; }

        /* Summary cards */
        .cat-card { border: 1px solid #e6ecf3; border-radius: 12px; padding: 14px 16px; margin-top: 12px; box-shadow: 0 1px 2px rgba(16,42,67,.04); }
        .cat-row { display: flex; justify-content: space-between; align-items: center; }
        .cat-name { font-weight: 700; font-size: 14px; color: #102a43; }
        .badge { font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 20px; }
        .bar { height: 7px; background: #eef2f7; border-radius: 20px; margin: 10px 0 8px; overflow: hidden; }
        .bar-fill { height: 100%; border-radius: 20px; }
        .cat-desc { color: #627d98; font-size: 11.5px; line-height: 1.5; }

        /* Gallery */
        .gallery { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
        .shot { border: 1px solid #e6ecf3; border-radius: 10px; overflow: hidden; background: #fff; break-inside: avoid; }
        .shot img { width: 100%; height: 150px; object-fit: cover; display: block; }
        .shot figcaption { font-size: 10.5px; color: #486581; padding: 7px 9px; border-top: 1px solid #eef2f7; }

        /* Section tables */
        .sec-head { display: flex; align-items: center; justify-content: space-between; }
        .count-line { display: flex; gap: 18px; font-size: 12px; font-weight: 700; margin: 4px 0 10px; }
        .count-line .ok { color: #0b6e2e; }
        .count-line .bad { color: #b91c1c; }
        table.params { width: 100%; border-collapse: collapse; font-size: 12px; }
        table.params thead th { text-align: left; font-size: 10px; letter-spacing: .08em; text-transform: uppercase; color: #829ab1; border-bottom: 2px solid #d9e2ec; padding: 8px 6px; }
        table.params th.res, table.params td.res { text-align: right; }
        table.params tbody td { padding: 8px 6px; border-bottom: 1px solid #eef2f7; }
        table.params tbody tr:nth-child(even) { background: #f9fbfd; }
        td.res-ok { color: #0b6e2e; font-weight: 600; }
        td.res-bad { color: #b91c1c; font-weight: 600; }
        td.res-neutral { color: #52606d; }
        .pill { display: inline-block; width: 15px; height: 15px; line-height: 15px; text-align: center; border-radius: 50%; font-size: 9px; color: #fff; margin-right: 4px; vertical-align: middle; }
        .pill.ok { background: #12805c; }
        .pill.bad { background: #d64545; }

        /* Damage evidence */
        .legend { font-size: 11px; color: #627d98; margin-bottom: 8px; }
        .photo { position: relative; display: inline-block; width: 100%; border-radius: 10px; overflow: hidden; border: 1px solid #e6ecf3; }
        .photo img { width: 100%; display: block; }
        .ai-box { position: absolute; border: 2px solid #2e7d32; background: rgba(46,125,50,.18); box-sizing: border-box; }
        .ai-tag { position: absolute; top: -15px; left: -2px; background: #2e7d32; color: #fff; font-size: 9px; font-weight: 700; padding: 1px 5px; border-radius: 3px; white-space: nowrap; }
        .pin { position: absolute; width: 13px; height: 13px; background: #1976d2; border: 2px solid #fff; border-radius: 50%; transform: translate(-50%, -50%); box-shadow: 0 0 0 1px #1976d2; }
        .finding-list { margin: 4px 0 4px 18px; }
        .finding-list li { font-size: 11.5px; color: #334e68; padding: 3px 0; }
        .note-card { background: #f7f9fc; border: 1px solid #e6ecf3; border-radius: 12px; padding: 12px 16px; margin: 10px 0; }
        .note-card p { font-size: 12.5px; color: #243b53; }
    """.trimIndent()

    // ---- Stats computation (parity with legacy generator) -------------------

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

    private fun ratingColor(rating: Int): String = when {
        rating >= 4 -> "#2E7D32"
        rating == 3 -> "#F9A825"
        else -> "#C62828"
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

    private fun imageLabel(image: InspectionImage): String {
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

    // ---- Image embedding ----------------------------------------------------

    private fun imageDataUri(path: String, targetWidth: Int): String? {
        val bitmap = loadBitmap(path, targetWidth) ?: return null
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, BuildConfig.PDF_IMAGE_QUALITY, out)
            out.toByteArray()
        }
        bitmap.recycle()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun loadBitmap(path: String, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        val rotated = applyExifRotation(path, decoded)
        return scaleToWidth(rotated, targetWidth)
    }

    private fun scaleToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val height = (targetWidth.toFloat() / bitmap.width * bitmap.height).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, height, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
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

    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
