package com.jatm.androidphoneapi.server

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val service: String = "android-phone-api",
    val apiVersion: String = "v1",
    val serverTimeEpochMillis: Long,
    val requestId: String,
)
