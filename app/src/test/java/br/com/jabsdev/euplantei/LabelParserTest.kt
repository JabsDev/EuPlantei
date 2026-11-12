package br.com.jabsdev.euplantei

import br.com.jabsdev.euplantei.domain.LabelParser
import br.com.jabsdev.euplantei.domain.model.ConfidenceLevel
import br.com.jabsdev.euplantei.domain.model.ConfidenceRules
import br.com.jabsdev.euplantei.domain.model.FriendlyLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelParserTest {
    private val map = mapOf(
        LabelParser.normalizeKey("Tomato___Early_blight") to FriendlyLabel(
            species = "Tomateiro",
            speciesId = "tomateiro",
            condition = "Pinta-preta",
            healthy = false,
        ),
        LabelParser.normalizeKey("Tomato___healthy") to FriendlyLabel(
            species = "Tomateiro",
            speciesId = "tomateiro",
            condition = "Planta saudável",
            healthy = true,
        ),
    )

    @Test
    fun `parse label conhecido usa traducao`() {
        val prediction = LabelParser.withConfidence("Tomato___Early_blight", 0.91f, map)
        assertEquals("Tomateiro", prediction.species)
        assertEquals("Pinta-preta", prediction.condition)
        assertEquals(0.91f, prediction.confidence, 0.0001f)
        assertFalse(prediction.healthy)
    }

    @Test
    fun `parse com acento na chave normaliza igual`() {
        val accented = mapOf(
            LabelParser.normalizeKey("Tomate___Doença") to FriendlyLabel(
                "Tomateiro", "tomateiro", "Doença", false,
            ),
        )
        val prediction = LabelParser.parse("Tomate___Doenca", accented)
        assertEquals("Tomateiro", prediction.species)
    }

    @Test
    fun `parse desconhecido nao lanca e separa condicao`() {
        val prediction = LabelParser.parse("Manga___Antracnose", map)
        assertEquals("Manga", prediction.species)
        assertEquals("Antracnose", prediction.condition)
        assertFalse(prediction.healthy)
    }

    @Test
    fun `top3 ordena e limita`() {
        val labels = listOf("Tomato___Early_blight", "Tomato___healthy", "Outra___Doenca", "Quarta___X")
        val probabilities = floatArrayOf(0.2f, 0.7f, 0.06f, 0.04f)
        val result = LabelParser.topPredictions(labels, probabilities, map)
        assertEquals(3, result.size)
        assertEquals("Tomato___healthy", result[0].rawLabel)
        assertEquals("Tomato___Early_blight", result[1].rawLabel)
    }

    @Test
    fun `limiares de confianca seguem regra de produto`() {
        assertEquals(ConfidenceLevel.HIGH, ConfidenceRules.level(0.65f))
        assertEquals(ConfidenceLevel.MEDIUM, ConfidenceRules.level(0.64f))
        assertEquals(ConfidenceLevel.MEDIUM, ConfidenceRules.level(0.40f))
        assertEquals(ConfidenceLevel.LOW, ConfidenceRules.level(0.39f))
        assertTrue(ConfidenceRules.shouldUseAutomatically(0.80f))
        assertTrue(ConfidenceRules.needsConfirmation(0.50f))
        assertTrue(ConfidenceRules.needsManualInput(0.10f))
    }
}
