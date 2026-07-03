package com.vsp.core.data.mapper

import com.vsp.core.data.local.entity.AiFindingEntity
import com.vsp.core.data.local.entity.AnnotationEntity
import com.vsp.core.data.local.entity.AuditLogEntity
import com.vsp.core.data.local.entity.ChecklistResponseEntity
import com.vsp.core.data.local.entity.InspectionEntity
import com.vsp.core.data.local.entity.InspectionImageEntity
import com.vsp.core.data.local.entity.InspectorEntity
import com.vsp.core.data.local.entity.ReportEntity
import com.vsp.core.data.local.entity.VehicleEntity
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.AnnotationShape
import com.vsp.core.model.AuditLogEntry
import com.vsp.core.model.BoundingBox
import com.vsp.core.model.CaptureState
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.DamageType
import com.vsp.core.model.DocumentType
import com.vsp.core.model.FindingSource
import com.vsp.core.model.ImageQuality
import com.vsp.core.model.Inspection
import com.vsp.core.model.InspectionContext
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.InspectionStatus
import com.vsp.core.model.Inspector
import com.vsp.core.model.Report
import com.vsp.core.model.Section
import com.vsp.core.model.Severity
import com.vsp.core.model.SyncState
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.VinInputMethod

fun InspectorEntity.toDomain() = Inspector(id, displayName, email)
fun Inspector.toEntity() = InspectorEntity(id, displayName, email)

fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    vin = vin,
    category = VehicleCategory.valueOf(category),
    year = year,
    manufacturer = manufacturer,
    make = make,
    model = model,
    variant = variant,
    trim = trim,
    bodyStyle = bodyStyle,
    fuelType = fuelType,
    transmission = transmission,
    color = color,
    registrationNumber = registrationNumber,
    engineNumber = engineNumber,
    chassisNumber = chassisNumber,
    numberOfOwnerships = numberOfOwnerships,
    numberOfKeys = numberOfKeys,
    odometerKm = odometerKm,
    vinInputMethod = VinInputMethod.valueOf(vinInputMethod),
    decoded = decoded,
)

fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    vin = vin,
    category = category.name,
    year = year,
    manufacturer = manufacturer,
    make = make,
    model = model,
    variant = variant,
    trim = trim,
    bodyStyle = bodyStyle,
    fuelType = fuelType,
    transmission = transmission,
    color = color,
    registrationNumber = registrationNumber,
    engineNumber = engineNumber,
    chassisNumber = chassisNumber,
    numberOfOwnerships = numberOfOwnerships,
    numberOfKeys = numberOfKeys,
    odometerKm = odometerKm,
    vinInputMethod = vinInputMethod.name,
    decoded = decoded,
)

fun InspectionEntity.toDomain() = Inspection(
    id = id,
    inspectorId = inspectorId,
    vehicleId = vehicleId,
    context = InspectionContext.valueOf(context),
    vehicleCategory = VehicleCategory.valueOf(vehicleCategory),
    status = InspectionStatus.valueOf(status),
    currentStep = currentStep,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    gpsLat = gpsLat,
    gpsLng = gpsLng,
    deviceInfo = deviceInfo,
    exteriorScore = exteriorScore,
    interiorScore = interiorScore,
    safetyScore = safetyScore,
    cosmeticScore = cosmeticScore,
    confidenceScore = confidenceScore,
    overallCondition = overallCondition,
    finalRecommendation = finalRecommendation,
    summary = summary,
    syncState = SyncState.valueOf(syncState),
)

fun Inspection.toEntity() = InspectionEntity(
    id = id,
    inspectorId = inspectorId,
    vehicleId = vehicleId,
    context = context.name,
    vehicleCategory = vehicleCategory.name,
    status = status.name,
    currentStep = currentStep,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    gpsLat = gpsLat,
    gpsLng = gpsLng,
    deviceInfo = deviceInfo,
    exteriorScore = exteriorScore,
    interiorScore = interiorScore,
    safetyScore = safetyScore,
    cosmeticScore = cosmeticScore,
    confidenceScore = confidenceScore,
    overallCondition = overallCondition,
    finalRecommendation = finalRecommendation,
    summary = summary,
    syncState = syncState.name,
)

fun InspectionImageEntity.toDomain() = InspectionImage(
    id = id,
    inspectionId = inspectionId,
    section = Section.valueOf(section),
    position = position,
    documentType = documentType?.let { DocumentType.valueOf(it) },
    checklistSectionId = checklistSectionId,
    checklistItemId = checklistItemId,
    captureState = CaptureState.valueOf(captureState),
    skipReason = skipReason,
    localFilePath = localFilePath,
    thumbnailPath = thumbnailPath,
    remoteUrl = remoteUrl,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    capturedAt = capturedAt,
    orientation = orientation,
    quality = ImageQuality.valueOf(quality),
    aiState = SyncState.valueOf(aiState),
    syncState = SyncState.valueOf(syncState),
)

fun InspectionImage.toEntity() = InspectionImageEntity(
    id = id,
    inspectionId = inspectionId,
    section = section.name,
    position = position,
    documentType = documentType?.name,
    checklistSectionId = checklistSectionId,
    checklistItemId = checklistItemId,
    captureState = captureState.name,
    skipReason = skipReason,
    localFilePath = localFilePath,
    thumbnailPath = thumbnailPath,
    remoteUrl = remoteUrl,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    capturedAt = capturedAt,
    orientation = orientation,
    quality = quality.name,
    aiState = aiState.name,
    syncState = syncState.name,
)

fun AiFindingEntity.toDomain() = AIFinding(
    id = id,
    imageId = imageId,
    damageType = DamageType.valueOf(damageType),
    confidence = confidence,
    severity = Severity.valueOf(severity),
    boundingBox = BoundingBox(bboxX, bboxY, bboxW, bboxH),
    repairRecommendation = repairRecommendation,
    reviewRequired = reviewRequired,
    source = FindingSource.valueOf(source),
    createdAt = createdAt,
)

fun AIFinding.toEntity() = AiFindingEntity(
    id = id,
    imageId = imageId,
    damageType = damageType.name,
    confidence = confidence,
    severity = severity.name,
    bboxX = boundingBox.x,
    bboxY = boundingBox.y,
    bboxW = boundingBox.w,
    bboxH = boundingBox.h,
    repairRecommendation = repairRecommendation,
    reviewRequired = reviewRequired,
    source = source.name,
    createdAt = createdAt,
)

fun AnnotationEntity.toDomain() = Annotation(
    id = id,
    imageId = imageId,
    shape = AnnotationShape.valueOf(shape),
    geometryJson = geometryJson,
    damageType = DamageType.valueOf(damageType),
    severity = Severity.valueOf(severity),
    comment = comment,
    component = component,
    vehicleSide = vehicleSide,
    estimatedSize = estimatedSize,
    repairRequired = repairRequired,
    estimatedCost = estimatedCost,
    manualVerified = manualVerified,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Annotation.toEntity() = AnnotationEntity(
    id = id,
    imageId = imageId,
    shape = shape.name,
    geometryJson = geometryJson,
    damageType = damageType.name,
    severity = severity.name,
    comment = comment,
    component = component,
    vehicleSide = vehicleSide,
    estimatedSize = estimatedSize,
    repairRequired = repairRequired,
    estimatedCost = estimatedCost,
    manualVerified = manualVerified,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ReportEntity.toDomain() = Report(
    id = id,
    inspectionId = inspectionId,
    json = json,
    localJsonPath = localJsonPath,
    remoteJsonUrl = remoteJsonUrl,
    generatedAt = generatedAt,
    status = status,
    syncState = SyncState.valueOf(syncState),
)

fun Report.toEntity() = ReportEntity(
    id = id,
    inspectionId = inspectionId,
    json = json,
    localJsonPath = localJsonPath,
    remoteJsonUrl = remoteJsonUrl,
    generatedAt = generatedAt,
    status = status,
    syncState = syncState.name,
)

fun AuditLogEntity.toDomain() = AuditLogEntry(id, inspectionId, eventType, actorId, timestamp, detailJson)
fun AuditLogEntry.toEntity() = AuditLogEntity(id, inspectionId, eventType, actorId, timestamp, detailJson)

fun ChecklistResponseEntity.toDomain() = ChecklistResponse(
    id = id,
    inspectionId = inspectionId,
    itemId = itemId,
    status = status?.let { com.vsp.core.model.catalog.ChecklistStatus.valueOf(it) },
    rating = rating,
    numericValue = numericValue,
    textValue = textValue,
    damageTypes = damageTypesCsv?.split(',')?.filter { it.isNotBlank() }?.map { DamageType.valueOf(it) } ?: emptyList(),
    updatedAt = updatedAt,
    syncState = SyncState.valueOf(syncState),
)

fun ChecklistResponse.toEntity() = ChecklistResponseEntity(
    id = id,
    inspectionId = inspectionId,
    itemId = itemId,
    status = status?.name,
    rating = rating,
    numericValue = numericValue,
    textValue = textValue,
    damageTypesCsv = damageTypes.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name },
    updatedAt = updatedAt,
    syncState = syncState.name,
)
