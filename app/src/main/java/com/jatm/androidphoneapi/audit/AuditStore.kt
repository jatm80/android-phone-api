package com.jatm.androidphoneapi.audit

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface AuditStore {
    fun append(event: AuditEvent)
    fun list(): List<AuditEvent>
    fun clear()
}

class InMemoryAuditStore(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) : AuditStore {
    private val events = mutableListOf<AuditEvent>()

    override fun append(event: AuditEvent) {
        events.add(event)
        while (events.size > maxEvents) {
            events.removeAt(0)
        }
    }

    override fun list(): List<AuditEvent> = events.toList()

    override fun clear() {
        events.clear()
    }
}

class SharedPreferencesAuditStore(
    context: Context,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val json: Json = defaultAuditJson,
) : AuditStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "audit_events",
        Context.MODE_PRIVATE,
    )

    override fun append(event: AuditEvent) {
        val current = loadEvents().toMutableList()
        current.add(event)
        while (current.size > maxEvents) {
            current.removeAt(0)
        }
        saveEvents(current)
    }

    override fun list(): List<AuditEvent> = loadEvents()

    override fun clear() {
        preferences.edit()
            .remove(KEY_EVENTS)
            .apply()
    }

    private fun loadEvents(): List<AuditEvent> {
        val encoded = preferences.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<AuditEvent>>(encoded)
        } catch (_: IllegalArgumentException) {
            emptyList()
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private fun saveEvents(events: List<AuditEvent>) {
        preferences.edit()
            .putString(KEY_EVENTS, json.encodeToString(events))
            .apply()
    }

    private companion object {
        const val KEY_EVENTS = "audit_events_json"
    }
}

const val DEFAULT_MAX_EVENTS = 500

val defaultAuditJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}
