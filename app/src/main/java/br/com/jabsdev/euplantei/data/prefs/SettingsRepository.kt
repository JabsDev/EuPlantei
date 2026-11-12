package br.com.jabsdev.euplantei.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.jabsdev.euplantei.BuildConfig
import br.com.jabsdev.euplantei.domain.model.ConsultationDraft
import br.com.jabsdev.euplantei.domain.model.ModelInfo
import br.com.jabsdev.euplantei.domain.model.ModelSource
import br.com.jabsdev.euplantei.domain.model.TemperatureUnit
import br.com.jabsdev.euplantei.domain.model.ThemeMode
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "euplantei_settings",
)

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val useGeocoder: Boolean = true,
    val gardenRemindersEnabled: Boolean = true,
    val lastUpdateCheckAt: Long = 0L,
)

data class CachedWeather(
    val weather: WeatherContext,
    val savedAtMillis: Long,
)

/**
 * Configurações pequenas e não sensíveis. Rascunhos de consulta também vivem aqui
 * para sobreviver à morte do processo sem acoplar a UI ao ciclo de vida da Activity.
 */
class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val useGeocoder = booleanPreferencesKey("use_geocoder")
        val gardenRemindersEnabled = booleanPreferencesKey("garden_reminders_enabled")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
        val themeMode = stringPreferencesKey("theme_mode")
        val temperatureUnit = stringPreferencesKey("temperature_unit")
        val weatherCache = stringPreferencesKey("weather_cache_json")
        val weatherCacheAt = stringPreferencesKey("weather_cache_at")
        val modelPath = stringPreferencesKey("model_path")
        val modelName = stringPreferencesKey("model_name")
        val modelSize = stringPreferencesKey("model_size")
        val modelAddedAt = stringPreferencesKey("model_added_at")
        val modelUrl = stringPreferencesKey("model_url")
        val modelSha = stringPreferencesKey("model_sha")
        val consultationDraft = stringPreferencesKey("consultation_draft_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false,
            themeMode = prefs[Keys.themeMode].toThemeMode(),
            temperatureUnit = prefs[Keys.temperatureUnit].toTemperatureUnit(),
            useGeocoder = prefs[Keys.useGeocoder] ?: true,
            gardenRemindersEnabled = prefs[Keys.gardenRemindersEnabled] ?: true,
            lastUpdateCheckAt = prefs[Keys.lastUpdateCheckAt] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[Keys.temperatureUnit] = unit.name }
    }

    suspend fun setUseGeocoder(enabled: Boolean) {
        dataStore.edit { it[Keys.useGeocoder] = enabled }
    }

    suspend fun setGardenRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.gardenRemindersEnabled] = enabled }
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        dataStore.edit { it[Keys.lastUpdateCheckAt] = timestamp }
    }

    val cachedWeather: Flow<CachedWeather?> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.weatherCache] ?: return@map null
        val savedAt = prefs[Keys.weatherCacheAt]?.toLongOrNull() ?: return@map null
        runCatching { json.decodeFromString<WeatherContext>(raw) }
            .getOrNull()
            ?.let { CachedWeather(it, savedAt) }
    }

    suspend fun saveWeatherCache(weather: WeatherContext) {
        val timestamp = System.currentTimeMillis()
        dataStore.edit { prefs ->
            prefs[Keys.weatherCache] = json.encodeToString(WeatherContext.serializer(), weather)
            prefs[Keys.weatherCacheAt] = timestamp.toString()
        }
    }

    suspend fun clearWeatherCache() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.weatherCache)
            prefs.remove(Keys.weatherCacheAt)
        }
    }

    val modelInfo: Flow<ModelInfo?> = dataStore.data.map { prefs ->
        val path = prefs[Keys.modelPath] ?: return@map null
        val name = prefs[Keys.modelName] ?: return@map null
        val size = prefs[Keys.modelSize]?.toLongOrNull() ?: return@map null
        val addedAt = prefs[Keys.modelAddedAt]?.toLongOrNull() ?: return@map null
        ModelInfo(path = path, displayName = name, sizeBytes = size, addedAtMillis = addedAt)
    }

    val modelSource: Flow<ModelSource> = dataStore.data.map { prefs ->
        ModelSource(
            url = prefs[Keys.modelUrl] ?: BuildConfig.MODEL_URL,
            sha256 = prefs[Keys.modelSha] ?: BuildConfig.MODEL_SHA256,
        )
    }

    suspend fun setActiveModel(info: ModelInfo) {
        dataStore.edit { prefs ->
            prefs[Keys.modelPath] = info.path
            prefs[Keys.modelName] = info.displayName
            prefs[Keys.modelSize] = info.sizeBytes.toString()
            prefs[Keys.modelAddedAt] = info.addedAtMillis.toString()
        }
    }

    suspend fun clearActiveModel() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.modelPath)
            prefs.remove(Keys.modelName)
            prefs.remove(Keys.modelSize)
            prefs.remove(Keys.modelAddedAt)
        }
    }

    suspend fun setModelSource(url: String, sha256: String) {
        dataStore.edit { prefs ->
            prefs[Keys.modelUrl] = url
            prefs[Keys.modelSha] = sha256
        }
    }

    suspend fun saveDraft(draft: ConsultationDraft) {
        dataStore.edit { prefs ->
            prefs[Keys.consultationDraft] =
                json.encodeToString(ConsultationDraft.serializer(), draft)
        }
    }

    val consultationDraft: Flow<ConsultationDraft?> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.consultationDraft] ?: return@map null
        runCatching { json.decodeFromString<ConsultationDraft>(raw) }.getOrNull()
    }

    suspend fun loadDraft(): ConsultationDraft? {
        val raw = dataStore.data.first()[Keys.consultationDraft] ?: return null
        return runCatching { json.decodeFromString<ConsultationDraft>(raw) }.getOrNull()
    }

    suspend fun clearDraft() {
        dataStore.edit { prefs -> prefs.remove(Keys.consultationDraft) }
    }

    private fun String?.toThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(this ?: "") }.getOrDefault(ThemeMode.SYSTEM)

    private fun String?.toTemperatureUnit(): TemperatureUnit =
        runCatching { TemperatureUnit.valueOf(this ?: "") }.getOrDefault(TemperatureUnit.CELSIUS)
}
