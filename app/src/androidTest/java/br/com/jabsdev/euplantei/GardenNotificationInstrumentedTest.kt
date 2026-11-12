package br.com.jabsdev.euplantei

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.domain.GardenAlertKind
import br.com.jabsdev.euplantei.domain.GardenWeatherAlert
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.service.GardenNotifications
import br.com.jabsdev.euplantei.service.GardenReminders
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GardenNotificationInstrumentedTest {

    @Test
    fun publicaNotificacaoDeRegaComAvisoDeChuva() {
        val context: Context = ApplicationProvider.getApplicationContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val descriptor = instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            )
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            SystemClock.sleep(150)
        }
        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())

        val plant = GardenPlantEntity(
            id = 1,
            name = "Lírio da varanda",
            species = "Lírio",
            speciesId = "lirio",
            coverImagePath = "",
            addedAt = 0,
            nextWateringAt = 0,
        )
        GardenNotifications.notify(
            context = context,
            due = listOf(plant),
            alerts = listOf(
                GardenWeatherAlert(
                    kind = GardenAlertKind.RAIN,
                    precipitationProbability = 80,
                ),
            ),
            weather = WeatherContext(city = "Campinas", temperatureC = 22.0),
            careTip = null,
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        var posted = false
        for (attempt in 0 until 20) {
            posted = manager.activeNotifications.any {
                it.id == GardenReminders.NOTIFICATION_ID
            }
            if (posted) break
            SystemClock.sleep(100)
        }
        assertTrue("Notificação do jardim não foi publicada", posted)
        manager.cancel(GardenReminders.NOTIFICATION_ID)
    }
}
