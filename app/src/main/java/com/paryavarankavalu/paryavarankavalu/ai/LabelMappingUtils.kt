package com.paryavarankavalu.paryavarankavalu.ai

/**
 * Utility object for converting raw model labels into waste categories.
 *
 * This is a pure mapping utility; it does not call ML Kit or any AI service.
 */
object LabelMappingUtils {

    private const val MIN_WEIGHTED_SCORE = 0.15f

    private val categoryMap: Map<String, List<String>> = mapOf(
        "Plastic" to listOf(
            "Plastic", "Bottle", "Bag", "Toy", "Wrapper", "Packaging",
            "Packet", "Sachet", "Straw", "Polyethylene", "Polypropylene", "PET", "HDPE"
        ),
        "Organic" to listOf(
            "Organic", "Biodegradable", "Food", "Fruit", "Vegetable", "Plant", "Leaf", "Leaves", "Wood", "Animal",
            "Meat", "Peel", "Dirt", "Tree", "Grass", "Flower", "Soil",
            "Produce", "Compost", "Branch", "Garden", "Agricultural"
        ),
        "Glass" to listOf(
            "Glass", "Jar", "Window", "Mirror", "Eyewear",
            "Glassware", "Crystal", "Porcelain", "Ceramic"
        ),
        "Metal" to listOf(
            "Metal", "Can", "Aluminium", "Aluminum", "Iron", "Steel",
            "Scrap", "Foil", "Tin", "Copper", "Rust", "Metallic", "Wire"
        ),
        "Paper" to listOf(
            "Paper", "Cardboard", "Carton", "Box", "Newspaper", "Book",
            "Magazine", "Notebook", "Tissue", "Napkin"
        ),
        "E-Waste" to listOf(
            "Electronic", "Electronics", "Computer", "Laptop", "Phone", "Screen", "Cable",
            "Charger", "Circuit", "Keyboard", "Mouse", "Appliance", "Device", "E-Waste"
        ),
        "Hazardous" to listOf(
            "Battery", "Medical", "Chemical", "Paint", "Syringe",
            "Pill", "Medicine", "Hazardous", "Toxic", "Oil", "Solvent",
            "Clinical", "Sanitary", "Pesticide"
        )
    )

    private val ambiguousKeywords = setOf(
        "Container", "Cup", "Liquid", "Beverage", "Drink", "Tub", "Jug", "Bucket"
    )

    private val materialKeywords = setOf(
        "plastic", "polyethylene", "polypropylene", "pet", "hdpe",
        "glass", "glassware", "crystal", "porcelain", "ceramic",
        "metal", "aluminium", "aluminum", "iron", "steel", "tin", "copper", "metallic",
        "paper", "cardboard", "carton",
        "electronic", "electronics", "circuit",
        "battery", "chemical", "paint", "medicine", "hazardous", "toxic", "oil", "solvent",
        "food", "fruit", "vegetable", "compost", "organic", "biodegradable"
    )

    private val weakObjectKeywords = setOf(
        "bottle", "bag", "toy", "wrapper", "packaging", "packet", "sachet", "straw",
        "jar", "window", "mirror", "eyewear", "can", "foil", "scrap", "wire",
        "box", "book", "magazine", "notebook", "tissue", "napkin",
        "computer", "laptop", "phone", "screen", "cable", "charger", "keyboard", "mouse",
        "appliance", "device"
    )

    private val specificKeywords = categoryMap.values.flatten().map { normalizeForMatch(it) }.toSet()

    private val wasteIndicators = listOf(
        "Waste", "Garbage", "Trash", "Plastic", "Debris", "Food",
        "Bottle", "Can", "Paper", "Container", "Box", "Pack", "Packaging", "Packet", "Sachet", "Wrapper",
        "Cup", "Bag", "Junk", "Litter", "Cardboard", "Scrap", "Rubble"
    )

    fun mapLabelToCategory(label: String): String? {
        val scores = scoreLabels(listOf(label to 1f), minConfidence = 0f)
        return scores.maxByOrNull { it.value }
            ?.takeIf { it.value >= MIN_WEIGHTED_SCORE }
            ?.key
    }

    fun bestCategory(labels: List<Pair<String, Float>>, minConfidence: Float = 0.05f): String {
        return bestCategoryWithConfidence(labels, minConfidence)?.category ?: PredictionResult.NOT_DETECTED
    }

    fun bestCategoryWithConfidence(labels: List<Pair<String, Float>>, minConfidence: Float = 0.05f): PredictionResult? {
        val scores = scoreLabels(labels, minConfidence)
        val best = scores.maxByOrNull { it.value } ?: return null
        if (best.value < MIN_WEIGHTED_SCORE) return null

        val normalized = normalizeWasteCategory(best.key)
        if (normalized == PredictionResult.NOT_DETECTED) return null

        return PredictionResult(
            category = normalized,
            confidence = best.value.coerceIn(0f, 1f),
            recommendedBin = recommendedBinFor(normalized)
        )
    }

    fun containsWaste(labels: List<Pair<String, Float>>, minConfidence: Float = 0.2f): Boolean {
        return labels.any { (text, confidence) ->
            confidence > minConfidence &&
                wasteIndicators.any { containsKeyword(text, it) }
        }
    }

    fun filterWasteLabels(labels: List<Pair<String, Float>>, minConfidence: Float = 0.1f): List<String> {
        return labels
            .filter { (text, confidence) ->
                confidence >= minConfidence &&
                    wasteIndicators.any { containsKeyword(text, it) }
            }
            .map { it.first }
    }

    fun allCategories(): List<String> = categoryMap.keys.toList()

    fun keywordsFor(category: String): List<String> = categoryMap[category] ?: emptyList()

    fun normalizeWasteCategory(rawLabel: String): String {
        val cleaned = rawLabel.trim()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("[*`\"'\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return when {
            cleaned.equals("bio waste", true) || cleaned.equals("biowaste", true) -> "Organic"
            cleaned.equals("organic waste", true) -> "Organic"
            cleaned.equals("plastic waste", true) -> "Plastic"
            cleaned.equals("glass waste", true) -> "Glass"
            cleaned.equals("metal waste", true) -> "Metal"
            cleaned.equals("paper waste", true) -> "Paper"
            cleaned.equals("electronic waste", true) || cleaned.equals("e waste", true) -> "E-Waste"
            cleaned.equals("hazardous waste", true) -> "Hazardous"
            allCategories().any { it.equals(cleaned, true) } -> allCategories().first { it.equals(cleaned, true) }
            else -> mapLabelToCategory(cleaned) ?: PredictionResult.NOT_DETECTED
        }
    }

    fun recommendedBinFor(category: String): String = when (normalizeWasteCategory(category)) {
        "Plastic" -> "Blue Bin"
        "Organic" -> "Green Bin"
        "Hazardous" -> "Red Bin"
        "E-Waste" -> "Black Bin"
        "Glass" -> "White Bin"
        "Metal" -> "Yellow Bin"
        "Paper" -> "Blue Bin"
        else -> "Manual Review"
    }

    private fun scoreLabels(labels: List<Pair<String, Float>>, minConfidence: Float): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()

        for ((text, confidence) in labels) {
            if (confidence < minConfidence || isOnlyAmbiguous(text)) continue

            for ((category, keywords) in categoryMap) {
                val weightedScore = keywords
                    .filter { containsKeyword(text, it) }
                    .maxOfOrNull { confidence * weightForMatch(text, it) }
                    ?: continue

                scores[category] = (scores[category] ?: 0f) + weightedScore
            }
        }

        return scores
    }

    private fun weightForMatch(text: String, keyword: String): Float {
        val normalizedText = normalizeForMatch(text)
        val normalizedKeyword = normalizeForMatch(keyword)
        val exact = normalizedText.equals(normalizedKeyword, ignoreCase = true)

        return when {
            normalizedKeyword in materialKeywords -> if (exact) 2.0f else 1.4f
            normalizedKeyword in weakObjectKeywords -> if (exact) 0.85f else 0.65f
            exact -> 2.0f
            normalizedKeyword.length > 10 -> 1.5f
            normalizedKeyword.length < 5 -> 0.7f
            else -> 1.0f
        }
    }

    private fun isOnlyAmbiguous(text: String): Boolean {
        val hasAmbiguousHint = ambiguousKeywords.any { containsKeyword(text, it) }
        if (!hasAmbiguousHint) return false

        return specificKeywords.none { containsKeyword(text, it) }
    }

    private fun containsKeyword(text: String, keyword: String): Boolean {
        val normalizedText = normalizeForMatch(text)
        val normalizedKeyword = normalizeForMatch(keyword)
        if (normalizedKeyword.isBlank()) return false

        val pattern = Regex(
            "(^|[^A-Za-z0-9])${Regex.escape(normalizedKeyword)}([^A-Za-z0-9]|$)",
            RegexOption.IGNORE_CASE
        )
        return pattern.containsMatchIn(normalizedText)
    }

    private fun normalizeForMatch(value: String): String {
        return value
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }
}
