package com.jatm.androidphoneapi.apikey

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface ApiKeyStore {
    fun load(): PersistedApiKeyState?
    fun save(state: PersistedApiKeyState)
}

class InMemoryApiKeyStore(
    initialState: PersistedApiKeyState? = null,
) : ApiKeyStore {
    var storedState: PersistedApiKeyState? = initialState
        private set

    override fun load(): PersistedApiKeyState? = storedState

    override fun save(state: PersistedApiKeyState) {
        storedState = state
    }
}

class SharedPreferencesApiKeyStore(
    context: Context,
    private val json: Json = defaultApiKeyJson,
) : ApiKeyStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "api_key_auth",
        Context.MODE_PRIVATE,
    )

    override fun load(): PersistedApiKeyState? {
        val encoded = preferences.getString(KEY_STATE, null) ?: return null
        return try {
            json.decodeFromString<PersistedApiKeyState>(encoded)
        } catch (exception: IllegalArgumentException) {
            null
        } catch (exception: SerializationException) {
            null
        }
    }

    override fun save(state: PersistedApiKeyState) {
        preferences.edit()
            .putString(KEY_STATE, json.encodeToString(state))
            .apply()
    }

    private companion object {
        const val KEY_STATE = "api_key_state_json"
    }
}

val defaultApiKeyJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}
