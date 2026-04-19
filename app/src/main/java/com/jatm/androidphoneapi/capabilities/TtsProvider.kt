package com.jatm.androidphoneapi.capabilities

interface TtsProvider {
    fun speak(request: TtsRequest): TtsResult
    fun engines(): List<TtsEngineInfo>
}

data class TtsRequest(
    val text: String,
    val locale: String = "en-US",
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
)

data class TtsResult(
    val spoken: Boolean,
    val errorReason: String? = null,
)

data class TtsEngineInfo(
    val name: String,
    val label: String,
    val isDefault: Boolean,
)
