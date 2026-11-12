package br.com.jabsdev.euplantei

import br.com.jabsdev.euplantei.domain.IdentificationSource
import br.com.jabsdev.euplantei.domain.PromptBuilder
import br.com.jabsdev.euplantei.domain.PromptInput
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PromptBuilderTest {
    private val now = LocalDateTime.of(2026, 11, 11, 14, 30)
    private val weather = WeatherContext(
        city = "São Paulo/SP",
        temperatureC = 27.4,
        apparentTemperatureC = 29.0,
        humidityPercent = 63,
        conditionCode = 2,
        cloudCoverPercent = 40,
        windSpeedMs = 3.2,
        shortwaveRadiationWm2 = 540.0,
        sunrise = "05:12",
        sunset = "18:31",
        uvIndexMax = 9.0,
        precipitationProbabilityPercent = 35,
    )

    @Test
    fun `prompt completo contem todos os campos`() {
        val prompt = PromptBuilder.build(
            PromptInput(
                weather = weather,
                species = "Rosa",
                identification = IdentificationSource.AUTOMATIC,
                confidencePercent = 91,
                symptom = "Manchas escuras nas folhas",
                question = "Posso podar agora?",
                now = now,
                conditionDescription = "Parcialmente nublado",
            ),
            knowledgeText = "Perfil: Rosa\n- Luz: sol pleno",
        )
        assertTrue(prompt.contains("Você é um assistente agronômico"))
        assertTrue(prompt.contains("- Local: São Paulo/SP"))
        assertTrue(prompt.contains("- Temperatura: 27.4°C (sensação 29°C) | Umidade do ar: 63%"))
        assertTrue(prompt.contains("- Condição: Parcialmente nublado | Nuvens: 40% | Vento: 3.2 m/s"))
        assertTrue(prompt.contains("- Radiação solar agora: 540 W/m² | UV máx hoje: 9"))
        assertTrue(prompt.contains("- Sol: nasce 05:12, põe 18:31 | Chance de chuva hoje: 35%"))
        assertTrue(prompt.contains("- Data/hora local: 11/11/2026 14:30"))
        assertTrue(prompt.contains("- Espécie: Rosa (identificação: automática, confiança 91%)"))
        assertTrue(prompt.contains("- Sintoma/problema: Manchas escuras nas folhas"))
        assertTrue(prompt.contains("- Pergunta do usuário: Posso podar agora?"))
        assertTrue(prompt.contains("Perfil: Rosa"))
        assertTrue(prompt.contains("6. Não invente dados climáticos"))
    }

    @Test
    fun `fallbacks nao deixam campos vazios`() {
        val prompt = PromptBuilder.build(
            PromptInput(weather = null, now = now),
            knowledgeText = "",
        )
        assertTrue(prompt.contains("- Local: não informado"))
        assertTrue(prompt.contains("- Temperatura: não informada (sensação não informada) | Umidade do ar: não informada"))
        assertTrue(prompt.contains("- Condição: não informada | Nuvens: não informado | Vento: não informado"))
        assertTrue(prompt.contains("- Sol: nasce não informado, põe não informado | Chance de chuva hoje: não informada"))
        assertTrue(prompt.contains("- Espécie: não informada (identificação: automática, confiança 0%)"))
        assertTrue(prompt.contains("- Sintoma/problema: não descrito"))
        assertTrue(prompt.contains("- Pergunta do usuário: O que devo fazer?"))
        assertTrue(prompt.contains("sem dados específicos para esta espécie"))
        assertFalse(prompt.contains("{especie}"))
    }

    @Test
    fun `identificacao manual e tratada como confiavel`() {
        val prompt = PromptBuilder.build(
            PromptInput(
                species = "Samambaia",
                identification = IdentificationSource.MANUAL,
                confidencePercent = 12,
                symptom = "Pontas secas",
                now = now,
            ),
            knowledgeText = "Perfil: Samambaia",
        )
        assertTrue(prompt.contains("(identificação: manual, confiança 100%)"))
        assertTrue(prompt.contains("Sintoma/problema: Pontas secas"))
    }

    @Test
    fun `regras de seguranca estao presentes`() {
        val prompt = PromptBuilder.build(PromptInput(now = now), "")
        assertTrue(prompt.contains("no máximo 3 passos práticos numerados"))
        assertTrue(prompt.contains("Não recomende agrotóxicos de uso restrito"))
        assertTrue(prompt.contains("oriente procurar um agrônomo"))
        assertTrue(prompt.contains("confiança da identificação for < 65%"))
    }
}
