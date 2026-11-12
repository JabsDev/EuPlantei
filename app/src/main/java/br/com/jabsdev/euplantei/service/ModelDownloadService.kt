package br.com.jabsdev.euplantei.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.jabsdev.euplantei.EuPlanteiApplication
import br.com.jabsdev.euplantei.ui.MainActivity
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.repo.DownloadFailure
import br.com.jabsdev.euplantei.data.repo.ModelDownloadState
import br.com.jabsdev.euplantei.data.repo.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Mantém o download do GGUF vivo com o aparelho bloqueado/em segundo plano.
 * O Android pode suspender uma corrotina comum quando a tela apaga; um
 * foreground service do tipo dataSync + WakeLock evita essa interrupção.
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val repository: ModelRepository
        get() = (application as EuPlanteiApplication).container.modelRepository

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            repository.cancelDownload()
            finish(removeNotification = true, finalNotification = null)
            return START_NOT_STICKY
        }

        startForegroundSafely(buildProgressNotification(0, 0, -1, indeterminate = true))
        acquireWakeLock()

        if (observationJob == null) {
            observationJob = scope.launch {
                repository.downloadState.collect { state -> render(state) }
            }
        }
        return START_NOT_STICKY
    }

    private fun render(state: ModelDownloadState) {
        when (state) {
            is ModelDownloadState.Downloading -> {
                val notification = buildProgressNotification(
                    downloaded = state.downloadedBytes,
                    total = state.totalBytes,
                    percent = state.percent,
                    indeterminate = state.totalBytes <= 0,
                )
                notificationManager().notify(NOTIFICATION_ID, notification)
            }

            ModelDownloadState.Verifying -> {
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildProgressNotification(0, 0, -1, indeterminate = true, message = getString(R.string.download_notification_verifying)),
                )
            }

            is ModelDownloadState.Completed -> {
                val notification = baseNotification(
                    title = getString(R.string.download_notification_complete_title),
                    text = getString(R.string.download_notification_complete_text),
                    ongoing = false,
                    progress = null,
                )
                finish(removeNotification = false, finalNotification = notification)
            }

            is ModelDownloadState.Failed -> {
                if (state.reason == DownloadFailure.CANCELLED) {
                    finish(removeNotification = true, finalNotification = null)
                } else {
                    val notification = baseNotification(
                        title = getString(R.string.download_notification_failed_title),
                        text = getString(R.string.download_notification_failed_text),
                        ongoing = false,
                        progress = null,
                    )
                    finish(removeNotification = false, finalNotification = notification)
                }
            }

            ModelDownloadState.Idle -> Unit
        }
    }

    private fun finish(removeNotification: Boolean, finalNotification: Notification?) {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!removeNotification) {
            finalNotification?.let {
                runCatching { notificationManager().notify(FINAL_NOTIFICATION_ID, it) }
            }
        }
        stopSelf()
    }

    private fun buildProgressNotification(
        downloaded: Long,
        total: Long,
        percent: Int,
        indeterminate: Boolean,
        message: String? = null,
    ): Notification {
        val text = message ?: if (total > 0) {
            getString(
                R.string.download_notification_progress,
                percent.coerceIn(0, 100),
                formatMegabytes(downloaded),
                formatMegabytes(total),
            )
        } else {
            getString(R.string.download_notification_unknown, formatMegabytes(downloaded))
        }
        return baseNotification(
            title = getString(R.string.download_notification_title),
            text = text,
            ongoing = true,
            progress = if (indeterminate) null else percent,
        )
    }

    private fun baseNotification(
        title: String,
        text: String,
        ongoing: Boolean,
        progress: Int?,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
        if (ongoing) {
            builder.addAction(0, getString(R.string.download_notification_cancel), cancelIntent)
            if (progress == null) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setProgress(100, progress, false)
            }
        }
        return builder.build()
    }

    private fun startForegroundSafely(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EuPlantei:model-download",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_DOWNLOAD_WAKE_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.download_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun formatMegabytes(bytes: Long): String =
        String.format(Locale.forLanguageTag("pt-BR"), "%.0f", bytes / 1_000_000.0)

    override fun onDestroy() {
        observationJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "euplantei_model_download"
        private const val NOTIFICATION_ID = 4101
        private const val FINAL_NOTIFICATION_ID = 4102
        private const val ACTION_START = "br.com.jabsdev.euplantei.action.START_MODEL_DOWNLOAD"
        private const val ACTION_CANCEL = "br.com.jabsdev.euplantei.action.CANCEL_MODEL_DOWNLOAD"
        private const val MAX_DOWNLOAD_WAKE_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(ACTION_START)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
        }
    }
}
