package com.jatm.androidphoneapi.capabilities

interface DeviceInfoProvider {
    fun deviceInfo(): DeviceInfo
}

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val securityPatch: String?,
    val uptimeMillis: Long,
)
