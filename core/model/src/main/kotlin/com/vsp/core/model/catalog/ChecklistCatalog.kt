package com.vsp.core.model.catalog

/** Which vehicle categories a checklist section/item applies to. */
enum class Applicability { NEW, OLD, BOTH }

/** How a checklist item is answered, which drives the UI control used to render it. */
enum class ChecklistResponseType {
    STATUS_OK, // OK / Not OK / N/A
    YES_NO, // Yes / No (+ remark)
    PASS_FAIL, // Pass / Fail (+ remark)
    RATING_1_5, // 1..5 rating (final assessment)
    TEXT, // free text
    NUMBER, // numeric value with a unit
    COMPONENT, // present toggle + damage types; deep-links to captured photos
}

/** Normalized status values persisted for a checklist response. */
enum class ChecklistStatus {
    OK, NOT_OK, NA, YES, NO, PASS, FAIL,
    // Component condition grades (COMPONENT items).
    GOOD, MINOR_SCRATCHES, MAJOR_SCRATCHES, DAMAGE,
}

data class ChecklistItem(
    val id: String,
    val label: String,
    val responseType: ChecklistResponseType,
    val appliesTo: Applicability = Applicability.BOTH,
    val unit: String? = null,
    val mandatory: Boolean = false,
    /** Whether photo evidence is meaningful for this item (damage, wear, leaks, documents). */
    val photoCapable: Boolean = false,
)

data class ChecklistGroup(
    val id: String,
    val title: String,
    val items: List<ChecklistItem>,
)

data class ChecklistSection(
    val id: String,
    val title: String,
    val order: Int,
    val appliesTo: Applicability,
    val groups: List<ChecklistGroup>,
) {
    val allItems: List<ChecklistItem> get() = groups.flatMap { it.items }
}

/**
 * The full, catalog-driven vehicle inspection checklist. Sections and items are tagged with
 * [Applicability] so the same catalog serves both New (cosmetic/PDI) and Old (full) inspections.
 *
 * This catalog covers the item-based inspection sections. Vehicle Information and the photo
 * damage-marking flow are handled by their dedicated screens and are surfaced separately in the
 * checklist hub.
 */
object ChecklistCatalog {

    /** Item id whose stored [ChecklistResponse.textValue] holds a [com.vsp.core.model.RepairRecommendation] name. */
    const val RECOMMENDATION_ITEM_ID = "fa_recommendation"

    val sections: List<ChecklistSection> = listOf(
        documents(),
        exterior(),
        wheelsAndTyres(),
        underbody(),
        engineBay(),
        interior(),
        electrical(),
        airConditioning(),
        safety(),
        mechanical(),
        roadTest(),
        finalAssessment(),
    )

    fun forCategory(applies: Applicability): List<ChecklistSection> =
        sections.filter { it.appliesTo == Applicability.BOTH || it.appliesTo == applies }
            .map { section ->
                section.copy(
                    groups = section.groups.map { group ->
                        group.copy(items = group.items.filter { it.appliesTo == Applicability.BOTH || it.appliesTo == applies })
                    }.filter { it.items.isNotEmpty() },
                )
            }
            .filter { it.groups.isNotEmpty() }

    fun section(id: String): ChecklistSection? = sections.firstOrNull { it.id == id }

    /** Finds a checklist item by id across every section. */
    fun item(id: String): ChecklistItem? =
        sections.firstNotNullOfOrNull { section -> section.allItems.firstOrNull { it.id == id } }

    /** The section that owns a given item id. */
    fun sectionForItem(id: String): ChecklistSection? =
        sections.firstOrNull { section -> section.allItems.any { it.id == id } }

    /** Global catalog ordering of every item id (section order, then item order), for report sorting. */
    val itemOrder: Map<String, Int> by lazy {
        buildMap {
            var index = 0
            sections.forEach { section -> section.allItems.forEach { put(it.id, index++) } }
        }
    }

    // ---- Builders -----------------------------------------------------------

    private fun ok(id: String, label: String, applies: Applicability = Applicability.BOTH, photo: Boolean = false) =
        ChecklistItem(id, label, ChecklistResponseType.STATUS_OK, applies, photoCapable = photo)

    private fun yn(id: String, label: String, applies: Applicability = Applicability.BOTH, photo: Boolean = false) =
        ChecklistItem(id, label, ChecklistResponseType.YES_NO, applies, photoCapable = photo)

    private fun pf(id: String, label: String, applies: Applicability = Applicability.BOTH) =
        ChecklistItem(id, label, ChecklistResponseType.PASS_FAIL, applies)

    private fun num(id: String, label: String, unit: String, applies: Applicability = Applicability.BOTH, photo: Boolean = false) =
        ChecklistItem(id, label, ChecklistResponseType.NUMBER, applies, unit = unit, photoCapable = photo)

    // Body/panel components always warrant photos of their condition.
    private fun comp(id: String, label: String) =
        ChecklistItem(id, label, ChecklistResponseType.COMPONENT, photoCapable = true)

    private fun rating(id: String, label: String) =
        ChecklistItem(id, label, ChecklistResponseType.RATING_1_5)

    private fun documents() = ChecklistSection(
        id = "documents", title = "Documents Verification", order = 2, appliesTo = Applicability.OLD,
        groups = listOf(
            ChecklistGroup(
                "documents_main", "Documents",
                listOf(
                    yn("doc_rc", "Registration Certificate (RC)", photo = true),
                    yn("doc_insurance", "Insurance Valid", photo = true),
                    yn("doc_puc", "Pollution Certificate", photo = true),
                    yn("doc_road_tax", "Road Tax Paid", photo = true),
                    yn("doc_service_book", "Service Book Available", photo = true),
                    yn("doc_spare_key", "Spare Key Available", photo = true),
                    yn("doc_owner_manual", "Owner Manual", photo = true),
                ),
            ),
        ),
    )

    private fun exterior() = ChecklistSection(
        id = "exterior", title = "Exterior Inspection", order = 3, appliesTo = Applicability.BOTH,
        groups = listOf(
            ChecklistGroup(
                "ext_front", "Front",
                listOf(
                    comp("ext_front_bumper", "Front Bumper"),
                    comp("ext_bonnet", "Bonnet"),
                    comp("ext_grill", "Grill"),
                    comp("ext_logo", "Company Logo"),
                    comp("ext_windshield", "Windshield"),
                    comp("ext_left_headlight", "Left Headlight"),
                    comp("ext_right_headlight", "Right Headlight"),
                    comp("ext_fog_lamps", "Fog Lamps"),
                    comp("ext_number_plate", "Number Plate"),
                    ok("ext_front_parking_sensors", "Front Parking Sensors"),
                    ok("ext_front_camera", "Front Camera"),
                    ok("ext_adas_camera", "ADAS Camera"),
                ),
            ),
            ChecklistGroup(
                "ext_left", "Left Side",
                listOf(
                    comp("ext_l_front_fender", "Front Fender"),
                    comp("ext_l_front_door", "Front Door"),
                    comp("ext_l_rear_door", "Rear Door"),
                    comp("ext_l_orvm", "ORVM"),
                    comp("ext_l_door_handle", "Door Handle"),
                    comp("ext_l_running_board", "Running Board"),
                    comp("ext_l_quarter_panel", "Quarter Panel"),
                    comp("ext_l_fuel_lid", "Fuel Lid"),
                    comp("ext_l_side_glass", "Side Glass"),
                ),
            ),
            ChecklistGroup(
                "ext_right", "Right Side",
                listOf(
                    comp("ext_r_front_fender", "Front Fender"),
                    comp("ext_r_front_door", "Front Door"),
                    comp("ext_r_rear_door", "Rear Door"),
                    comp("ext_r_orvm", "ORVM"),
                    comp("ext_r_door_handle", "Door Handle"),
                    comp("ext_r_quarter_panel", "Quarter Panel"),
                    comp("ext_r_side_glass", "Side Glass"),
                ),
            ),
            ChecklistGroup(
                "ext_rear", "Rear",
                listOf(
                    comp("ext_rear_bumper", "Rear Bumper"),
                    comp("ext_boot_door", "Boot Door"),
                    comp("ext_tailgate", "Tailgate"),
                    comp("ext_tail_lamps", "Tail Lamps"),
                    comp("ext_reverse_lamps", "Reverse Lamps"),
                    comp("ext_rear_windshield", "Rear Windshield"),
                    comp("ext_spoiler", "Spoiler"),
                    ok("ext_rear_camera", "Rear Camera"),
                    ok("ext_rear_parking_sensors", "Parking Sensors"),
                    comp("ext_exhaust_pipe", "Exhaust Pipe"),
                ),
            ),
            ChecklistGroup(
                "ext_roof", "Roof",
                listOf(
                    comp("ext_roof_paint", "Roof Paint"),
                    comp("ext_sunroof", "Sunroof"),
                    comp("ext_roof_rails", "Roof Rails"),
                    comp("ext_antenna", "Antenna"),
                    comp("ext_shark_fin", "Shark Fin"),
                ),
            ),
        ),
    )

    private fun wheelWorks(prefix: String, title: String): ChecklistGroup = ChecklistGroup(
        "wheel_$prefix", title,
        listOf(
            num("wheel_${prefix}_tread", "Tread Depth", "mm", Applicability.OLD, photo = true),
            num("wheel_${prefix}_pressure", "Tyre Pressure", "psi", Applicability.OLD),
            ok("wheel_${prefix}_sidewall", "Sidewall Damage", Applicability.OLD, photo = true),
            ok("wheel_${prefix}_rim", "Rim Damage", photo = true),
            ok("wheel_${prefix}_alloy_scratch", "Alloy Wheel Scratches", photo = true),
            yn("wheel_${prefix}_nut", "Wheel Nut Present"),
        ),
    )

    private fun wheelsAndTyres() = ChecklistSection(
        id = "wheels", title = "Wheels & Tyres", order = 4, appliesTo = Applicability.BOTH,
        groups = listOf(
            wheelWorks("fl", "Front Left"),
            wheelWorks("fr", "Front Right"),
            wheelWorks("rl", "Rear Left"),
            wheelWorks("rr", "Rear Right"),
            wheelWorks("spare", "Spare Wheel"),
        ),
    )

    private fun underbody() = ChecklistSection(
        id = "underbody", title = "Underbody", order = 5, appliesTo = Applicability.OLD,
        groups = listOf(
            ChecklistGroup(
                "underbody_main", "Underbody",
                listOf(
                    ok("ub_oil_leak", "Oil Leakage", photo = true),
                    ok("ub_coolant_leak", "Coolant Leakage", photo = true),
                    ok("ub_brake_fluid_leak", "Brake Fluid Leakage", photo = true),
                    ok("ub_fuel_leak", "Fuel Leakage", photo = true),
                    ok("ub_rust", "Rust", photo = true),
                    ok("ub_chassis", "Chassis Damage", photo = true),
                    ok("ub_suspension", "Suspension Damage", photo = true),
                    ok("ub_exhaust", "Exhaust Damage", photo = true),
                ),
            ),
        ),
    )

    private fun engineBay() = ChecklistSection(
        id = "engine_bay", title = "Engine Bay", order = 6, appliesTo = Applicability.OLD,
        groups = listOf(
            ChecklistGroup(
                "engine_main", "Engine Bay",
                listOf(
                    ok("eng_oil_level", "Engine Oil Level"),
                    ok("eng_coolant_level", "Coolant Level"),
                    ok("eng_brake_fluid", "Brake Fluid"),
                    ok("eng_washer_fluid", "Washer Fluid"),
                    ok("eng_battery_condition", "Battery Condition", photo = true),
                    ok("eng_battery_terminals", "Battery Terminals", photo = true),
                    ok("eng_belts", "Belts", photo = true),
                    ok("eng_hoses", "Hoses", photo = true),
                    ok("eng_air_filter", "Air Filter", photo = true),
                    ok("eng_mounts", "Engine Mounts", photo = true),
                    ok("eng_oil_leak", "Oil Leakage", photo = true),
                    ok("eng_noise", "Engine Noise"),
                ),
            ),
        ),
    )

    private fun interior() = ChecklistSection(
        id = "interior", title = "Interior Inspection", order = 7, appliesTo = Applicability.BOTH,
        groups = listOf(
            ChecklistGroup(
                "int_dashboard", "Dashboard",
                listOf(
                    comp("int_dashboard_damage", "Dashboard Damage"),
                    ok("int_instrument_cluster", "Instrument Cluster"),
                    ok("int_warning_lights", "Warning Lights", photo = true),
                    ok("int_speedometer", "Speedometer"),
                    ok("int_tachometer", "Tachometer"),
                ),
            ),
            ChecklistGroup(
                "int_seats", "Seats",
                listOf(
                    comp("int_driver_seat", "Driver Seat"),
                    comp("int_passenger_seat", "Passenger Seat"),
                    comp("int_rear_seat", "Rear Seat"),
                    ok("int_seat_covers", "Seat Covers", photo = true),
                    ok("int_seat_adjustment", "Seat Adjustment"),
                ),
            ),
            ChecklistGroup(
                "int_steering", "Steering",
                listOf(
                    ok("int_steering_wear", "Steering Wear", Applicability.OLD, photo = true),
                    ok("int_horn", "Horn"),
                    ok("int_steering_controls", "Steering Controls"),
                ),
            ),
            ChecklistGroup(
                "int_doors", "Doors",
                listOf(
                    ok("int_door_lock", "Lock"),
                    ok("int_window_operation", "Window Operation"),
                    ok("int_child_lock", "Child Lock"),
                    comp("int_door_trim", "Door Trim"),
                    ok("int_door_pocket", "Door Pocket"),
                ),
            ),
            ChecklistGroup(
                "int_roof", "Roof Interior",
                listOf(
                    comp("int_roof_lining", "Roof Lining"),
                    ok("int_cabin_lights", "Cabin Lights"),
                    ok("int_sun_visor", "Sun Visor"),
                    ok("int_grab_handles", "Grab Handles"),
                ),
            ),
            ChecklistGroup(
                "int_floor", "Floor",
                listOf(
                    ok("int_floor_mats", "Floor Mats"),
                    ok("int_carpet", "Carpet", photo = true),
                    ok("int_water_leak", "Water Leakage", photo = true),
                    ok("int_mud", "Mud", photo = true),
                ),
            ),
        ),
    )

    private fun electrical() = ChecklistSection(
        id = "electrical", title = "Electrical Inspection", order = 8, appliesTo = Applicability.BOTH,
        groups = listOf(
            ChecklistGroup(
                "electrical_main", "Electrical",
                listOf(
                    ok("el_headlights", "Headlights"),
                    ok("el_high_beam", "High Beam"),
                    ok("el_indicators", "Indicators"),
                    ok("el_brake_lights", "Brake Lights"),
                    ok("el_reverse_lights", "Reverse Lights"),
                    ok("el_hazard", "Hazard Lights"),
                    ok("el_drl", "DRL"),
                    ok("el_cabin_lights", "Cabin Lights"),
                    ok("el_power_windows", "Power Windows"),
                    ok("el_central_locking", "Central Locking"),
                    ok("el_remote_lock", "Remote Lock"),
                    ok("el_infotainment", "Infotainment"),
                    ok("el_touchscreen", "Touchscreen"),
                    ok("el_bluetooth", "Bluetooth"),
                    ok("el_usb", "USB Ports"),
                    ok("el_wireless_charger", "Wireless Charger"),
                    ok("el_12v", "12V Socket"),
                    ok("el_wipers", "Wipers"),
                    ok("el_washer_pump", "Washer Pump"),
                ),
            ),
        ),
    )

    private fun airConditioning() = ChecklistSection(
        id = "ac", title = "Air Conditioning", order = 9, appliesTo = Applicability.BOTH,
        groups = listOf(
            ChecklistGroup(
                "ac_main", "Air Conditioning",
                listOf(
                    ok("ac_cooling", "Cooling Performance"),
                    ok("ac_blower", "Blower Speed"),
                    ok("ac_compressor", "AC Compressor"),
                    ok("ac_rear_vent", "Rear AC Vent"),
                    ok("ac_heater", "Heater"),
                ),
            ),
        ),
    )

    private fun safety() = ChecklistSection(
        id = "safety", title = "Safety Features", order = 10, appliesTo = Applicability.BOTH,
        groups = listOf(
            ChecklistGroup(
                "safety_main", "Safety Features",
                listOf(
                    ok("sf_airbags", "Airbags"),
                    ok("sf_seat_belts", "Seat Belts"),
                    ok("sf_abs", "ABS"),
                    ok("sf_esp", "ESP"),
                    ok("sf_tpms", "TPMS"),
                    ok("sf_reverse_camera", "Reverse Camera"),
                    ok("sf_parking_sensors", "Parking Sensors"),
                    ok("sf_adas", "ADAS Functions"),
                    ok("sf_blind_spot", "Blind Spot Monitor"),
                    ok("sf_lane_assist", "Lane Assist"),
                ),
            ),
        ),
    )

    private fun mechanical() = ChecklistSection(
        id = "mechanical", title = "Mechanical Inspection", order = 11, appliesTo = Applicability.OLD,
        groups = listOf(
            ChecklistGroup(
                "mech_main", "Mechanical",
                listOf(
                    ok("mech_engine_start", "Engine Start"),
                    ok("mech_engine_idle", "Engine Idle"),
                    ok("mech_clutch", "Clutch"),
                    ok("mech_gear_shift", "Gear Shift"),
                    ok("mech_auto_transmission", "Automatic Transmission"),
                    ok("mech_brakes", "Brakes"),
                    ok("mech_hand_brake", "Hand Brake"),
                    ok("mech_suspension", "Suspension"),
                    ok("mech_steering_alignment", "Steering Alignment"),
                    ok("mech_wheel_alignment", "Wheel Alignment"),
                ),
            ),
        ),
    )

    private fun roadTest() = ChecklistSection(
        id = "road_test", title = "Road Test", order = 12, appliesTo = Applicability.OLD,
        groups = listOf(
            ChecklistGroup(
                "road_main", "Road Test",
                listOf(
                    pf("rt_engine_power", "Engine Power"),
                    pf("rt_acceleration", "Acceleration"),
                    pf("rt_braking", "Braking"),
                    pf("rt_steering", "Steering"),
                    pf("rt_suspension", "Suspension"),
                    pf("rt_noise_vibration", "Noise/Vibration"),
                    pf("rt_gear_shifting", "Gear Shifting"),
                    pf("rt_cruise_control", "Cruise Control"),
                ),
            ),
        ),
    )

    private fun finalAssessment() = ChecklistSection(
        id = "final_assessment", title = "Final Assessment", order = 15, appliesTo = Applicability.BOTH,
        groups = listOf(
            ChecklistGroup(
                "final_ratings", "Category Ratings (1-5)",
                listOf(
                    rating("fa_exterior", "Exterior"),
                    rating("fa_interior", "Interior"),
                    rating("fa_engine", "Engine"),
                    rating("fa_electrical", "Electrical"),
                    rating("fa_tyres", "Tyres"),
                    rating("fa_suspension", "Suspension"),
                    rating("fa_safety", "Safety"),
                    rating("fa_documentation", "Documentation"),
                ),
            ),
            ChecklistGroup(
                "final_recommendation_group", "Recommendation",
                listOf(
                    ChecklistItem(RECOMMENDATION_ITEM_ID, "Repair Recommendation", ChecklistResponseType.TEXT),
                ),
            ),
            ChecklistGroup(
                "final_remarks", "Inspector Remarks",
                listOf(
                    ChecklistItem("fa_remarks", "Remarks", ChecklistResponseType.TEXT),
                ),
            ),
        ),
    )
}
