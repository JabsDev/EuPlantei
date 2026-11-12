package br.com.jabsdev.euplantei.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.jabsdev.euplantei.EuPlanteiApplication
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.domain.GardenWeatherAdvisor
import br.com.jabsdev.euplantei.domain.GardenWeatherAlert
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.ui.MainActivity
import br.com.jabsdev.euplantei.util.bodyRes
import br.com.jabsdev.euplantei.util.titleRes
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Agendamento diário com WorkManager. O worker consulta o clima, mas nunca
 * depende dele: sem rede, os lembretes de rega continuam funcionando.
 */
object GardenReminders {
    const val CHANNEL_ID = "euplantei_garden"
    const val EXTRA_OPEN_GARDEN = "br.com.jabsdev.euplantei.extra.OPEN_GARDEN"
    const val NOTIFICATION_ID = 4201

    private const val WORK_NAME = "euplantei_garden_daily_reminders"
    private const val REMINDER_HOUR = 9

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.garden_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.garden_notification_channel_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<GardenReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Próxima ocorrência às 9h locais; se já passou, amanhã. */
    fun initialDelayMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
        var next = now
            .withHour(REMINDER_HOUR)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}

class GardenReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? EuPlanteiApplication ?: return Result.success()
        val container = app.container

        val settings = runCatching { container.settingsRepository.current() }.getOrNull()
            ?: return Result.success()
        if (!settings.gardenRemindersEnabled) return Result.success()

        val plants = runCatching { container.gardenRepository.plantsOnce() }
            .getOrDefault(emptyList())
        if (plants.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val due = runCatching { container.gardenRepository.duePlants(now) }
            .getOrDefault(emptyList())
            .sortedBy { it.nextWateringAt }
        val weather = runCatching { container.weatherRepository.getWeather(forceRefresh = false) }
            .getOrNull()
            ?.weather
        val alerts = GardenWeatherAdvisor.alerts(weather)

        if (due.isEmpty() && alerts.isEmpty()) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }
        val careTip = (due.firstOrNull() ?: plants.firstOrNull())?.let { plant ->
            runCatching {
                container.gardenRepository.profileFor(plant.speciesId, plant.species)
            }.getOrNull()?.let { profile ->
                if (profile.rega.isBlank() && profile.intervaloRegaDias == null) {
                    null
                } else {
                    buildString {
                        append(plant.name).append(": ")
                        profile.intervaloRegaDias?.let {
                            append(
                                container.context.getString(
                                    R.string.garden_notification_interval,
                                    it,
                                ),
                            )
                            append(". ")
                        }
                        append(profile.rega)
                    }.trim().removeSuffix(":")
                }
            }
        }
        GardenNotifications.notify(applicationContext, due, alerts, weather, careTip)
        return Result.success()
    }
}

object GardenNotifications {
    fun notify(
        context: Context,
        due: List<GardenPlantEntity>,
        alerts: List<GardenWeatherAlert>,
        weather: WeatherContext?,
        careTip: String? = null,
    ) {
        GardenReminders.ensureChannel(context)

        val title = if (due.isNotEmpty()) {
            context.getString(R.string.garden_notification_title_water)
        } else {
            context.getString(R.string.garden_notification_title_weather)
        }

        val lines = buildList {
            if (due.isNotEmpty()) {
                val names = due.take(4).joinToString(", ") { it.name }
                add(context.getString(R.string.garden_notification_body_water, names))
                if (due.size > 4) {
                    add(context.getString(R.string.garden_notification_more, due.size - 4))
                }
            }
            alerts.take(2).forEach { alert ->
                add("${context.getString(alert.kind.titleRes)}: ${context.getString(alert.kind.bodyRes)}")
            }
            if (!careTip.isNullOrBlank()) {
                add(context.getString(R.string.garden_notification_care, careTip))
            }
            if (weather?.city?.isNotBlank() == true) {
                add(context.getString(R.string.garden_notification_city, weather.city))
            }
        }
        val text = lines.joinToString("\n").ifBlank {
            context.getString(R.string.garden_notification_body_fallback)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(GardenReminders.EXTRA_OPEN_GARDEN, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, GardenReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(GardenReminders.NOTIFICATION_ID, notification)
        }
    }
}
