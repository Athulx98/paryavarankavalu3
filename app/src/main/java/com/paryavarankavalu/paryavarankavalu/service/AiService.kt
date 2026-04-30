package com.paryavarankavalu.paryavarankavalu.service

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class AiService(private val apiKey: String) {
    suspend fun analyzeWaste(imageBitmap: Bitmap): String {
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            
            val prompt = """
                You are an expert environmental waste auditor. 
                Analyze this image and identify the dominant type of waste.
                Categorize it into exactly one of these labels: Plastic, Organic, E-Waste, Hazardous, or General.
                
                Rules:
                - Return ONLY the category name as a single word.
                - If unsure, return 'General'.
                - Plastic: bottles, bags, wrappers, etc.
                - Organic: food, plants, paper (if compostable).
                - E-Waste: electronics, batteries, cables.
                - Hazardous: chemicals, medical waste, oils.
            """.trimIndent()
            
            val response = generativeModel.generateContent(
                content {
                    image(imageBitmap)
                    text(prompt)
                }
            )
            return response.text?.trim()?.replace(Regex("[^A-Za-z-]"), "") ?: "General"
        } catch (e: Exception) {
            e.printStackTrace()
            return "General"
        }
    }
}