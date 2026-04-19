package com.jatm.androidphoneapi.capabilities

interface BatteryInfoProvider {
    fun batteryInfo(): BatteryInfo
}

data class BatteryInfo(
    val level: Int,
    val scale: Int,
    val percentage: Int,
    val status: String,
    val health: String,
    val plugged: String,
    val technology: String?,
    val temperature: Float,
    val voltage: Float,
)
