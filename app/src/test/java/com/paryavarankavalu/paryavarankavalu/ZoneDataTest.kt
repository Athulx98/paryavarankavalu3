package com.paryavarankavalu.paryavarankavalu

import com.paryavarankavalu.paryavarankavalu.service.LocationUtils.getDistanceInKm
import com.paryavarankavalu.paryavarankavalu.service.LocationUtils.getNearestZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ZoneDataTest {

    @Test
    fun testDistanceCalculation() {
        // Distance between Kozhikode Central (11.2588, 75.7804) and Feroke (11.1792, 75.8419)
        // is approximately 11-12 km.
        val distance = getDistanceInKm(11.2588, 75.7804, 11.1792, 75.8419)
        assertEquals(11.0, distance, 2.0) // Relaxed delta for simple coordinate math
    }

    @Test
    fun testNearestZoneKozhikode() {
        val nearest = getNearestZone(11.2588, 75.7804)
        assertEquals("Kozhikode Central", nearest.name)
    }

    @Test
    fun testNearestZoneKochi() {
        val nearest = getNearestZone(9.93, 76.26)
        assertEquals("Kochi Ernakulam", nearest.name)
    }
}
