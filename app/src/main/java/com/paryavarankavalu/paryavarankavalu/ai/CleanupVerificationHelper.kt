package com.paryavarankavalu.paryavarankavalu.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.paryavarankavalu.paryavarankavalu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

object CleanupVerificationHelper {
    private const val TAG = "CleanupVerify"

    suspend fun verifyCleaningResultWithUrl(
        context: Context,
        beforeImageUrl: String?,
        afterBitmap: Bitmap
    ): CleanupResult {
        val beforeBitmap = loadBeforeBitmap(beforeImageUrl)
        return verifyCleaningResult(context, beforeBitmap, afterBitmap)
    }

    suspend fun verifyCleaningResult(
        context: Context,
        beforeBitmap: Bitmap?,
        afterBitmap: Bitmap
    ): CleanupResult {
        CleanupVerifier.verifyWithTflite(context, beforeBitmap, afterBitmap)?.let {
            Log.d(TAG, "TFLite cleanup result: $it")
            return it
        }
        geminiCleanupResult(beforeBitmap, afterBitmap)?.let {
            Log.d(TAG, "Gemini cleanup fallback result: $it")
            return it
        }
        return CleanupVerifier.verifyWithMlKit(beforeBitmap, afterBitmap)
    }

    /**
     * Backward-compatible API used by older call sites. It preserves the old
     * return values while still benefiting from Gemini and ML Kit fallbacks.
     */
    suspend fun verifyCleaningWithUrl(beforeImageUrl: String?, afterBitmap: Bitmap): String {
        val beforeBitmap = loadBeforeBitmap(beforeImageUrl)
        return verifyCleaning(beforeBitmap, afterBitmap)
    }

    suspend fun verifyCleaning(beforeBitmap: Bitmap?, afterBitmap: Bitmap): String {
        return geminiCleanupResult(beforeBitmap, afterBitmap)?.status
            ?: CleanupVerifier.verifyWithMlKit(beforeBitmap, afterBitmap).status
    }

    private suspend fun loadBeforeBitmap(urlOrDataUri: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (urlOrDataUri.isNullOrBlank()) return@withContext null
        try {
            if (urlOrDataUri.startsWith("data:image", ignoreCase = true)) {
                val base64 = urlOrDataUri.substringAfter(",", missingDelimiterValue = "")
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            val connection = URL(urlOrDataUri).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doInput = true
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Before image load failed: ${e.message}")
            null
        }
    }

    private suspend fun geminiCleanupResult(beforeBitmap: Bitmap?, afterBitmap: Bitmap): CleanupResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "null") return null

        return try {
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = content {
                    text("You are a strict waste cleanup auditor. Estimate visible waste before and after cleanup.")
                },
                safetySettings = listOf(
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HARASSMENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HATE_SPEECH, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.SEXUALLY_EXPLICIT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.DANGEROUS_CONTENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE)
                )
            )

            val response = withTimeoutOrNull(14000L) {
                model.generateContent(
                    content {
                        if (beforeBitmap != null) image(beforeBitmap)
                        image(afterBitmap)
                        text(
                            """
                            Return JSON only:
                            {"beforeWasteLevel":0-100,"afterWasteLevel":0-100,"cleanupVerified":true|false}
                            If no before image is provided, estimate only the after image and set beforeWasteLevel to 0.
                            """.trimIndent()
                        )
                    }
                )
            }

            parseGeminiCleanup(response?.text)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini cleanup fallback failed: ${e.message}")
            null
        }
    }

    private fun parseGeminiCleanup(text: String?): CleanupResult? {
        if (text.isNullOrBlank()) return null
        val before = Regex("\"beforeWasteLevel\"\\s*:\\s*(\\d+(?:\\.\\d+)?)")
            .find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val after = Regex("\"afterWasteLevel\"\\s*:\\s*(\\d+(?:\\.\\d+)?)")
            .find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val verified = Regex("\"cleanupVerified\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true)

        if (after != null) {
            val result = if (before != null && before > 0f) {
                CleanupResult.fromLevels(before / 100f, after / 100f, source = "Gemini")
            } else {
                CleanupResult.afterOnly(after / 100f, source = "Gemini")
            }
            return if (verified == null) {
                result
            } else {
                result.copy(
                    cleanupVerified = verified,
                    status = if (verified) "Cleaned" else "Not Cleaned"
                )
            }
        }

        return when {
            text.contains("not cleaned", ignoreCase = true) -> CleanupResult.afterOnly(0.75f, "Gemini")
            text.contains("cleaned", ignoreCase = true) -> CleanupResult.afterOnly(0.12f, "Gemini")
            else -> null
        }
    }
}
