package com.vsp.core.data.vin

import com.vsp.core.domain.port.VinDecodeSource
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VinInputMethod
import java.util.UUID
import javax.inject.Inject

/**
 * Offline VIN decoder. Derives model year (position 10) and manufacturer from the WMI
 * (first 3 chars) using a compact lookup. Fully deterministic and unit-tested; a networked
 * decode source can be layered in later without changing callers.
 */
class VinDecoder @Inject constructor() : VinDecodeSource {

    override suspend fun decode(vin: String): Vehicle? {
        val v = vin.trim().uppercase()
        if (!VIN_REGEX.matches(v)) return null
        return Vehicle(
            id = UUID.randomUUID().toString(),
            vin = v,
            year = decodeYear(v[9]),
            manufacturer = decodeManufacturer(v.substring(0, 3)),
            vinInputMethod = VinInputMethod.MANUAL,
            decoded = true,
        )
    }

    fun decodeYear(code: Char): Int? {
        val year = YEAR_MAP[code] ?: return null
        // Prefer the most recent plausible cycle (VIN year codes repeat every 30 years).
        return if (year + 30 <= CURRENT_YEAR) year + 30 else year
    }

    fun decodeManufacturer(wmi: String): String? = WMI_MAP.entries
        .firstOrNull { wmi.startsWith(it.key) }?.value

    companion object {
        private const val CURRENT_YEAR = 2026
        private val VIN_REGEX = Regex("^[A-HJ-NPR-Z0-9]{17}$")

        private val YEAR_MAP: Map<Char, Int> = buildMap {
            val codes = "ABCDEFGHJKLMNPRSTVWXY123456789"
            var year = 2010
            for (c in codes) { put(c, year); year++ }
        }

        private val WMI_MAP: Map<String, String> = mapOf(
            "MA3" to "Suzuki", "MAT" to "Tata Motors", "MAK" to "Honda",
            "MBH" to "Maruti Suzuki", "MAL" to "Hyundai", "MEE" to "Renault",
            "1HG" to "Honda", "1FT" to "Ford", "1G1" to "Chevrolet",
            "JHM" to "Honda", "JTD" to "Toyota", "JN1" to "Nissan",
            "WBA" to "BMW", "WDB" to "Mercedes-Benz", "WVW" to "Volkswagen",
            "SAL" to "Land Rover", "SAJ" to "Jaguar", "ZFA" to "Fiat",
            "KMH" to "Hyundai", "KNA" to "Kia", "5YJ" to "Tesla",
        )
    }
}
