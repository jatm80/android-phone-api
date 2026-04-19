package com.jatm.androidphoneapi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jatm.androidphoneapi.server.ApiServer
import com.jatm.androidphoneapi.server.ApiServerConfig
import com.jatm.androidphoneapi.server.EmbeddedKtorApiServer
import com.jatm.androidphoneapi.server.RequestOutcome
import com.jatm.androidphoneapi.server.RequestOutcomeLogger
import com.jatm.androidphoneapi.server.TlsConfigurationRequiredException

class ApiServerForegroundService : Service() {
    private var apiServer: ApiServer? = null

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

        return try {
            apiServer = EmbeddedKtorApiServer(
                config = ApiServerConfig.forBuild(BuildConfig.DEBUG),
                logger = AndroidRequestOutcomeLogger,
                apiKeyAuthenticator = AppGraph.apiKeyRepository(applicationContext),
                pairingRepository = AppGraph.pairingRepository(applicationContext),
            ).also { it.start() }
            ServerLifecycleRepository.markRunning()
            START_STICKY
        } catch (exception: TlsConfigurationRequiredException) {
            Log.w(TAG, "API server refused to start without TLS configuration")
            ServerLifecycleRepository.markStopped()
            stopSelf(startId)
            START_NOT_STICKY
        } catch (exception: RuntimeException) {
            Log.e(TAG, "API server failed to start: ${exception::class.simpleName}")
            ServerLifecycleRepository.markStopped()
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        apiServer?.stop()
        apiServer = null
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

        private const val TAG = "ApiServerService"
        private const val CHANNEL_ID = "api_server"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context): Intent =
            Intent(context, ApiServerForegroundService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, ApiServerForegroundService::class.java).setAction(ACTION_STOP)
    }
}

private object AndroidRequestOutcomeLogger : RequestOutcomeLogger {
    override fun log(outcome: RequestOutcome) {
        Log.i(
            "ApiRequest",
            "requestId=${outcome.requestId} method=${outcome.method} path=${outcome.path} " +
                "status=${outcome.statusCode} failure=${outcome.failure ?: "none"}",
        )
    }
}
