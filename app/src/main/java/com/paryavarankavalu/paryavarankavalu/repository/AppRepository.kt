package com.paryavarankavalu.paryavarankavalu.repository

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.model.UserProfile
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.TimeUnit

class AppRepository {
    private val databaseId = "ai-studio-53d8912c-025e-436b-963e-d638bad473c7"
    
    private val firestore = FirebaseFirestore.getInstance(databaseId)
    private val auth = FirebaseAuth.getInstance()
    
    private val storage = FirebaseStorage.getInstance()

    private val reportsCollection = firestore.collection("reports")
    private val usersCollection = firestore.collection("users")

    suspend fun uploadImage(bitmap: Bitmap): String {
        val fileName = "${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("reports/$fileName")
        
        // Scale down aggressively for fallback base64
        val maxDim = 600f
        val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height)
        val scaledBitmap = if (scale < 1) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap

        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        val data = baos.toByteArray()
        
        return try {
            storageRef.putBytes(data).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            // Fallback if Firebase Storage is not configured: save as base64 data URI directly in Firestore
            val base64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        }
    }

    suspend fun submitReport(report: Report) {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        reportsCollection.document(report.id).set(report.copy(reporterId = userId), SetOptions.merge()).await()
        
        updateActivityAndKarma(userId, 10, isReport = true)
    }

    suspend fun createUserProfile(uid: String, email: String, displayName: String) {
        val profile = UserProfile(
            uid = uid,
            email = email,
            displayName = displayName,
            role = "Citizen",
            ecoKarma = 0
        )
        usersCollection.document(uid).set(profile, SetOptions.merge()).await()
    }

    suspend fun bookCleanup(reportId: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        
        val userDoc = usersCollection.document(userId).get().await()
        val displayName = userDoc.getString("displayName") ?: "Eco Warrior"

        reportsCollection.document(reportId).set(
            mapOf(
                "status" to "Assigned", 
                "cleanerId" to userId,
                "cleanerName" to displayName
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun completeCleanup(reportId: String, cleanedPhotoUrl: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        reportsCollection.document(reportId).set(
            mapOf(
                "status" to "Cleaned", 
                "cleanedPhotoUrl" to cleanedPhotoUrl,
                "timestamp" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
        
        updateActivityAndKarma(userId, 50, isCleanup = true)
    }

    suspend fun toggleLike(reportId: String) {
        val userId = auth.currentUser?.uid ?: return
        val reportDoc = reportsCollection.document(reportId).get().await()
        val likes = reportDoc.get("likes") as? List<String> ?: emptyList()
        
        if (likes.contains(userId)) {
            reportsCollection.document(reportId).update("likes", FieldValue.arrayRemove(userId)).await()
        } else {
            reportsCollection.document(reportId).update("likes", FieldValue.arrayUnion(userId)).await()
        }
    }

    private suspend fun updateActivityAndKarma(userId: String, karmaPoints: Int, isReport: Boolean = false, isCleanup: Boolean = false) {
        val userRef = usersCollection.document(userId)
        val snapshot = userRef.get().await()
        val profile = snapshot.toObject(UserProfile::class.java) ?: UserProfile(uid = userId)
        
        val now = System.currentTimeMillis()
        val lastActivity = profile.lastActivityTimestamp
        
        var newStreak = profile.streak
        if (lastActivity != 0L) {
            val diff = now - lastActivity
            val daysDiff = TimeUnit.MILLISECONDS.toDays(diff)
            
            if (daysDiff == 1L) {
                newStreak += 1
            } else if (daysDiff > 1L) {
                newStreak = 1
            }
        } else {
            newStreak = 1
        }

        val updates = mutableMapOf<String, Any>(
            "ecoKarma" to FieldValue.increment(karmaPoints.toLong()),
            "lastActivityTimestamp" to now,
            "streak" to newStreak,
            "updatedAt" to now
        )
        
        if (isReport) updates["reportsCount"] = FieldValue.increment(1)
        if (isCleanup) updates["cleanupsCount"] = FieldValue.increment(1)
        
        userRef.set(updates, SetOptions.merge()).await()
    }

    fun observeLeaderboard(onUpdate: (List<UserProfile>) -> Unit) {
        usersCollection
            .orderBy("ecoKarma", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                val users = snapshot?.toObjects(UserProfile::class.java) ?: emptyList()
                onUpdate(users)
            }
    }

    suspend fun updateRegion(region: String?) {
        val userId = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "assignedRegion" to region,
            "updatedAt" to System.currentTimeMillis()
        )
        usersCollection.document(userId).set(updates, SetOptions.merge()).await()
    }

    suspend fun updateRole(role: String) {
        val userId = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "role" to role,
            "updatedAt" to System.currentTimeMillis()
        )
        usersCollection.document(userId).set(updates, SetOptions.merge()).await()
    }

    fun observeReports(onUpdate: (List<Report>) -> Unit) {
        reportsCollection.addSnapshotListener { snapshot, _ ->
            val reports = snapshot?.toObjects(Report::class.java) ?: emptyList()
            onUpdate(reports)
        }
    }

    fun observeUserProfile(userId: String, onUpdate: (UserProfile?) -> Unit) {
        usersCollection.document(userId).addSnapshotListener { snapshot, _ ->
            onUpdate(snapshot?.toObject(UserProfile::class.java))
        }
    }
}
