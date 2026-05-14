package com.paryavarankavalu.paryavarankavalu

import com.paryavarankavalu.paryavarankavalu.ai.LabelMappingUtils
import com.paryavarankavalu.paryavarankavalu.ai.PredictionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelMappingUtilsTest {

    @Test
    fun aluminumCanLabelsPreferMetalOverGenericDrinkLabels() {
        val result = LabelMappingUtils.bestCategoryWithConfidence(
            listOf(
                "Beverage" to 0.92f,
                "Drink" to 0.89f,
                "Container" to 0.84f,
                "Aluminum" to 0.72f
            ),
            minConfidence = 0.01f
        )

        assertEquals("Metal", result?.category)
    }

    @Test
    fun genericContainerLabelsAreNotEnoughForPlastic() {
        val result = LabelMappingUtils.bestCategoryWithConfidence(
            listOf(
                "Beverage" to 0.92f,
                "Drink" to 0.89f,
                "Container" to 0.84f
            ),
            minConfidence = 0.01f
        )

        assertNull(result)
        assertEquals(
            PredictionResult.NOT_DETECTED,
            LabelMappingUtils.bestCategory(listOf("Container" to 0.84f), minConfidence = 0.01f)
        )
    }

    @Test
    fun materialKeywordBeatsAmbiguousBottleShape() {
        val result = LabelMappingUtils.bestCategoryWithConfidence(
            listOf("Glass bottle" to 0.90f),
            minConfidence = 0.01f
        )

        assertEquals("Glass", result?.category)
    }
}
