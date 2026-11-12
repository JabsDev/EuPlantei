package br.com.jabsdev.euplantei.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlantProfile(
    val id: String,
    val nomes: List<String> = emptyList(),
    val luz: String = "",
    @SerialName("rega_por_faixa_umidade") val rega: String = "",
    @SerialName("intervalo_rega_dias") val intervaloRegaDias: Int? = null,
    @SerialName("faixa_temperatura") val faixaTemperatura: String = "",
    val solo: String = "",
    val poda: String = "",
    val adubacao: String = "",
    @SerialName("doencas_comuns") val doencasComuns: List<DiseaseInfo> = emptyList(),
    @SerialName("toxicidade_pet") val toxicidadePet: String = "",
)

@Serializable
data class DiseaseInfo(
    val nome: String,
    val sintomas: String,
    val manejo: String,
)

@Serializable
data class KnowledgeBase(
    val plantas: List<PlantProfile> = emptyList(),
)

data class RetrievedDisease(
    val profileId: String,
    val profileName: String,
    val disease: DiseaseInfo,
)

data class RetrievalResult(
    val profiles: List<PlantProfile> = emptyList(),
    val diseases: List<RetrievedDisease> = emptyList(),
) {
    val hasData: Boolean get() = profiles.isNotEmpty() || diseases.isNotEmpty()

    fun toPromptText(): String {
        if (!hasData) {
            return "sem dados específicos para esta espécie; use conhecimento geral com cautela"
        }
        val builder = StringBuilder()
        profiles.forEach { profile ->
            builder.appendLine("Perfil: ${profile.nomes.firstOrNull() ?: profile.id} (${profile.nomes.drop(1).joinToString(", ")})")
            builder.appendLine("- Luz: ${profile.luz}")
            builder.appendLine("- Rega: ${profile.rega}")
            profile.intervaloRegaDias?.let {
                builder.appendLine("- Frequência de rega recomendada: a cada $it dias")
            }
            builder.appendLine("- Temperatura: ${profile.faixaTemperatura}")
            builder.appendLine("- Solo: ${profile.solo}")
            if (profile.poda.isNotBlank()) builder.appendLine("- Poda: ${profile.poda}")
            if (profile.adubacao.isNotBlank()) builder.appendLine("- Adubação: ${profile.adubacao}")
            if (profile.toxicidadePet.isNotBlank()) {
                builder.appendLine("- Toxicidade para pets: ${profile.toxicidadePet}")
            }
            builder.appendLine()
        }
        if (diseases.isNotEmpty()) {
            builder.appendLine("Doenças e problemas relevantes da base local:")
            diseases.forEach { item ->
                builder.appendLine("- ${item.disease.nome} (${item.profileName})")
                builder.appendLine("  Sintomas: ${item.disease.sintomas}")
                builder.appendLine("  Manejo: ${item.disease.manejo}")
            }
        }
        return builder.toString().trim()
    }
}
