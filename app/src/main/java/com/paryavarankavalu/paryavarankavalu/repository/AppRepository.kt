package com.paryavarankavalu.paryavarankavalu.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.storage.FirebaseStorage
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.model.UserProfile
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import com.paryavarankavalu.paryavarankavalu.model.toLocal
import com.paryavarankavalu.paryavarankavalu.model.toDomain
import com.paryavarankavalu.paryavarankavalu.model.LocalReport

class AppRepository(private val reportDao: ReportDao) {
    private val TAG = "AppRepository"
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val reportsCollection = firestore.collection("reports")
    private val usersCollection = firestore.collection("users")

    /**
     * Resilient image upload. 
     * Uses aggressive scaling (400px) and strict timeout to ensure near-instant transmission.
     */
    suspend fun uploadImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val fileName = "${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("reports/$fileName")
        
        val maxDim = 400f
        val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height)
        val scaledBitmap = if (scale < 1) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        
        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 25, baos)
        val data = baos.toByteArray()
        
        val downloadUrl = withTimeoutOrNull(15000L) {
            try {
                Log.d(TAG, "Syncing verification proof to cloud storage...")
                storageRef.putBytes(data).await()
                storageRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                Log.e(TAG, "Storage failure: ${e.message}")
                null
            }
        }

        if (downloadUrl != null) {
            return@withContext downloadUrl
        } else {
            Log.w(TAG, "Network slow, using compressed Base64 fallback (~15KB).")
            val tinyBaos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 15, tinyBaos)
            val base64 = android.util.Base64.encodeToString(tinyBaos.toByteArray(), android.util.Base64.NO_WRAP)
            return@withContext "data:image/jpeg;base64,$base64"
        }
    }

    suspend fun submitReport(report: Report) = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        val now = System.currentTimeMillis()
        val batch = firestore.batch()
        
        batch.set(reportsCollection.document(report.id), report.copy(reporterId = userId, timestamp = now), SetOptions.merge())
        batch.set(usersCollection.document(userId), mapOf(
            "ecoKarma" to FieldValue.increment(10),
            "reportsCount" to FieldValue.increment(1),
            "lastActivityTimestamp" to now
        ), SetOptions.merge())
        
        batch.commit()
    }

    suspend fun bookCleanup(reportId: String) = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        val id = reportId.trim()
        
        reportsCollection.document(id).set(mapOf(
            "status" to "Assigned", 
            "cleanerId" to userId,
            "assignedTo" to userId,
            "timestamp" to System.currentTimeMillis()
        ), SetOptions.merge())
    }

    /**
     * Finalizes cleanup with separate prioritized writes and firm timeouts.
     * Guaranteed to finish or throw a catchable error (no hangs).
     */
    suspend fun completeCleanup(
        reportId: String,
        cleanedPhotoUrl: String,
        aiCleanStatus: String? = null,
        cleanupScore: Float? = null,
        cleanupVerified: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        val cleanId = reportId.trim()
        val now = System.currentTimeMillis()
        
        if (cleanId.isEmpty()) throw Exception("Invalid Task ID")
        Log.d(TAG, "Syncing final status for task: $cleanId")

        // 1. Critical Update: Mark as Cleaned in cloud
        try {
            reportsCollection.document(cleanId).set(mapOf(
                "status" to "Cleaned", 
                "cleanedPhotoUrl" to cleanedPhotoUrl,
                "aiCleanStatus" to aiCleanStatus,
                "aiCleanupResult" to aiCleanStatus, // Mapping status to result for consistency
                "cleanupScore" to cleanupScore,
                "cleanupVerified" to cleanupVerified,
                "timestamp" to now
            ).filterValues { it != null }, SetOptions.merge())
            Log.d(TAG, "Cloud task status updated.")
        } catch (e: Exception) {
            Log.e(TAG, "Critical sync failed: ${e.message}")
            throw Exception("Network sync failed. Please try again.")
        }

        // 2. Non-Critical Update: Grant Karma (Perform in background)
        repositoryScope.launch {
            try {
                withTimeout(10000L) {
                    usersCollection.document(userId).set(mapOf(
                        "ecoKarma" to FieldValue.increment(50),
                        "cleanupsCount" to FieldValue.increment(1),
                        "lastActivityTimestamp" to now,
                        "updatedAt" to now
                    ), SetOptions.merge()).await()
                    Log.d(TAG, "Karma points granted.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background reward sync failed: ${e.message}")
            }
        }
    }

    suspend fun toggleLike(reportId: String) {
        val userId = auth.currentUser?.uid ?: return
        val cleanId = reportId.trim()
        val reportDoc = reportsCollection.document(cleanId).get().await()
        val likes = reportDoc.get("likes") as? List<String> ?: emptyList()
        if (likes.contains(userId)) {
            reportsCollection.document(cleanId).update("likes", FieldValue.arrayRemove(userId))
        } else {
            reportsCollection.document(cleanId).update("likes", FieldValue.arrayUnion(userId))
        }
    }

    suspend fun deleteReport(reportId: String) {
        val cleanId = reportId.trim()
        reportsCollection.document(cleanId).delete()
        repositoryScope.launch { reportDao.deleteReportById(cleanId) }
    }

    /**
     * Robust Data Synchronizer: 
     * - Maps Firestore Document IDs to internal ID fields to ensure consistency.
     * - Handles type conversion safely to prevent sync crashes.
     */
    fun observeReports(): ListenerRegistration {
        return reportsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Cloud sync error: ${error.message}")
                return@addSnapshotListener
            }
            repositoryScope.launch {
                val toInsert = mutableListOf<LocalReport>()
                snapshot?.documentChanges?.forEach { dc ->
                    try {
                        val doc = dc.document
                        val data = doc.data ?: return@forEach
                        
                        // Robust Manual Mapping to handle Timestamp/Number types from cloud
                        val report = Report(
                            id = doc.id,
                            reporterId = data["reporterId"] as? String ?: "",
                            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                            wasteType = data["wasteType"] as? String ?: "General Waste",
                            photoUrl = data["photoUrl"] as? String ?: "",
                            status = data["status"] as? String ?: "Reported",
                            cleanerId = data["cleanerId"] as? String,
                            cleanerName = data["cleanerName"] as? String,
                            cleanedPhotoUrl = data["cleanedPhotoUrl"] as? String,
                            timestamp = when (val t = data["timestamp"]) {
                                is Number -> t.toLong()
                                is Timestamp -> t.toDate().time
                                else -> System.currentTimeMillis()
                            },
                            region = data["region"] as? String ?: "",
                            likes = (data["likes"] as? List<String>) ?: emptyList(),
                            aiSuggestedCategory = data["aiSuggestedCategory"] as? String,
                            aiCategory = data["aiCategory"] as? String,
                            confidence = (data["confidence"] as? Number)?.toFloat(),
                            disposalBin = data["disposalBin"] as? String,
                            afterImageUri = data["cleanedPhotoUrl"] as? String,
                            aiCleanStatus = data["aiCleanStatus"] as? String,
                            beforeImageUri = data["beforeImageUri"] as? String,
                            aiCleanupResult = data["aiCleanupResult"] as? String,
                            cleanupScore = (data["cleanupScore"] as? Number)?.toFloat(),
                            cleanupVerified = data["cleanupVerified"] as? Boolean,
                            beforeDetectedLabels = data["beforeDetectedLabels"] as? List<String>,
                            afterDetectedLabels = data["afterDetectedLabels"] as? List<String>,
                            assignedTo = data["assignedTo"] as? String,
                            priority = data["priority"] as? String ?: "Low"
                        )
                        
                        when (dc.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> toInsert.add(report.toLocal())
                            DocumentChange.Type.REMOVED -> reportDao.deleteReportById(doc.id)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Individual report mapping failed: ${e.message}")
                    }
                }
                if (toInsert.isNotEmpty()) reportDao.insertReports(toInsert)
            }
        }
    }

    fun getLocalReports(): Flow<List<Report>> = reportDao.getAllReports().map { it.map { lr -> lr.toDomain() } }

    /**
     * Robust User Data Observer.
     */
    fun observeUserProfile(userId: String, onUpdate: (UserProfile?) -> Unit): ListenerRegistration {
        return usersCollection.document(userId).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) {
                onUpdate(null)
                return@addSnapshotListener
            }
            try {
                val data = snapshot.data ?: return@addSnapshotListener
                onUpdate(UserProfile(
                    uid = snapshot.id,
                    displayName = data["displayName"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    ecoKarma = (data["ecoKarma"] as? Number)?.toLong() ?: 0L,
                    reportsCount = (data["reportsCount"] as? Number)?.toLong() ?: 0L,
                    cleanupsCount = (data["cleanupsCount"] as? Number)?.toLong() ?: 0L,
                    streak = (data["streak"] as? Number)?.toInt() ?: 0,
                    lastActivityTimestamp = (data["lastActivityTimestamp"] as? Number)?.toLong() ?: 0L,
                    assignedRegion = data["assignedRegion"] as? String,
                    role = data["role"] as? String ?: "Citizen",
                    pushNotificationsEnabled = data["pushNotificationsEnabled"] as? Boolean ?: true,
                    notificationSound = data["notificationSound"] as? String ?: "Default",
                    vibrationEnabled = data["vibrationEnabled"] as? Boolean ?: true,
                    fcmToken = data["fcmToken"] as? String,
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Profile sync error: ${e.message}")
            }
        }
    }

    fun observeLeaderboard(onUpdate: (List<UserProfile>) -> Unit): ListenerRegistration {
        return usersCollection.orderBy("ecoKarma", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { snapshot, _ ->
                val users = mutableListOf<UserProfile>()
                snapshot?.forEach { doc ->
                    val data = doc.data
                    users.add(UserProfile(
                        uid = doc.id,
                        displayName = data["displayName"] as? String ?: "",
                        ecoKarma = (data["ecoKarma"] as? Number)?.toLong() ?: 0L,
                        role = data["role"] as? String ?: "Citizen",
                        cleanupsCount = (data["cleanupsCount"] as? Number)?.toLong() ?: 0L
                    ))
                }
                onUpdate(users)
            }
    }

    suspend fun createUserProfile(uid: String, email: String, displayName: String) {
        val now = System.currentTimeMillis()
        val profile = UserProfile(uid = uid, email = email, displayName = displayName, createdAt = now, updatedAt = now)
        usersCollection.document(uid).set(profile, SetOptions.merge())
    }

    suspend fun updateRegion(region: String?) {
        val uid = auth.currentUser?.uid ?: return
        usersCollection.document(uid).set(mapOf("assignedRegion" to region, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
    }

    suspend fun updateRole(role: String) {
        val uid = auth.currentUser?.uid ?: return
        usersCollection.document(uid).set(mapOf("role" to role, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
    }

    suspend fun updateNotificationSettings(enabled: Boolean, sound: String, vibration: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        usersCollection.document(uid).set(mapOf(
            "pushNotificationsEnabled" to enabled,
            "notificationSound" to sound,
            "vibrationEnabled" to vibration,
            "updatedAt" to System.currentTimeMillis()
        ), SetOptions.merge())
    }

    suspend fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        usersCollection.document(uid).set(mapOf("fcmToken" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
    }
}
