package com.paryavarankavalu.paryavarankavalu.model

data class Report(
    val id: String = "",
    val reporterId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val wasteType: String = "",
    val photoUrl: String = "",
    val status: String = "Reported",
    val cleanerId: String? = null,
    val cleanerName: String? = null,
    val cleanedPhotoUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val region: String = "",
    val likes: List<String> = emptyList(),
    val priority: String = "Low"
)

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val ecoKarma: Int = 0,
    val reportsCount: Int = 0,
    val cleanupsCount: Int = 0,
    val streak: Int = 0,
    val lastActivityTimestamp: Long = 0,
    val assignedRegion: String? = null,
    val role: String = "Citizen", // Citizen or Volunteer
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)