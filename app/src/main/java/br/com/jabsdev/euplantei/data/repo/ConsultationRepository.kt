package br.com.jabsdev.euplantei.data.repo

import android.content.Context
import android.net.Uri
import br.com.jabsdev.euplantei.data.db.AppDatabase
import br.com.jabsdev.euplantei.data.db.ConsultationDao
import br.com.jabsdev.euplantei.data.db.ConsultationEntity
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NewConsultation(
    val imagePath: String,
    val species: String,
    val condition: String,
    val confidencePercent: Int,
    val manual: Boolean,
    val symptom: String,
    val question: String,
    val city: String?,
    val weather: WeatherContext?,
    val advice: String,
)

class ConsultationRepository(
    context: Context,
    database: AppDatabase = AppDatabase.get(context),
) {
    private val appContext = context.applicationContext
    private val dao: ConsultationDao = database.consultationDao()
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<ConsultationEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<ConsultationEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): ConsultationEntity? = dao.getById(id)

    suspend fun save(new: NewConsultation): ConsultationEntity = withContext(Dispatchers.IO) {
        val persistentPath = persistImage(new.imagePath)
        val entity = ConsultationEntity(
            createdAt = System.currentTimeMillis(),
            imagePath = persistentPath,
            species = new.species,
            condition = new.condition,
            confidencePercent = new.confidencePercent.coerceIn(0, 100),
            manual = new.manual,
            symptom = new.symptom,
            question = new.question,
            city = new.city,
            weatherJson = new.weather?.let {
                json.encodeToString(WeatherContext.serializer(), it)
            },
            advice = new.advice,
        )
        val id = dao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun delete(entity: ConsultationEntity) = withContext(Dispatchers.IO) {
        dao.deleteById(entity.id)
    }

    suspend fun undoDelete(entity: ConsultationEntity): ConsultationEntity = withContext(Dispatchers.IO) {
        val id = dao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun deleteImage(path: String) {
        ImageUtils.deleteQuietly(path)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val entities = dao.getAllOnce()
        dao.clear()
        entities.forEach { ImageUtils.deleteQuietly(it.imagePath) }
        File(appContext.cacheDir, "consultas").listFiles()?.forEach { it.delete() }
    }

    suspend fun exportText(entity: ConsultationEntity, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = buildExportText(entity)
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("Não foi possível abrir o arquivo de destino")
            }
        }

    fun weatherOf(entity: ConsultationEntity): WeatherContext? =
        entity.weatherJson?.let {
            runCatching { json.decodeFromString(WeatherContext.serializer(), it) }.getOrNull()
        }

    fun buildExportText(entity: ConsultationEntity): String {
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(entity.createdAt).atZone(ZoneId.systemDefault()))
        val weather = weatherOf(entity)
        return buildString {
            appendLine("EuPlantei — Consulta")
            appendLine("Data: $date")
            appendLine("Espécie: ${entity.species}")
            appendLine("Condição provável: ${entity.condition}")
            appendLine("Confiança: ${entity.confidencePercent}%")
            appendLine("Identificação: ${if (entity.manual) "manual" else "automática"}")
            if (entity.symptom.isNotBlank()) appendLine("Sintoma: ${entity.symptom}")
            if (entity.city != null) appendLine("Local: ${entity.city}")
            if (weather != null) {
                appendLine(
                    "Clima: ${weather.temperatureC ?: "?"} °C, umidade ${weather.humidityPercent ?: "?"}%, " +
                        "chuva ${weather.precipitationProbabilityPercent ?: "?"}%",
                )
            }
            appendLine()
            appendLine("CONSELHO")
            appendLine(entity.advice)
            appendLine()
            appendLine("Orientação educativa. Em casos graves, consulte um profissional.")
        }
    }

    private suspend fun persistImage(path: String): String {
        val source = File(path)
        if (!source.exists()) return path
        val filesRoot = File(appContext.filesDir, "consultas").absolutePath
        if (path.startsWith(filesRoot)) return path
        return ImageUtils.persistForHistory(appContext, path).absolutePath
    }
}
