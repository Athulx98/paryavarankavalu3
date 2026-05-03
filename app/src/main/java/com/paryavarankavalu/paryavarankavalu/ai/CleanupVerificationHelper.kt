package com.paryavarankavalu.paryavarankavalu.ai

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.paryavarankavalu.paryavarankavalu.BuildConfig
import kotlinx.coroutines.tasks.await

/**
 * Helper class for verifying whether an area has been cleaned.
 * Uses a hybrid approach:
 * 1. Gemini 1.5 Flash for high-accuracy visual comparison (if API key is available).
 * 2. ML Kit Object Detection as an offline fallback.
 */
object CleanupVerificationHelper {

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    private val objectDetector = ObjectDetection.getClient(options)

    /**
     * Verifies if the area in the afterBitmap is clean.
     * Can optionally download a 'before' image from a URL for comparison.
     */
    suspend fun verifyCleaningWithUrl(beforeImageUrl: String?, afterBitmap: Bitmap): String {
        return try {
            val beforeBitmap = if (!beforeImageUrl.isNullOrEmpty()) {
                downloadBitmap(beforeImageUrl)
            } else {
                null
            }
            verifyCleaning(beforeBitmap, afterBitmap)
        } catch (e: Exception) {
            android.util.Log.e("CleanupVerify", "Url download failed: ${e.message}")
            verifyCleaning(null, afterBitmap)
        }
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            android.graphics.BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verifies if the area in the afterBitmap is clean.
     * If beforeBitmap is provided, it performs a comparison.
     */
    suspend fun verifyCleaning(beforeBitmap: Bitmap?, afterBitmap: Bitmap): String {
        return try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            android.util.Log.d("CleanupVerify", "Attempting AI verification. Key present: ${apiKey.isNotEmpty()}")
            
            if (apiKey.isEmpty() || apiKey == "null") {
                return fallbackToMlKit(beforeBitmap, afterBitmap)
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = content {
                    text("You are a waste cleanup auditor. Examine the before/after images and determine if the cleanup was successful. Return ONLY 'Cleaned' or 'Not Cleaned'.")
                },
                safetySettings = listOf(
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HARASSMENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HATE_SPEECH, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.SEXUALLY_EXPLICIT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.DANGEROUS_CONTENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE)
                )
            )

            val response = generativeModel.generateContent(
                content {
                    if (beforeBitmap != null) image(beforeBitmap)
                    image(afterBitmap)
                    text("Compare these images. Is the area now clean?")
                }
            )

            val result = response.text?.trim()?.replace("*", "") ?: "Not Cleaned"
            android.util.Log.d("CleanupVerify", "Gemini Result: $result")
            
            if (result.contains("Cleaned", ignoreCase = true) && !result.contains("Not", ignoreCase = true)) {
                "Cleaned"
            } else if (result.contains("Not Cleaned", ignoreCase = true)) {
                "Not Cleaned"
            } else {
                "Cleaned" // Default to Cleaned if result is ambiguous
            }
        } catch (e: Exception) {
            android.util.Log.e("CleanupVerify", "AI Error: ${e.message}")
            fallbackToMlKit(beforeBitmap, afterBitmap)
        }
    }

    private suspend fun fallbackToMlKit(beforeBitmap: Bitmap?, afterBitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(afterBitmap, 0)
            val labels = objectDetector.process(image).await()
            val labelPairs = labels.flatMap { obj -> obj.labels.map { it.text to it.confidence } }
            // Highly sensitive threshold for local cleanup verification
            val hasWaste = LabelMappingUtils.containsWaste(labelPairs, minConfidence = 0.1f)

            if (!hasWaste) "Cleaned" else "Not Cleaned"
        } catch (e: Exception) {
            "Not Cleaned"
        }
    }
}
