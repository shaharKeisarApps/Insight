package com.keisardev.insight.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.keisardev.insight.InsightApp
import com.keisardev.insight.R
import com.keisardev.insight.core.ai.model.ModelRepository
import com.keisardev.insight.core.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var modelRepository: ModelRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        modelRepository = (application as InsightApp).appGraph.modelRepository
        startForeground(NOTIFICATION_ID, createProgressNotification(0f))

        serviceScope.launch {
            modelRepository.modelState.collect { state ->
                when (state) {
                    is ModelState.Downloading -> {
                        updateNotification(createProgressNotification(state.progress))
                    }
                    is ModelState.Ready -> {
                        updateNotification(createCompleteNotification())
                        stopSelf()
                    }
                    is ModelState.Error -> {
                        updateNotification(createErrorNotification(state.message))
                        stopSelf()
                    }
                    ModelState.NotInstalled -> {
                        // Cancelled
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Download",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress when downloading AI models"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createProgressNotification(progress: Float): Notification {
        val percent = (progress * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading AI Model")
            .setContentText("$percent% complete")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Cancel",
                createCancelPendingIntent(),
            )
            .build()
    }

    private fun createCompleteNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AI Model Ready")
            .setContentText("On-device AI is ready to use")
            .setAutoCancel(true)
            .build()

    private fun createErrorNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Download Failed")
            .setContentText(message)
            .setAutoCancel(true)
            .build()

    private fun createCancelPendingIntent(): PendingIntent {
        val intent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            modelRepository.cancelDownload()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(notification: Notification) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_CANCEL = "com.keisardev.insight.CANCEL_DOWNLOAD"
    }
}
