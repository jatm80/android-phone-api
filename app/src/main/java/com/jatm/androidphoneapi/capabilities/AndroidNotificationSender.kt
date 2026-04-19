package com.jatm.androidphoneapi.capabilities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jatm.androidphoneapi.R
import java.util.concurrent.atomic.AtomicInteger

class AndroidNotificationSender(
    private val context: Context,
) : NotificationSender {
    private val nextId = AtomicInteger(STARTING_ID)
    private val createdChannels = mutableSetOf<String>()

    override fun send(request: NotificationRequest): NotificationResult {
        ensureChannel(request.channel)

        val notificationId = nextId.getAndIncrement()
        val priority = mapPriority(request.priority)

        val notification = NotificationCompat.Builder(context, request.channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(request.title)
            .setContentText(request.body)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)

        return NotificationResult(
            notificationId = notificationId,
            delivered = true,
        )
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (channelId in createdChannels) return

        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(
            channelId,
            "Homelab: $channelId",
            importance,
        ).apply {
            description = "Notifications from homelab API (channel: $channelId)"
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        createdChannels.add(channelId)
    }

    private fun mapPriority(priority: String): Int = when (priority) {
        "low" -> NotificationCompat.PRIORITY_LOW
        "high" -> NotificationCompat.PRIORITY_HIGH
        else -> NotificationCompat.PRIORITY_DEFAULT
    }

    companion object {
        private const val STARTING_ID = 2001
    }
}
