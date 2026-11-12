package br.com.jabsdev.euplantei.domain

import br.com.jabsdev.euplantei.domain.model.WeatherContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class IdentificationSource {
    MANUAL,
    AUTOMATIC,
}

data class PromptInput(
    val weather: WeatherContext? = null,
    val species: String? = null,
    val identification: IdentificationSource = IdentificationSource.AUTOMATIC,
    val confidencePercent: Int? = null,
    val symptom: String? = null,
    val question: String? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val conditionDescription: String? = null,
)

/**
 * Monta o prompt exatamente no formato definido pelo produto. Fallbacks são
 * explícitos para que nenhum campo vazio vire instrução ambígua ao modelo.
 */
object PromptBuilder {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    fun build(input: PromptInput, knowledgeText: String): String {
        val weather = input.weather
        val city = weather?.city?.takeIf { it.isNotBlank() } ?: "não informado"
        val temperature = number(weather?.temperatureC, "°C") ?: "não informada"
        val apparent = number(weather?.apparentTemperatureC, "°C") ?: "não informada"
        val humidity = weather?.humidityPercent?.let { "$it%" } ?: "não informada"
        val condition = input.conditionDescription?.takeIf { it.isNotBlank() } ?: "não informada"
        val clouds = weather?.cloudCoverPercent?.let { "$it%" } ?: "não informado"
        val wind = weather?.windSpeedMs?.let { number(it, " m/s") } ?: "não informado"
        val radiation = weather?.shortwaveRadiationWm2?.let { "${it.roundToLong()} W/m²" } ?: "não informada"
        val uv = weather?.uvIndexMax?.let { number(it, "") } ?: "não informado"
        val sunrise = weather?.sunrise ?: "não informado"
        val sunset = weather?.sunset ?: "não informado"
        val rain = weather?.precipitationProbabilityPercent?.let { "$it%" } ?: "não informada"
        val species = input.species?.takeIf { it.isNotBlank() } ?: "não informada"
        val identification = if (input.identification == IdentificationSource.MANUAL) "manual" else "automática"
        val confidence = when {
            input.identification == IdentificationSource.MANUAL -> 100
            input.confidencePercent == null -> 0
            else -> input.confidencePercent.coerceIn(0, 100)
        }
        val symptom = input.symptom?.takeIf { it.isNotBlank() } ?: "não descrito"
        val question = input.question?.takeIf { it.isNotBlank() } ?: "O que devo fazer?"
        val knowledge = knowledgeText.takeIf { it.isNotBlank() }
            ?: "sem dados específicos para esta espécie"
        val dateTime = input.now.format(dateFormatter)

        return buildString {
            appendLine("Você é um assistente agronômico especializado em plantas ornamentais, hortaliças e frutíferas no Brasil.")
            appendLine()
            appendLine("CONTEXTO AMBIENTAL (local do usuário):")
            appendLine("- Local: $city")
            appendLine("- Temperatura: $temperature (sensação $apparent) | Umidade do ar: $humidity")
            appendLine("- Condição: $condition | Nuvens: $clouds | Vento: $wind")
            appendLine("- Radiação solar agora: $radiation | UV máx hoje: $uv")
            appendLine("- Sol: nasce $sunrise, põe $sunset | Chance de chuva hoje: $rain")
            appendLine("- Data/hora local: $dateTime")
            appendLine()
            appendLine("PLANTA:")
            appendLine("- Espécie: $species (identificação: $identification, confiança $confidence%)")
            appendLine("- Sintoma/problema: $symptom")
            appendLine("- Pergunta do usuário: $question")
            appendLine()
            appendLine("CONHECIMENTO DE REFERÊNCIA (base local):")
            appendLine(knowledge.trim())
            appendLine()
            appendLine("REGRAS DE RESPOSTA:")
            appendLine("1. Português do Brasil, direto, no máximo 3 passos práticos numerados.")
            appendLine("2. Considere obrigatoriamente umidade, chuva prevista e sol ao recomendar rega, adubação, poda ou transplante.")
            appendLine("3. Se a confiança da identificação for < 65%, declare a incerteza e dê conselho conservador.")
            appendLine("4. Não recomende agrotóxicos de uso restrito nem doses químicas; priorize manejo cultural/biológico.")
            appendLine("5. Se os dados forem insuficientes ou o caso parecer grave, oriente procurar um agrônomo/loja especializada.")
            append("6. Não invente dados climáticos nem nomes de doenças não suportados pelos sintomas.")
        }
    }

    private fun number(value: Double?, suffix: String): String? {
        value ?: return null
        val rounded = value.roundToLong().toDouble()
        val text = if (abs(value - rounded) < 0.05) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
        return if (suffix.isBlank()) text else "$text$suffix"
    }
}
