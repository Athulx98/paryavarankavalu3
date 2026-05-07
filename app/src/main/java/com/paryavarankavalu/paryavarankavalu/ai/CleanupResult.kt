package com.paryavarankavalu.paryavarankavalu.ai

data class CleanupResult(
    val beforeWasteLevel: Float,
    val afterWasteLevel: Float,
    val improvement: Float,
    val cleanlinessScore: Float,
    val cleanupScore: Float,
    val cleanupVerified: Boolean,
    val status: String,
    val source: String
) {
    companion object {
        fun fromLevels(beforeWasteLevel: Float, afterWasteLevel: Float, source: String): CleanupResult {
            val before = beforeWasteLevel.coerceIn(0f, 1f)
            val after = afterWasteLevel.coerceIn(0f, 1f)
            val improvement = (before - after).coerceIn(0f, 1f)
            val cleanliness = (1f - after).coerceIn(0f, 1f)
            val score = ((improvement * 0.65f) + (cleanliness * 0.35f)).coerceIn(0f, 1f)
            val verified = (before >= 0.35f && improvement >= 0.35f && after <= 0.45f) || score >= 0.72f
            return CleanupResult(
                beforeWasteLevel = before,
                afterWasteLevel = after,
                improvement = improvement,
                cleanlinessScore = cleanliness,
                cleanupScore = score,
                cleanupVerified = verified,
                status = if (verified) "Cleaned" else "Not Cleaned",
                source = source
            )
        }

        fun afterOnly(afterWasteLevel: Float, source: String): CleanupResult {
            val after = afterWasteLevel.coerceIn(0f, 1f)
            val cleanliness = (1f - after).coerceIn(0f, 1f)
            val verified = after <= 0.28f
            return CleanupResult(
                beforeWasteLevel = 0f,
                afterWasteLevel = after,
                improvement = 0f,
                cleanlinessScore = cleanliness,
                cleanupScore = cleanliness,
                cleanupVerified = verified,
                status = if (verified) "Cleaned" else "Not Cleaned",
                source = source
            )
        }
    }
}
