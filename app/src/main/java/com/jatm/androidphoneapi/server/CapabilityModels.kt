package com.jatm.androidphoneapi.server

import kotlinx.serialization.Serializable

@Serializable
data class BatteryInfoResponse(
    val level: Int,
    val scale: Int,
    val percentage: Int,
    val status: String,
    val health: String,
    val plugged: String,
    val technology: String? = null,
    val temperatureCelsius: Float,
    val voltageVolts: Float,
    val requestId: String,
)

@Serializable
data class DeviceInfoResponse(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val securityPatch: String? = null,
    val uptimeMillis: Long,
    val requestId: String,
)

@Serializable
data class SendNotificationRequest(
    val title: String,
    val body: String = "",
    val channel: String = "homelab",
    val priority: String = "default",
)

@Serializable
data class SendNotificationResponse(
    val notificationId: Int,
    val delivered: Boolean,
    val requestId: String,
)
