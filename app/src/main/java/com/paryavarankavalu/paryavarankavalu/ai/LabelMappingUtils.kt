package com.paryavarankavalu.paryavarankavalu.ai

/**
 * Utility object for converting raw model labels into waste categories.
 *
 * This is a pure mapping utility — it does NOT call ML Kit or any AI service.
 * Feed it the raw label strings that ML Kit returns and it will tell you:
 *   - Which waste category each label maps to
 *   - What the best overall category is, given a list of label+confidence pairs
 *   - Whether a set of labels contains waste-related objects
 */
object LabelMappingUtils {

    // ── Category keyword map (UNIQUE per category, no overlaps) ──────────
    private val categoryMap: Map<String, List<String>> = mapOf(
        "Plastic" to listOf(
            "Plastic", "Bottle", "Bag", "Toy", "Container", "Cup", "Wrapper",
            "Packaging", "Liquid", "Beverage", "Drink", "Tub", "Jug", "Bucket",
            "Syringe", "Straw", "Polyethylene", "Polypropylene", "PET", "HDPE"
        ),
        "Organic" to listOf(
            "Food", "Fruit", "Vegetable", "Plant", "Leaf", "Wood", "Animal",
            "Meat", "Peel", "Dirt", "Tree", "Grass", "Flower", "Soil",
            "Produce", "Compost", "Branch", "Garden", "Agricultural"
        ),
        "Glass" to listOf(
            "Glass", "Jar", "Window", "Mirror", "Eyewear",
            "Glassware", "Crystal", "Porcelain", "Ceramic"
        ),
        "Metal" to listOf(
            "Metal", "Can", "Aluminium", "Iron", "Steel",
            "Scrap", "Foil", "Tin", "Copper", "Rust", "Metallic"
        ),
        "Paper" to listOf(
            "Paper", "Cardboard", "Carton", "Box", "Newspaper", "Book",
            "Magazine", "Notebook", "Tissue", "Napkin"
        ),
        "E-Waste" to listOf(
            "Electronic", "Computer", "Laptop", "Phone", "Screen", "Cable",
            "Charger", "Circuit", "Keyboard", "Mouse", "Appliance", "E-Waste"
        ),
        "Hazardous" to listOf(
            "Battery", "Electronic", "Medical", "Chemical", "Paint",
            "Pill", "Medicine", "Hazardous", "Toxic", "Oil", "Solvent",
            "Clinical", "Sanitary", "Pesticide"
        )
    )

    // Labels that indicate waste/rubbish objects (used for cleanup verification)
    private val wasteIndicators = listOf(
        "Waste", "Garbage", "Trash", "Plastic", "Debris", "Food",
        "Bottle", "Can", "Paper", "Container", "Box", "Pack", "Wrapper",
        "Cup", "Bag", "Junk", "Litter", "Cardboard", "Scrap", "Rubble"
    )

    /**
     * Maps a single raw label string to a waste category.
     * Returns null if the label doesn't match any known category.
     *
     * Example: mapLabelToCategory("Plastic bottle") → "Plastic"
     */
    fun mapLabelToCategory(label: String): String? {
        for ((category, keywords) in categoryMap) {
            if (keywords.any { label.contains(it, ignoreCase = true) }) {
                return category
            }
        }
        return null
    }

    /**
     * Given a list of (labelText, confidence) pairs returned by ML Kit,
     * returns the best waste category using score-accumulation voting.
     *
     * Each matching label adds its confidence to its category's total.
     * The category with the highest accumulated score wins.
     *
     * Returns "Not detected" if nothing matches.
     */
    fun bestCategory(labels: List<Pair<String, Float>>, minConfidence: Float = 0.05f): String {
        return bestCategoryWithConfidence(labels, minConfidence)?.category ?: "Not detected"
    }

    fun bestCategoryWithConfidence(labels: List<Pair<String, Float>>, minConfidence: Float = 0.05f): PredictionResult? {
        val scores = mutableMapOf<String, Float>()

        for ((text, confidence) in labels) {
            if (confidence < minConfidence) continue
            for ((category, keywords) in categoryMap) {
                if (keywords.any { text.contains(it, ignoreCase = true) }) {
                    scores[category] = (scores[category] ?: 0f) + confidence
                }
            }
        }

        val best = scores.maxByOrNull { it.value } ?: return null
        val normalized = normalizeWasteCategory(best.key)
        return PredictionResult(
            category = normalized,
            confidence = best.value.coerceIn(0f, 1f),
            recommendedBin = recommendedBinFor(normalized)
        )
    }

    /**
     * Checks whether any of the given label strings indicate waste/rubbish.
     * Used by CleaningVerificationHelper to decide if an image contains waste.
     */
    fun containsWaste(labels: List<Pair<String, Float>>, minConfidence: Float = 0.2f): Boolean {
        return labels.any { (text, confidence) ->
            confidence > minConfidence &&
                wasteIndicators.any { text.contains(it, ignoreCase = true) }
        }
    }

    /**
     * Extracts only the label strings that are related to waste/rubbish.
     * Useful for storing detected waste labels in the database.
     */
    fun filterWasteLabels(labels: List<Pair<String, Float>>, minConfidence: Float = 0.1f): List<String> {
        return labels
            .filter { (text, confidence) ->
                confidence >= minConfidence &&
                    wasteIndicators.any { text.contains(it, ignoreCase = true) }
            }
            .map { it.first }
    }

    /** Returns all supported waste categories. */
    fun allCategories(): List<String> = categoryMap.keys.toList()

    /** Returns all keywords for a given category, or empty list if unknown. */
    fun keywordsFor(category: String): List<String> = categoryMap[category] ?: emptyList()

    fun normalizeWasteCategory(rawLabel: String): String {
        val cleaned = rawLabel.trim()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")

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
}
