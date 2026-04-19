package com.jatm.androidphoneapi.capabilities

interface NotificationSender {
    fun send(request: NotificationRequest): NotificationResult
}

data class NotificationRequest(
    val title: String,
    val body: String,
    val channel: String = "homelab",
    val priority: String = "default",
)

data class NotificationResult(
    val notificationId: Int,
    val delivered: Boolean,
)
