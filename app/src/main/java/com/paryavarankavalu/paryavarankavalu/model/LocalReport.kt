package com.paryavarankavalu.paryavarankavalu.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class LocalReport(
    @PrimaryKey val id: String,
    val reporterId: String,
    val latitude: Double,
    val longitude: Double,
    val wasteType: String,
    val photoUrl: String,
    val status: String,
    val cleanerId: String?,
    val cleanerName: String?,
    val cleanedPhotoUrl: String?,
    val timestamp: Long,
    val region: String,
    val likesJson: String, // Stored as comma-separated or JSON string
    val priority: String
)

fun Report.toLocal(): LocalReport = LocalReport(
    id = id,
    reporterId = reporterId,
    latitude = latitude,
    longitude = longitude,
    wasteType = wasteType,
    photoUrl = photoUrl,
    status = status,
    cleanerId = cleanerId,
    cleanerName = cleanerName,
    cleanedPhotoUrl = cleanedPhotoUrl,
    timestamp = timestamp,
    region = region,
    likesJson = likes.joinToString(","),
    priority = priority
)

fun LocalReport.toDomain(): Report = Report(
    id = id,
    reporterId = reporterId,
    latitude = latitude,
    longitude = longitude,
    wasteType = wasteType,
    photoUrl = photoUrl,
    status = status,
    cleanerId = cleanerId,
    cleanerName = cleanerName,
    cleanedPhotoUrl = cleanedPhotoUrl,
    timestamp = timestamp,
    region = region,
    likes = if (likesJson.isEmpty()) emptyList() else likesJson.split(","),
    priority = priority
)
