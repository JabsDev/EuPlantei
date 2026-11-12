package br.com.jabsdev.euplantei.domain

import br.com.jabsdev.euplantei.domain.model.DiseaseInfo
import br.com.jabsdev.euplantei.domain.model.KnowledgeBase
import br.com.jabsdev.euplantei.domain.model.PlantProfile
import br.com.jabsdev.euplantei.domain.model.RetrievalResult
import br.com.jabsdev.euplantei.domain.model.RetrievedDisease
import java.text.Normalizer
import java.util.Locale

/**
 * Mini-RAG local: casa a espécie informada (com ou sem acento) e palavras do
 * sintoma contra os perfis do plants.json. Não usa embeddings nem rede.
 *
 * Regras:
 * - no máximo 2 perfis;
 * - no máximo 3 doenças, priorizando sobreposição com o sintoma;
 * - sem correspondência de espécie, não inventa perfil.
 */
class KnowledgeRetriever {
    private val stopWords = setOf(
        "de", "da", "do", "das", "dos", "com", "sem", "para", "por", "que", "uma", "uns",
        "umas", "nos", "nas", "pelo", "pela", "aos", "das", "como", "mais", "muito",
        "esta", "estao", "tem", "ter", "foi", "ser", "sua", "seu", "minha", "meu",
    )
    private val genericSymptomWords = setOf(
        "folha", "folhas", "planta", "plantas", "mancha", "manchas", "lesao", "lesoes",
        "problema", "sintoma", "sintomas", "esta", "estao", "ficando", "muito", "pouco",
    )

    fun retrieve(base: KnowledgeBase, species: String, symptom: String): RetrievalResult {
        val normalizedSpecies = normalize(species)
        val speciesTokens = tokens(species)
        val symptomTokens = tokens(symptom)

        val scoredProfiles = base.plantas.mapNotNull { profile ->
            val score = profileScore(profile, normalizedSpecies, speciesTokens, symptomTokens)
            if (score > 0) profile to score else null
        }.sortedByDescending { it.second }.take(MAX_PROFILES)

        if (scoredProfiles.isEmpty()) {
            return RetrievalResult()
        }

        val relevantDiseases = scoredProfiles.flatMap { (profile, _) ->
            profile.doencasComuns.map { disease ->
                Triple(profile, disease, diseaseScore(disease, symptomTokens))
            }
        }.sortedByDescending { it.third }
            .filter { it.third > 0 }
            .take(MAX_DISEASES)
            .map { (profile, disease, _) ->
                RetrievedDisease(
                    profileId = profile.id,
                    profileName = profile.nomes.firstOrNull() ?: profile.id,
                    disease = disease,
                )
            }

        val profiles = scoredProfiles.map { it.first }
        val fallbackDiseases = if (relevantDiseases.isEmpty()) {
            profiles.flatMap { profile ->
                profile.doencasComuns.take(2).map { disease ->
                    RetrievedDisease(
                        profileId = profile.id,
                        profileName = profile.nomes.firstOrNull() ?: profile.id,
                        disease = disease,
                    )
                }
            }.take(MAX_DISEASES)
        } else {
            relevantDiseases
        }

        return RetrievalResult(
            profiles = profiles,
            diseases = fallbackDiseases,
        )
    }

    fun matchSpecies(base: KnowledgeBase, species: String): PlantProfile? {
        val normalized = normalize(species)
        if (normalized.isBlank()) return null
        return base.plantas.firstOrNull { profile ->
            profile.nomes.any { name ->
                val candidate = normalize(name)
                candidate == normalized ||
                    (candidate.length >= 4 && normalized.contains(candidate)) ||
                    (normalized.length >= 4 && candidate.contains(normalized))
            } || normalize(profile.id) == normalized
        }
    }

    private fun profileScore(
        profile: PlantProfile,
        normalizedSpecies: String,
        speciesTokens: Set<String>,
        symptomTokens: Set<String>,
    ): Int {
        var score = 0
        val names = profile.nomes.map(::normalize) + normalize(profile.id)
        if (names.any { it == normalizedSpecies && it.isNotBlank() }) score += 100
        if (names.any { name ->
                name.length >= 4 && normalizedSpecies.length >= 4 &&
                    (normalizedSpecies.contains(name) || name.contains(normalizedSpecies))
            }
        ) {
            score += 50
        }
        val nameTokens = names.flatMap(::tokens).toSet()
        score += speciesTokens.intersect(nameTokens).size * 20
        // Nome da planta citado no sintoma também ajuda (ex.: "minha rosa está...").
        if (names.any { name -> name.length >= 4 && symptomTokens.any { token -> name.contains(token) } }) {
            score += 15
        }
        return score
    }

    private fun diseaseScore(disease: DiseaseInfo, symptomTokens: Set<String>): Int {
        if (symptomTokens.isEmpty()) return 0
        val diseaseTokens = tokens(disease.nome) + tokens(disease.sintomas) + tokens(disease.manejo)
        val meaningful = diseaseTokens.filterNot { it in genericSymptomWords }.toSet()
        return symptomTokens.count { it in meaningful }
    }

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return decomposed.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun tokens(value: String): Set<String> =
        normalize(value)
            .split(' ')
            .asSequence()
            .filter { it.length >= 4 && it !in stopWords }
            .toSet()

    companion object {
        private const val MAX_PROFILES = 2
        private const val MAX_DISEASES = 3
    }
}
