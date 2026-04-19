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

@Serializable
data class ClipboardReadResponse(
    val text: String? = null,
    val hasContent: Boolean,
    val requestId: String,
)

@Serializable
data class ClipboardWriteRequest(
    val text: String,
)

@Serializable
data class ClipboardWriteResponse(
    val written: Boolean,
    val requestId: String,
)

@Serializable
data class LocationResponse(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null,
    val speed: Float? = null,
    val timestampEpochMillis: Long,
    val provider: String? = null,
    val requestId: String,
)

@Serializable
data class TtsSpeakRequest(
    val text: String,
    val locale: String = "en-US",
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
)

@Serializable
data class TtsSpeakResponse(
    val spoken: Boolean,
    val errorReason: String? = null,
    val requestId: String,
)

@Serializable
data class TtsEngineResponse(
    val name: String,
    val label: String,
    val isDefault: Boolean,
)

@Serializable
data class TtsEnginesResponse(
    val engines: List<TtsEngineResponse>,
    val requestId: String,
)

@Serializable
data class CameraListResponse(
    val cameras: List<CameraDescriptionResponse>,
    val requestId: String,
)

@Serializable
data class CameraDescriptionResponse(
    val id: String,
    val facing: String,
    val megapixels: Float? = null,
)

@Serializable
data class CameraCaptureRequest(
    val cameraId: String? = null,
    val facing: String = "back",
)

@Serializable
data class CameraCaptureResponse(
    val success: Boolean,
    val filePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val errorReason: String? = null,
    val requestId: String,
)

@Serializable
data class AudioRecordRequest(
    val durationSeconds: Int = 10,
    val format: String = "mp4",
    val quality: String = "medium",
)

@Serializable
data class AudioRecordResponse(
    val success: Boolean,
    val filePath: String? = null,
    val durationMillis: Long? = null,
    val sizeBytes: Long? = null,
    val errorReason: String? = null,
    val requestId: String,
)

@Serializable
data class SmsSendRequest(
    val recipients: List<String>,
    val message: String,
    val simSlot: Int? = null,
)

@Serializable
data class SmsSendResponse(
    val sent: Boolean,
    val recipientCount: Int,
    val errorReason: String? = null,
    val requestId: String,
)
