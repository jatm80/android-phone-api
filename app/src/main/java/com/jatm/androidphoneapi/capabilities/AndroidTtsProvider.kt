package com.jatm.androidphoneapi.capabilities

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AndroidTtsProvider(
    private val context: Context,
) : TtsProvider {
    private var tts: TextToSpeech? = null

    @Volatile
    private var initialized = false

    init {
        val latch = CountDownLatch(1)
        tts = TextToSpeech(context) { status ->
            initialized = status == TextToSpeech.SUCCESS
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    override fun speak(request: TtsRequest): TtsResult {
        val engine = tts
        if (engine == null || !initialized) {
            return TtsResult(spoken = false, errorReason = "tts_not_ready")
        }

        val parts = request.locale.split("-", "_")
        val locale = when (parts.size) {
            1 -> Locale(parts[0])
            else -> Locale(parts[0], parts[1])
        }
        engine.language = locale
        engine.setSpeechRate(request.rate)
        engine.setPitch(request.pitch)

        val result = engine.speak(request.text, TextToSpeech.QUEUE_ADD, null, null)
        return if (result == TextToSpeech.SUCCESS) {
            TtsResult(spoken = true)
        } else {
            TtsResult(spoken = false, errorReason = "tts_speak_failed")
        }
    }

    override fun engines(): List<TtsEngineInfo> {
        val engine = tts ?: return emptyList()
        val defaultEngine = engine.defaultEngine
        return engine.engines.map { info ->
            TtsEngineInfo(
                name = info.name,
                label = info.label,
                isDefault = info.name == defaultEngine,
            )
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
