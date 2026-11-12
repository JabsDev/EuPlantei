package br.com.jabsdev.euplantei

import android.app.Application
import android.content.Context
import br.com.jabsdev.euplantei.data.db.AppDatabase
import br.com.jabsdev.euplantei.data.prefs.SettingsRepository
import br.com.jabsdev.euplantei.data.repo.ConsultationRepository
import br.com.jabsdev.euplantei.data.repo.GardenRepository
import br.com.jabsdev.euplantei.data.repo.KnowledgeRepository
import br.com.jabsdev.euplantei.data.repo.ModelRepository
import br.com.jabsdev.euplantei.data.repo.UpdateManager
import br.com.jabsdev.euplantei.data.repo.WeatherRepository
import br.com.jabsdev.euplantei.ml.LlamaEngine
import br.com.jabsdev.euplantei.ml.LlmManager
import br.com.jabsdev.euplantei.domain.model.PredictionSource
import br.com.jabsdev.euplantei.ml.HybridVisionClassifier
import br.com.jabsdev.euplantei.ml.PlantClassifier
import br.com.jabsdev.euplantei.ml.VisionClassifier
import br.com.jabsdev.euplantei.util.NetworkMonitor
import br.com.jabsdev.euplantei.util.ThermalMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    private val app = application

    val context: Context get() = app

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val networkMonitor: NetworkMonitor = NetworkMonitor(application)
    val thermalMonitor: ThermalMonitor = ThermalMonitor(application)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    val visionClassifier: VisionClassifier by lazy {
        HybridVisionClassifier(
            houseplantClassifier = PlantClassifier(
                context = app,
                modelAsset = "models/houseplant_model.tflite",
                labelsAsset = "models/houseplant_labels.txt",
                friendlyAsset = "models/houseplant_labels_ptbr.json",
                normalizeToUnit = false,
                source = PredictionSource.HOUSEPLANT,
            ),
            cropClassifier = PlantClassifier(
                context = app,
                modelAsset = "models/plant_model.tflite",
                labelsAsset = "models/labels.txt",
                friendlyAsset = "models/labels_ptbr.json",
                normalizeToUnit = true,
                source = PredictionSource.CROP_DISEASE,
            ),
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(app, settingsRepository, networkMonitor, httpClient)
    }

    val knowledgeRepository: KnowledgeRepository by lazy {
        KnowledgeRepository(app)
    }

    val consultationRepository: ConsultationRepository by lazy {
        ConsultationRepository(app, AppDatabase.get(app))
    }

    val gardenRepository: GardenRepository by lazy {
        GardenRepository(
            context = app,
            database = AppDatabase.get(app),
            weatherRepository = weatherRepository,
            knowledgeRepository = knowledgeRepository,
        )
    }

    val modelRepository: ModelRepository by lazy {
        ModelRepository(
            context = app,
            settings = settingsRepository,
            networkMonitor = networkMonitor,
            scope = appScope,
        )
    }

    val updateManager: UpdateManager by lazy {
        UpdateManager(
            context = app,
            settings = settingsRepository,
            client = httpClient,
            scope = appScope,
        )
    }

    val llmManager: LlmManager by lazy {
        LlmManager(
            scope = appScope,
            engine = LlamaEngine(),
            modelRepository = modelRepository,
            thermalMonitor = thermalMonitor,
        )
    }

    fun onCriticalMemory() {
        llmManager.onCriticalMemory()
    }
}
