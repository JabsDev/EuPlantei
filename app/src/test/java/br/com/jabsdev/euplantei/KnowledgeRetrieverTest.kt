package br.com.jabsdev.euplantei

import br.com.jabsdev.euplantei.domain.KnowledgeRetriever
import br.com.jabsdev.euplantei.domain.model.DiseaseInfo
import br.com.jabsdev.euplantei.domain.model.KnowledgeBase
import br.com.jabsdev.euplantei.domain.model.PlantProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KnowledgeRetrieverTest {
    private val retriever = KnowledgeRetriever()

    private val base = KnowledgeBase(
        plantas = listOf(
            PlantProfile(
                id = "rosa",
                nomes = listOf("Rosa", "Roseira", "Rosa spp."),
                luz = "Sol pleno",
                rega = "Quando o solo secar",
                faixaTemperatura = "15 a 28 °C",
                solo = "Bem drenado",
                doencasComuns = listOf(
                    DiseaseInfo(
                        nome = "Mancha-negra",
                        sintomas = "Manchas escuras nas folhas com amarelecimento",
                        manejo = "Retirar folhas doentes e melhorar ventilação",
                    ),
                    DiseaseInfo(
                        nome = "Oídio",
                        sintomas = "Pó branco nas folhas",
                        manejo = "Aumentar circulação de ar",
                    ),
                ),
                toxicidadePet = "Não tóxica",
            ),
            PlantProfile(
                id = "orquidea-phalaenopsis",
                nomes = listOf("Orquídea phalaenopsis", "Phalaenopsis amabilis"),
                luz = "Luz indireta",
                rega = "Quando as raízes pratearem",
                faixaTemperatura = "18 a 28 °C",
                solo = "Casca de pinus",
                doencasComuns = listOf(
                    DiseaseInfo(
                        nome = "Podridão de raiz",
                        sintomas = "Raízes marrons e folhas amareladas",
                        manejo = "Suspender a rega e replantar",
                    ),
                ),
                toxicidadePet = "Não tóxica",
            ),
        ),
    )

    @Test
    fun `match com acento encontra perfil`() {
        val result = retriever.retrieve(base, "Orquídea phalaenopsis", "raízes escuras")
        assertEquals(1, result.profiles.size)
        assertEquals("orquidea-phalaenopsis", result.profiles.first().id)
        assertTrue(result.hasData)
    }

    @Test
    fun `match sem acento encontra o mesmo perfil`() {
        val result = retriever.retrieve(base, "orquidea", "raizes escuras")
        assertEquals("orquidea-phalaenopsis", result.profiles.first().id)
    }

    @Test
    fun `sintoma escolhe doenca relevante`() {
        val result = retriever.retrieve(base, "roseira", "manchas escuras nas folhas")
        assertEquals("rosa", result.profiles.first().id)
        assertTrue(result.diseases.any { it.disease.nome == "Mancha-negra" })
        assertTrue(result.diseases.size <= 3)
        assertTrue(result.profiles.size <= 2)
    }

    @Test
    fun `sem match nao inventa dados`() {
        val result = retriever.retrieve(base, "tomateiro", "folhas amarelas")
        assertFalse(result.hasData)
        assertTrue(result.toPromptText().contains("sem dados específicos"))
    }

    @Test
    fun `todos os labels do modelo de especies apontam para um perfil`() {
        val baseFile = File("src/main/assets/knowledge/plants.json")
        val base = Json { ignoreUnknownKeys = true }.decodeFromString<KnowledgeBase>(baseFile.readText())
        val ids = base.plantas.map { it.id }.toSet()
        val labelsFile = File("src/main/assets/models/houseplant_labels_ptbr.json")
        val root = Json.parseToJsonElement(labelsFile.readText()).jsonObject
        val labels = root["labels"]!!.jsonObject
        assertEquals(59, labels.size)
        labels.values.forEach { value ->
            val speciesId = value.jsonObject["speciesId"]!!.jsonPrimitive.content
            assertTrue("Perfil ausente para $speciesId", speciesId in ids)
            val species = value.jsonObject["species"]!!.jsonPrimitive.content
            assertTrue(
                "Recuperação vazia para $species",
                KnowledgeRetriever().retrieve(base, species, "").hasData,
            )
        }
        val ordered = File("src/main/assets/models/houseplant_labels.txt")
            .readLines()
            .filter { it.isNotBlank() }
        assertEquals(ordered.size, labels.size)
        ordered.forEach { assertTrue("Label fora do JSON: $it", it in labels) }
    }

    @Test
    fun `todos os perfis tem intervalo de rega recomendado`() {
        val baseFile = File("src/main/assets/knowledge/plants.json")
        val base = Json { ignoreUnknownKeys = true }
            .decodeFromString<KnowledgeBase>(baseFile.readText())
        assertEquals(67, base.plantas.size)
        base.plantas.forEach { profile ->
            val days = profile.intervaloRegaDias
            assertTrue(
                "Intervalo ausente ou inválido para ${profile.id}: $days",
                days != null && days in 1..60,
            )
        }
    }

    @Test
    fun `base real tem ao menos trinta plantas e json valido`() {
        val file = File("src/main/assets/knowledge/plants.json")
        assertTrue("plants.json ausente", file.exists())
        val real = Json { ignoreUnknownKeys = true }.decodeFromString<KnowledgeBase>(file.readText())
        assertTrue(real.plantas.size >= 30)
        assertTrue(real.plantas.all { it.nomes.isNotEmpty() && it.doencasComuns.isNotEmpty() })
    }
}
