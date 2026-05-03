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
    val priority: String = "Low",
    val aiSuggestedCategory: String? = null,
    val afterImageUri: String? = null,
    val aiCleanStatus: String? = null,
    val beforeImageUri: String? = null,
    val aiCleanupResult: String? = null,
    val beforeDetectedLabels: List<String>? = null,
    val afterDetectedLabels: List<String>? = null,
    val assignedTo: String? = null
)

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val ecoKarma: Long = 0,      // Changed from Int to Long for Firebase compatibility
    val reportsCount: Long = 0,   // Changed from Int to Long
    val cleanupsCount: Long = 0,  // Changed from Int to Long
    val streak: Int = 0,
    val lastActivityTimestamp: Long = 0,
    val assignedRegion: String? = null,
    val role: String = "Citizen", // Citizen or Volunteer
    val pushNotificationsEnabled: Boolean = true,
    val notificationSound: String = "Default",
    val vibrationEnabled: Boolean = true,
    val fcmToken: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
