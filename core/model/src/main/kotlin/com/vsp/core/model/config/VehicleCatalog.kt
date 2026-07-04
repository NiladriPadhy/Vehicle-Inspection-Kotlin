package com.vsp.core.model.config

import kotlinx.serialization.Serializable

/**
 * Vendor-configurable make → model → variant catalog fetched from Firebase RTDB. Used by vehicle
 * identification for manual attribute selection when VIN decode is unavailable/incomplete.
 */
@Serializable
data class VehicleCatalog(
    val version: Int,
    val hash: String = "",
    val updatedAt: Long = 0L,
    val makes: List<VehicleMake> = emptyList(),
)

@Serializable
data class VehicleMake(
    val id: String,
    val name: String,
    val order: Int = 0,
    val models: List<VehicleModelEntry> = emptyList(),
)

@Serializable
data class VehicleModelEntry(
    val id: String,
    val name: String,
    val order: Int = 0,
    val variants: List<VehicleVariant> = emptyList(),
)

@Serializable
data class VehicleVariant(
    val id: String,
    val name: String,
    val fuelType: String? = null,
    val transmission: String? = null,
    val bodyStyle: String? = null,
)
