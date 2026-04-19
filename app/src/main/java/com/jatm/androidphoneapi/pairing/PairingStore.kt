package com.jatm.androidphoneapi.pairing

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface PairingStore {
    fun load(): PairingState
    fun save(state: PairingState)
}

class InMemoryPairingStore(
    initialState: PairingState = PairingState(),
) : PairingStore {
    var storedState: PairingState = initialState
        private set

    override fun load(): PairingState = storedState

    override fun save(state: PairingState) {
        storedState = state
    }
}

class SharedPreferencesPairingStore(
    context: Context,
    private val json: Json = defaultPairingJson,
) : PairingStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "client_trust",
        Context.MODE_PRIVATE,
    )

    override fun load(): PairingState {
        val encoded = preferences.getString(KEY_PAIRING_STATE, null) ?: return PairingState()
        return try {
            json.decodeFromString<PairingState>(encoded)
        } catch (exception: IllegalArgumentException) {
            PairingState()
        } catch (exception: SerializationException) {
            PairingState()
        }
    }

    override fun save(state: PairingState) {
        preferences.edit()
            .putString(KEY_PAIRING_STATE, json.encodeToString(state))
            .apply()
    }

    private companion object {
        const val KEY_PAIRING_STATE = "pairing_state_json"
    }
}

val defaultPairingJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}
