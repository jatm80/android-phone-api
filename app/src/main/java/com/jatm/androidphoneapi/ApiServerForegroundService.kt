package com.jatm.androidphoneapi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ApiServerForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServerLifecycleRepository.markStopping()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        ServerLifecycleRepository.markStarting()
        startForeground(NOTIFICATION_ID, buildNotification())
        ServerLifecycleRepository.markRunning()
        return START_STICKY
    }

    override fun onDestroy() {
        ServerLifecycleRepository.markStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.server_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when the local API server lifecycle is active."
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Local API server lifecycle is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        const val ACTION_START = "com.jatm.androidphoneapi.action.START_SERVER"
        const val ACTION_STOP = "com.jatm.androidphoneapi.action.STOP_SERVER"

        private const val CHANNEL_ID = "api_server"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context): Intent =
            Intent(context, ApiServerForegroundService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, ApiServerForegroundService::class.java).setAction(ACTION_STOP)
    }
}
