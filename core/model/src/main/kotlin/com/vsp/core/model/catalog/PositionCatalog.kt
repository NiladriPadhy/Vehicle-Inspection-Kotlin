package com.vsp.core.model.catalog

import com.vsp.core.model.DocumentType
import com.vsp.core.model.Section

/** A defined capture position with guidance metadata, in strict sequence order. */
data class CapturePosition(
    val id: String,
    val section: Section,
    val displayName: String,
    val mandatory: Boolean,
    val order: Int,
)

/**
 * Ordered catalogs of capture positions. The capture wizard MUST advance through these in
 * [CapturePosition.order] sequence (see FR-008a).
 */
object PositionCatalog {

    val exterior: List<CapturePosition> = listOf(
        "FRONT" to "Front",
        "FRONT_LEFT_CORNER" to "Front Left Corner (45°)",
        "LEFT_SIDE" to "Left Side",
        "REAR_LEFT_CORNER" to "Rear Left Corner",
        "REAR" to "Rear",
        "REAR_RIGHT_CORNER" to "Rear Right Corner",
        "RIGHT_SIDE" to "Right Side",
        "FRONT_RIGHT_CORNER" to "Front Right Corner",
        "ROOF" to "Roof",
        "BONNET" to "Bonnet / Hood",
        "WINDSHIELD_CLOSEUP" to "Windshield Close-up",
        "REAR_WINDSHIELD" to "Rear Windshield",
        "FRONT_LEFT_WHEEL" to "Front Left Wheel",
        "FRONT_RIGHT_WHEEL" to "Front Right Wheel",
        "REAR_LEFT_WHEEL" to "Rear Left Wheel",
        "REAR_RIGHT_WHEEL" to "Rear Right Wheel",
        "DRIVER_DOOR_CLOSEUP" to "Driver Door Close-up",
        "PASSENGER_DOOR_CLOSEUP" to "Passenger Door Close-up",
        "FUEL_CAP_AREA" to "Fuel Cap Area",
        "FRONT_BUMPER_CLOSEUP" to "Front Bumper Close-up",
        "REAR_BUMPER_CLOSEUP" to "Rear Bumper Close-up",
        "HEADLIGHTS" to "Headlights",
        "TAIL_LIGHTS" to "Tail Lights",
        "NUMBER_PLATE" to "Number Plate",
        "UNDERBODY" to "Underbody",
    ).mapIndexed { index, (id, name) ->
        CapturePosition(
            id = id,
            section = Section.EXTERIOR,
            displayName = name,
            mandatory = id != "UNDERBODY", // Underbody is optional
            order = index,
        )
    }

    val interior: List<CapturePosition> = listOf(
        "DASHBOARD" to "Dashboard",
        "STEERING_WHEEL" to "Steering Wheel",
        "INSTRUMENT_CLUSTER" to "Instrument Cluster",
        "ODOMETER" to "Odometer",
        "INFOTAINMENT_DISPLAY" to "Infotainment Display",
        "GEAR_LEVER" to "Gear Lever",
        "CENTER_CONSOLE" to "Center Console",
        "DRIVER_SEAT" to "Driver Seat",
        "PASSENGER_SEAT" to "Passenger Seat",
        "REAR_SEATS" to "Rear Seats",
        "ROOF_LINING" to "Roof Lining",
        "FLOOR_MATS" to "Floor Mats",
        "DRIVER_DOOR_TRIM" to "Driver Door Trim",
        "PASSENGER_DOOR_TRIM" to "Passenger Door Trim",
        "REAR_DOOR_TRIM" to "Rear Door Trim",
        "BOOT_TRUNK" to "Boot / Trunk",
        "SPARE_WHEEL" to "Spare Wheel",
        "TOOLKIT" to "Toolkit",
        "AC_CONTROLS" to "AC Controls",
        "PEDALS" to "Pedals",
        "SEAT_BELTS" to "Seat Belts",
        "SUNROOF" to "Sunroof (if available)",
        "GLOVE_BOX" to "Glove Box",
        "CUP_HOLDERS" to "Cup Holders",
        "CARGO_AREA" to "Cargo Area",
    ).mapIndexed { index, (id, name) ->
        CapturePosition(
            id = id,
            section = Section.INTERIOR,
            displayName = name,
            mandatory = id != "SUNROOF", // Sunroof only when available
            order = index,
        )
    }

    fun forSection(section: Section): List<CapturePosition> = when (section) {
        Section.EXTERIOR -> exterior
        Section.INTERIOR -> interior
        Section.DOCUMENT -> emptyList()
    }
}

/** Old-vehicle documents required before exterior capture (see US9). */
object DocumentCatalog {
    data class DocumentSlot(val type: DocumentType, val displayName: String, val order: Int)

    val oldVehicleDocuments: List<DocumentSlot> = listOf(
        DocumentSlot(DocumentType.RC, "Registration Certificate (RC)", 0),
        DocumentSlot(DocumentType.POLLUTION_CERTIFICATE, "Pollution Certificate (PUC)", 1),
        DocumentSlot(DocumentType.INSURANCE, "Insurance", 2),
    )
}
