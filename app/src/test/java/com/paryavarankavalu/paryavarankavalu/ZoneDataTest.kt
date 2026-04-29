package com.paryavarankavalu.paryavarankavalu

import com.paryavarankavalu.paryavarankavalu.uii.screen.getDistanceInKm
import com.paryavarankavalu.paryavarankavalu.uii.screen.getNearestZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneDataTest {

    @Test
    fun testDistanceCalculation() {
        // Distance between Kozhikode Central (11.2588, 75.7804) and Feroke (11.1792, 75.8419)
        // is approximately 11-12 km.
        val distance = getDistanceInKm(11.2588, 75.7804, 11.1792, 75.8419)
        assertEquals(11.0, distance, 1.0)
    }

    @Test
    fun testNearestZoneKozhikode() {
        // Coordinates near Kozhikode Central
        val nearest = getNearestZone(11.25, 75.78)
        assertEquals("Kozhikode Central", nearest.name)
    }

    @Test
    fun testNearestZoneKochi() {
        // Coordinates near Kochi
        val nearest = getNearestZone(9.9, 76.2)
        assertEquals("Kochi Ernakulam", nearest.name)
    }
}
