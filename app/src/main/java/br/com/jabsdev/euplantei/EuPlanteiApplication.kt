package br.com.jabsdev.euplantei

import android.app.Application
import android.content.ComponentCallbacks2
import br.com.jabsdev.euplantei.service.GardenReminders
import kotlinx.coroutines.launch

class EuPlanteiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // O worker confere a preferência antes de notificar; agendar só quando
        // ligado evita manter trabalho periódico desnecessário. O WorkManager
        // persiste o agendamento entre reinícios do aparelho.
        container.appScope.launch {
            runCatching {
                if (container.settingsRepository.current().gardenRemindersEnabled) {
                    GardenReminders.schedule(this@EuPlanteiApplication)
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            container.onCriticalMemory()
        }
    }
}
