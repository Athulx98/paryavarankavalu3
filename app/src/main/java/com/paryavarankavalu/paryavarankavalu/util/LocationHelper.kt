package com.paryavarankavalu.paryavarankavalu.util

import android.content.Context
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

object LocationHelper {
    suspend fun getAddressFromLatLng(context: Context, lat: Double, lng: Double): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val res = addresses.firstOrNull()?.let {
                            listOfNotNull(it.thoroughfare, it.subLocality, it.locality)
                                .joinToString(", ")
                        } ?: "Lat: ${"%.4f".format(lat)}, Lng: ${"%.4f".format(lng)}"
                        continuation.resume(res)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                addresses?.firstOrNull()?.let {
                    listOfNotNull(it.thoroughfare, it.subLocality, it.locality)
                        .joinToString(", ")
                } ?: "Lat: ${"%.4f".format(lat)}, Lng: ${"%.4f".format(lng)}"
            }
        } catch (e: IOException) {
            "Lat: ${"%.4f".format(lat)}, Lng: ${"%.4f".format(lng)}"
        } catch (e: Exception) {
            "Address unavailable"
        }
    }

    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()}m"
        } else {
            "${"%.1f".format(distanceKm)}km"
        }
    }

    fun estimateWalkingTime(distanceKm: Double): String {
        val speedKmH = 5.0
        val timeHours = distanceKm / speedKmH
        val timeMinutes = (timeHours * 60).toInt()
        
        return if (timeMinutes < 60) {
            "$timeMinutes min walk"
        } else {
            val hours = timeMinutes / 60
            val mins = timeMinutes % 60
            "$hours hr $mins min walk"
        }
    }

    fun isWithinRadius(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        radiusKm: Double
    ): Boolean {
        val R = 6371.0 // Radius of the earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = R * c
        return distance <= radiusKm
    }
}
