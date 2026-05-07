package com.paryavarankavalu.paryavarankavalu.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

object WasteDetectionHelper {
    private const val TAG = "WasteDetection"

    suspend fun classifyWaste(context: Context, bitmap: Bitmap): PredictionResult {
        return try {
            WasteClassifier.classify(context, bitmap).also {
                Log.d(TAG, "Hybrid waste result: ${it.category}, confidence=${it.confidence}, bin=${it.recommendedBin}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Waste detection failed: ${e.message}", e)
            PredictionResult.notDetected()
        }
    }

    suspend fun suggestWasteCategory(context: Context, bitmap: Bitmap): String {
        return classifyWaste(context, bitmap).category
    }

    suspend fun suggestWasteCategory(bitmap: Bitmap): String {
        return WasteClassifier.classifyWithGemini(bitmap)?.category
            ?: WasteClassifier.classifyWithMlKit(bitmap)?.category
            ?: PredictionResult.NOT_DETECTED
    }

    fun release() {
        WasteClassifier.release()
    }
}
