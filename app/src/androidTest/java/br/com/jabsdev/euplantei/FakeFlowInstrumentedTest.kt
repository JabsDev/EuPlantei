package br.com.jabsdev.euplantei

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.jabsdev.euplantei.data.db.AppDatabase
import br.com.jabsdev.euplantei.data.repo.ConsultationRepository
import br.com.jabsdev.euplantei.data.repo.NewConsultation
import br.com.jabsdev.euplantei.domain.model.LlmParams
import br.com.jabsdev.euplantei.domain.model.PlantPrediction
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.ml.LlmEngine
import br.com.jabsdev.euplantei.ml.VisionClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Fluxo instrumentado com as duas interfaces falsas (visão e LLM), sem carregar
 * TensorFlow Lite nem llama.cpp. Exercita o caminho de persistência que a UI usa:
 * salvar → listar → excluir → desfazer.
 */
@RunWith(AndroidJUnit4::class)
class FakeFlowInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ConsultationRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ConsultationRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fluxoCompletoSalvaListaExcluiEDesfaz() = runBlocking {
        val image = File(context.cacheDir, "fake_consulta.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        val classifier = FakeVisionClassifier()
        val predictions = classifier.classify(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        assertEquals("Rosa", predictions.first().species)

        val llm = FakeLlmEngine()
        val tokens = mutableListOf<String>()
        llm.generate("prompt").collect { tokens += it }
        assertEquals("Conselho de teste.", tokens.joinToString(""))

        val saved = repository.save(
            NewConsultation(
                imagePath = image.absolutePath,
                species = predictions.first().species,
                condition = predictions.first().condition,
                confidencePercent = 91,
                manual = false,
                symptom = "Manchas nas folhas",
                question = "",
                city = "Campinas/SP",
                weather = WeatherContext(city = "Campinas/SP", temperatureC = 24.0, humidityPercent = 70),
                advice = tokens.joinToString(""),
            ),
        )
        assertEquals(1, repository.observeAll().first().size)
        repository.delete(saved)
        assertTrue(repository.observeAll().first().isEmpty())
        repository.undoDelete(saved)
        assertEquals(1, repository.observeAll().first().size)
    }

    private class FakeVisionClassifier : VisionClassifier {
        override val labelsCount = 1
        override suspend fun classify(bitmap: Bitmap): List<PlantPrediction> = listOf(
            PlantPrediction(
                rawLabel = "Rosa___healthy",
                species = "Rosa",
                speciesId = "rosa",
                condition = "Planta saudável",
                healthy = true,
                confidence = 0.91f,
            ),
        )
        override fun close() = Unit
    }

    private class FakeLlmEngine : LlmEngine {
        override val isLoaded: Boolean = true
        override suspend fun load(modelPath: String, nCtx: Int, nThreads: Int) = Unit
        override fun generate(prompt: String, params: LlmParams): Flow<String> =
            flowOf("Conselho ", "de ", "teste.")
        override fun cancel() = Unit
        override suspend fun unload() = Unit
    }
}
