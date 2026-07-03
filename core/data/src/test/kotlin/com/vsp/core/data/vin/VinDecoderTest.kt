package com.vsp.core.data.vin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VinDecoderTest {

    private val decoder = VinDecoder()

    @Test
    fun `valid vin decodes with manufacturer and year`() = runTest {
        val vehicle = decoder.decode("MAT625187NB123456")
        assertThat(vehicle).isNotNull()
        assertThat(vehicle!!.decoded).isTrue()
        assertThat(vehicle.manufacturer).isEqualTo("Tata Motors")
        assertThat(vehicle.year).isNotNull()
    }

    @Test
    fun `invalid length returns null`() = runTest {
        assertThat(decoder.decode("SHORT")).isNull()
    }

    @Test
    fun `invalid characters returns null`() = runTest {
        // I, O, Q are not valid VIN characters
        assertThat(decoder.decode("MATIOQ187NB123456")).isNull()
    }

    @Test
    fun `manufacturer lookup falls back to null for unknown wmi`() {
        assertThat(decoder.decodeManufacturer("ZZZ")).isNull()
    }
}
