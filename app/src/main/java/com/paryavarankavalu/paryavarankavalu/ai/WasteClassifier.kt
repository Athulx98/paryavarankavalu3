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
    private const val GEMINI_TIMEOUT_MS = 12000L
    private const val GEMINI_DEFAULT_CONFIDENCE = 0.65f
    private const val GEMINI_MIN_CONFIDENCE = 0.50f

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
            if (!ModelLoader.hasAsset(context, ModelLoader.WASTE_MODEL_FILE)) {
                Log.d(TAG, "TFLite waste model missing; using fallbacks")
                return null
            }
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
                    text(
                        """
                        You are an expert waste classifier. Analyze the image and identify waste materials.

                        WASTE CATEGORIES AND EXAMPLES:
                        - Plastic: bottles, bags, containers, wrappers, packaging, straws, toys
                        - Glass: bottles, jars, broken glass, mirrors
                        - Metal: cans, foil, scrap metal, wire
                        - Paper: cardboard, newspaper, magazines, boxes, cartons
                        - Organic: food scraps, fruit or vegetable peels, leaves, grass, branches
                        - E-Waste: electronics, phones, computers, batteries, cables
                        - Hazardous: chemicals, medical waste, paint, oil containers

                        INSTRUCTIONS:
                        1. Identify the primary waste type, meaning the most prominent waste in the image.
                        2. Rate your confidence from 0 to 100 percent.
                        3. If confidence is below 50 percent, output Not detected.

                        RESPOND IN EXACT FORMAT:
                        Category: [Plastic, Glass, Metal, Paper, Organic, E-Waste, Hazardous, or Not detected]
                        Confidence: [number]%
                        """.trimIndent()
                    )
                },
                safetySettings = listOf(
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HARASSMENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HATE_SPEECH, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.SEXUALLY_EXPLICIT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                    com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.DANGEROUS_CONTENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE)
                )
            )

            val response = withTimeoutOrNull(GEMINI_TIMEOUT_MS) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text("Identify the waste category using the required response format.")
                    }
                )
            }

            val rawText = response?.text.orEmpty()
            val parsed = parseGeminiWasteResult(rawText) ?: return null
            val (category, confidence) = parsed

            if (category == PredictionResult.NOT_DETECTED) null else PredictionResult(
                category = category,
                confidence = confidence,
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
            Log.d(TAG, "ML Kit waste labels: ${labels.joinToString { "${it.first}=${it.second}" }}")
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

    private fun parseGeminiWasteResult(rawText: String): Pair<String, Float>? {
        if (rawText.isBlank()) return null

        val category = LabelMappingUtils.normalizeWasteCategory(extractGeminiCategory(rawText))
        if (category == PredictionResult.NOT_DETECTED) return null

        val confidence = extractGeminiConfidence(rawText) ?: GEMINI_DEFAULT_CONFIDENCE
        if (confidence < GEMINI_MIN_CONFIDENCE) return null

        Log.d(TAG, "Gemini waste result: $category ($confidence)")
        return category to confidence.coerceIn(0f, 1f)
    }

    private fun extractGeminiCategory(rawText: String): String {
        val categoryPattern = Regex("(?im)^\\s*category\\s*:\\s*([A-Za-z -]+)")
        val jsonPattern = Regex("(?i)\"?primary_category\"?\\s*:\\s*\"?([A-Za-z -]+)\"?")
        val knownCategories = listOf(PredictionResult.NOT_DETECTED) + LabelMappingUtils.allCategories()

        return categoryPattern.find(rawText)?.groupValues?.getOrNull(1)
            ?: jsonPattern.find(rawText)?.groupValues?.getOrNull(1)
            ?: knownCategories.firstOrNull { rawText.contains(it, ignoreCase = true) }
            ?: rawText.lines().firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun extractGeminiConfidence(rawText: String): Float? {
        val match = Regex("(?i)confidence\\s*:?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%?")
            .find(rawText)
            ?: return null
        val value = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: return null
        return if (value > 1f) value / 100f else value
    }
}
