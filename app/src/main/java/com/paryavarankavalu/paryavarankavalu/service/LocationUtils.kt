package com.paryavarankavalu.paryavarankavalu.service

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.maps.model.LatLng
import java.util.*

data class Zone(val name: String, val latitude: Double, val longitude: Double)

object LocationUtils {
    val SMART_ZONES = listOf(
        Zone("All India", 0.0, 0.0),
        // Kozhikode area
        Zone("Kozhikode Central", 11.2588, 75.7804),
        Zone("Feroke", 11.1792, 75.8419),
        Zone("Kunnamangalam", 11.3048, 75.8770),
        Zone("Beypore", 11.1716, 75.8131),
        Zone("Elathur", 11.3088, 75.8243),
        Zone("Chevayur", 11.2800, 75.8200),
        Zone("Kuttichira", 11.2481, 75.7749),
        Zone("Palayam", 11.2512, 75.7832),
        // Other Kerala cities
        Zone("Thrissur", 10.5276, 76.2144),
        Zone("Kochi Ernakulam", 9.9312, 76.2673),
        Zone("Thiruvananthapuram", 8.5241, 76.9366),
        Zone("Kannur", 11.8745, 75.3704),
        Zone("Malappuram", 11.0730, 76.0740),
        Zone("Palakkad", 10.7867, 76.6548),
        Zone("Kollam", 8.8932, 76.6141),
        Zone("Alappuzha", 9.4981, 76.3388)
    )

    fun getDistanceFromLatLonInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return (results[0] / 1000).toDouble()
    }

    fun getNearestZone(lat: Double, lng: Double): Zone {
        return SMART_ZONES.filter { it.name != "All India" }
            .minByOrNull { getDistanceFromLatLonInKm(lat, lng, it.latitude, it.longitude) }
            ?: SMART_ZONES[0]
    }

    fun getAddressFromLatLng(context: Context, lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.let {
                listOfNotNull(it.thoroughfare, it.subLocality, it.locality)
                    .joinToString(", ")
            } ?: "Unknown Location"
        } catch (e: Exception) {
            "Address unavailable"
        }
    }
}
