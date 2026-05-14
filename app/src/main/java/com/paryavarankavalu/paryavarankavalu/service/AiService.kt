package com.paryavarankavalu.paryavarankavalu.service

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.paryavarankavalu.paryavarankavalu.ai.LabelMappingUtils
import com.paryavarankavalu.paryavarankavalu.ai.WasteDetectionHelper
import kotlinx.coroutines.withTimeoutOrNull

class AiService(private val apiKey: String, private val context: Context? = null) {
    suspend fun analyzeWaste(imageBitmap: Bitmap, isCleanup: Boolean = false): String {
        try {
            if (!isCleanup && context != null) {
                val prediction = WasteDetectionHelper.classifyWaste(context, imageBitmap)
                return prediction.category.takeIf { prediction.isDetected } ?: "General Waste"
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            
            val prompt = if (isCleanup) {
                """
                Analyze this image. Your only task is to check for obvious garbage or waste.
                If the area looks generally clean, empty, or normal (like an empty floor, street, or desk without trash), return 'Site Cleaned'.
                If and only if you see a clear pile of untreated garbage, litter, or waste, return 'Needs More Cleaning'.
                
                Return EXACTLY ONE of these phrases:
                - Site Cleaned
                - Needs More Cleaning
                
                Return ONLY the phrase. No other text.
                """.trimIndent()
            } else {
                """
                You are an expert environmental waste auditor. 
                Analyze this image and identify the dominant type of waste.
                Categorize it into exactly one of these labels: Plastic Waste, Bio Waste, Electronic Waste, Hazardous Waste, or General Waste.
                
                Rules:
                - Return ONLY the category name as a single phrase.
                - If unsure, return 'General Waste'.
                - Plastic Waste: bottles, bags, wrappers.
                - Bio Waste: food, plants, paper.
                - Electronic Waste: electronics, batteries, cables.
                - Hazardous Waste: chemicals, medical waste, oils.
                """.trimIndent()
            }
            
            val response = withTimeoutOrNull(15000L) {
                generativeModel.generateContent(
                    content {
                        image(imageBitmap)
                        text(prompt)
                    }
                )
            }
            if (isCleanup) {
                return response?.text?.trim()?.takeIf { it.contains("Site Cleaned", true) }?.let { "Site Cleaned" } ?: "Needs More Cleaning"
            }
            return response?.text?.trim()?.replace(Regex("[^A-Za-z -]"), "") ?: "General Waste"
        } catch (e: Exception) {
            e.printStackTrace()
            return if (isCleanup) "Needs More Cleaning" else "General Waste"
        }
    }

    fun determinePriority(wasteType: String): String {
        return when (LabelMappingUtils.normalizeWasteCategory(wasteType)) {
            "Hazardous" -> "High"
            "E-Waste", "Organic" -> "Medium"
            "Glass", "Metal" -> "Medium"
            else -> "Low"
        }
    }
}
