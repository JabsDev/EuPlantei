package br.com.jabsdev.euplantei.domain

import br.com.jabsdev.euplantei.domain.model.ConfidenceRules
import br.com.jabsdev.euplantei.domain.model.FriendlyLabel
import br.com.jabsdev.euplantei.domain.model.PlantPrediction
import java.text.Normalizer
import java.util.Locale

/**
 * Converte os rótulos PlantVillage (`Planta___Doença`) em texto amigável em PT-BR.
 * O mapa de labels é carregado de `labels_ptbr.json`; quando um rótulo novo aparece,
 * o parser ainda entrega uma separação razoável em vez de falhar.
 */
object LabelParser {
    private const val TRIPLE_UNDERSCORE = "___"

    fun normalizeKey(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutAccents.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }

    fun parse(rawLabel: String, friendlyMap: Map<String, FriendlyLabel>): PlantPrediction {
        val normalized = normalizeKey(rawLabel)
        val friendly = friendlyMap[normalized] ?: fallback(rawLabel)
        return PlantPrediction(
            rawLabel = rawLabel,
            species = friendly.species,
            speciesId = friendly.speciesId,
            condition = friendly.condition,
            healthy = friendly.healthy,
            confidence = 0f,
        )
    }

    fun withConfidence(rawLabel: String, confidence: Float, friendlyMap: Map<String, FriendlyLabel>): PlantPrediction =
        parse(rawLabel, friendlyMap).copy(confidence = confidence.coerceIn(0f, 1f))

    fun topPredictions(
        labels: List<String>,
        probabilities: FloatArray,
        friendlyMap: Map<String, FriendlyLabel>,
        limit: Int = 3,
    ): List<PlantPrediction> {
        val count = minOf(labels.size, probabilities.size)
        return (0 until count)
            .map { index -> withConfidence(labels[index], probabilities[index], friendlyMap) }
            .sortedByDescending { it.confidence }
            .take(limit.coerceAtLeast(1))
    }

    fun confidenceLevel(confidence: Float) = ConfidenceRules.level(confidence)

    private fun fallback(rawLabel: String): FriendlyLabel {
        val cleaned = rawLabel.replace('_', ' ').trim()
        val parts = when {
            rawLabel.contains(TRIPLE_UNDERSCORE) -> rawLabel.split(TRIPLE_UNDERSCORE, limit = 2)
            else -> cleaned.split(Regex("\\s+"), limit = 2)
        }
        val species = parts.firstOrNull()?.trim()?.replace('_', ' ')?.ifBlank { "Planta" } ?: "Planta"
        val rawCondition = parts.getOrNull(1)?.trim()?.replace('_', ' ').orEmpty()
        val healthy = rawCondition.equals("healthy", ignoreCase = true) || rawCondition.isBlank()
        return FriendlyLabel(
            species = species,
            speciesId = normalizeKey(species),
            condition = if (healthy) "Planta saudável" else rawCondition.ifBlank { "Condição não catalogada" },
            healthy = healthy,
        )
    }
}
