package com.paryavarankavalu.paryavarankavalu.ai

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.paryavarankavalu.paryavarankavalu.BuildConfig
import kotlinx.coroutines.tasks.await

object WasteDetectionHelper {

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun suggestWasteCategory(bitmap: Bitmap): String {
        return try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            android.util.Log.d("WasteDetection", "Attempting AI detection. Key present: ${apiKey.isNotEmpty()}")
            
            if (apiKey.isEmpty() || apiKey == "null") {
                return fallbackToMlKit(bitmap)
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                systemInstruction = content { 
                    text("You are a waste categorization expert. Identify the type of waste in the image and return ONLY the category name: Plastic, Organic, Glass, Metal, Hazardous, or Mixed Waste. If no waste is found, return 'Not detected'.") 
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
                    image(bitmap)
                    text("Categorize the waste in this image.")
                }
            )

            val category = response.text?.trim()?.replace("*", "") ?: "Not detected"
            android.util.Log.d("WasteDetection", "Gemini Result: $category")
            
            val validCategories = LabelMappingUtils.allCategories()
            val matched = validCategories.firstOrNull { it.equals(category, ignoreCase = true) }
            
            matched ?: fallbackToMlKit(bitmap)
        } catch (e: Exception) {
            android.util.Log.e("WasteDetection", "AI Error: ${e.message}")
            fallbackToMlKit(bitmap)
        }
    }

    private suspend fun fallbackToMlKit(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(image).await()
            val labelPairs = labels.map { it.text to it.confidence }
            // Increased sensitivity for local detection
            LabelMappingUtils.bestCategory(labelPairs, minConfidence = 0.01f)
        } catch (e: Exception) {
            "Not detected"
        }
    }
}
