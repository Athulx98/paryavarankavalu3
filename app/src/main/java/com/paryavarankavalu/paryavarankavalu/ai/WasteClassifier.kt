package com.paryavarankavalu.paryavarankavalu.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.paryavarankavalu.paryavarankavalu.BuildConfig
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object WasteClassifier {
    private const val TAG = "WasteClassifier"

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    @Volatile private var tfliteClassifier: TFLiteWasteClassifier? = null

    suspend fun classify(context: Context, bitmap: Bitmap): PredictionResult {
        classifyWithTflite(context, bitmap)?.takeIf { it.isDetected }?.let { return it }
        classifyWithGemini(bitmap)?.takeIf { it.isDetected }?.let { return it }
        return classifyWithMlKit(bitmap) ?: PredictionResult.notDetected()
    }

    suspend fun classifyWithTflite(context: Context, bitmap: Bitmap): PredictionResult? {
        return try {
            if (!ModelLoader.hasAsset(context, ModelLoader.WASTE_MODEL_FILE)) return null
            getTfliteClassifier(context).classify(bitmap).also {
                Log.d(TAG, "TFLite waste result: ${it.category} (${it.confidence})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TFLite waste classifier failed, falling back: ${e.message}")
            null
        }
    }

    suspend fun classifyWithGemini(bitmap: Bitmap): PredictionResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "null") return null

        return try {
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = content {
                    text("Classify the dominant waste in the image. Return only one category: Plastic, Glass, Metal, Paper, Organic, E-Waste, Hazardous, or Not detected.")
                },
                safetySettings = listOf(
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HARASSMENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HATE_SPEECH, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.SEXUALLY_EXPLICIT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.DANGEROUS_CONTENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE)
                )
            )

            val response = withTimeoutOrNull(12000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text("Identify the waste category.")
                    }
                )
            }

            val category = LabelMappingUtils.normalizeWasteCategory(
                response?.text?.trim()?.replace("*", "").orEmpty()
            )
            if (category == PredictionResult.NOT_DETECTED) null else PredictionResult(
                category = category,
                confidence = 0.65f,
                recommendedBin = LabelMappingUtils.recommendedBinFor(category)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Gemini waste fallback failed: ${e.message}")
            null
        }
    }

    suspend fun classifyWithMlKit(bitmap: Bitmap): PredictionResult? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(image).await().map { it.text to it.confidence }
            LabelMappingUtils.bestCategoryWithConfidence(labels, minConfidence = 0.01f)
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit waste fallback failed: ${e.message}")
            null
        }
    }

    fun release() {
        tfliteClassifier?.close()
        tfliteClassifier = null
    }

    private fun getTfliteClassifier(context: Context): TFLiteWasteClassifier {
        return tfliteClassifier ?: synchronized(this) {
            tfliteClassifier ?: TFLiteWasteClassifier(context.applicationContext).also { tfliteClassifier = it }
        }
    }
}
