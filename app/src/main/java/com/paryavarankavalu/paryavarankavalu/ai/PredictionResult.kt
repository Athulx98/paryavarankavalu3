package com.paryavarankavalu.paryavarankavalu.ai

data class PredictionResult(
    val category: String,
    val confidence: Float,
    val recommendedBin: String,
    val inferenceTimeMs: Long = 0L
) {
    val isDetected: Boolean
        get() = category != NOT_DETECTED && confidence > 0f

    companion object {
        const val NOT_DETECTED = "Not detected"

        fun notDetected(): PredictionResult = PredictionResult(
            category = NOT_DETECTED,
            confidence = 0f,
            recommendedBin = "Manual Review"
        )
    }
}
