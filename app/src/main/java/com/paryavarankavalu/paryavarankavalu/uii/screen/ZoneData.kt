package com.paryavarankavalu.paryavarankavalu.uii.screen

import kotlin.math.*

data class Zone(val name: String, val latitude: Double, val longitude: Double)

val SMART_ZONES = listOf(
  // Kozhikode
  Zone("Kozhikode Central", 11.2588, 75.7804),
  Zone("Feroke", 11.1792, 75.8419),
  Zone("Kunnamangalam", 11.3048, 75.8770),
  Zone("Beypore", 11.1716, 75.8131),
  Zone("Elathur", 11.3088, 75.8243),
  Zone("Chevayur", 11.2800, 75.8200),
  Zone("Kuttichira", 11.2481, 75.7749),
  Zone("Palayam", 11.2512, 75.7832),
  // Kerala
  Zone("Thrissur", 10.5276, 76.2144),
  Zone("Kochi Ernakulam", 9.9312, 76.2673),
  Zone("Thiruvananthapuram", 8.5241, 76.9366),
  Zone("Kannur", 11.8745, 75.3704),
  Zone("Malappuram", 11.0730, 76.0740),
  Zone("Palakkad", 10.7867, 76.6548),
  Zone("Kollam", 8.8932, 76.6141),
  Zone("Alappuzha", 9.4981, 76.3388)
)

fun getDistanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0 // Radius of the earth in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

fun getNearestZone(lat: Double, lon: Double): Zone {
    return SMART_ZONES.minByOrNull { getDistanceInKm(lat, lon, it.latitude, it.longitude) }
        ?: SMART_ZONES.first()
}
