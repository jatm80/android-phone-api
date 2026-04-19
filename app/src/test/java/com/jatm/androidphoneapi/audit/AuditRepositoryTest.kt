package com.jatm.androidphoneapi.audit

import com.jatm.androidphoneapi.apikey.ApiKeyAuditEvent
import com.jatm.androidphoneapi.apikey.ApiKeyAuditEventType
import com.jatm.androidphoneapi.server.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRepositoryTest {
    @Test
    fun loggedEventIsRetrievable() {
        val repository = repository()

        repository.log(auditEvent(type = ApiKeyAuditEventType.CREATED))

        val events = repository.events()
        assertEquals(1, events.size)
        assertEquals("CREATED", events[0].type)
        assertEquals("id-1", events[0].id)
    }

    @Test
    fun eventsPreserveAllFields() {
        val repository = repository()

        repository.log(
            ApiKeyAuditEvent(
                type = ApiKeyAuditEventType.AUTH_FAILED,
                timestampEpochMillis = 42_000L,
                requestId = "req-99",
                path = "/api/v1/battery",
                reason = "invalid",
            ),
        )

        val event = repository.events().single()
        assertEquals("AUTH_FAILED", event.type)
        assertEquals(42_000L, event.timestampEpochMillis)
        assertEquals("req-99", event.requestId)
        assertEquals("/api/v1/battery", event.path)
        assertEquals("invalid", event.reason)
    }

    @Test
    fun clearRemovesAllEvents() {
        val repository = repository()
        repository.log(auditEvent(type = ApiKeyAuditEventType.ENABLED))
        repository.log(auditEvent(type = ApiKeyAuditEventType.DISABLED))

        repository.clear()

        assertTrue(repository.events().isEmpty())
    }

    @Test
    fun inMemoryStoreDropsOldestWhenCapReached() {
        val cap = 5
        val store = InMemoryAuditStore(maxEvents = cap)
        val repository = AuditRepository(store = store, idGenerator = idSequence())

        repeat(cap + 3) { i ->
            repository.log(auditEvent(timestampEpochMillis = i.toLong()))
        }

        val events = repository.events()
        assertEquals(cap, events.size)
        assertEquals(3L, events.first().timestampEpochMillis)
        assertEquals(7L, events.last().timestampEpochMillis)
    }

    @Test
    fun defaultCapIs500() {
        val store = InMemoryAuditStore()
        val repository = AuditRepository(store = store, idGenerator = idSequence())

        repeat(502) { i ->
            repository.log(auditEvent(timestampEpochMillis = i.toLong()))
        }

        val events = repository.events()
        assertEquals(DEFAULT_MAX_EVENTS, events.size)
        assertEquals(2L, events.first().timestampEpochMillis)
        assertEquals(501L, events.last().timestampEpochMillis)
    }

    @Test
    fun auditEventDoesNotContainRawSecrets() {
        val repository = repository()

        repository.log(
            ApiKeyAuditEvent(
                type = ApiKeyAuditEventType.AUTH_FAILED,
                timestampEpochMillis = 1_000L,
                requestId = "req-1",
                path = "/api/v1/auth/check",
                reason = "invalid",
            ),
        )

        val event = repository.events().single()
        val serialized = event.toString()
        assertFalse(serialized.contains("apa_live_"))
        assertFalse(serialized.contains("Bearer"))
    }

    @Test
    fun logAccessPersistsEvent() {
        val repository = repository()

        repository.logAccess(
            type = "BATTERY_READ",
            requestId = "req-42",
            path = "/api/v1/battery",
        )

        val events = repository.events()
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals("BATTERY_READ", event.type)
        assertEquals("req-42", event.requestId)
        assertEquals("/api/v1/battery", event.path)
        assertEquals(99_000L, event.timestampEpochMillis)
    }

    @Test
    fun logAccessAppearsInEventsFlow() {
        val repository = repository()

        repository.logAccess(
            type = "DEVICE_READ",
            requestId = "req-7",
            path = "/api/v1/device",
        )

        val snapshot = repository.eventsFlow.value
        assertEquals(1, snapshot.size)
        assertEquals("DEVICE_READ", snapshot.single().type)
    }

    private fun repository(
        store: AuditStore = InMemoryAuditStore(),
        ids: MutableList<String> = mutableListOf("id-1", "id-2", "id-3", "id-4"),
    ): AuditRepository = AuditRepository(
        store = store,
        timeProvider = TimeProvider { 99_000L },
        idGenerator = { ids.removeAt(0) },
    )

    private fun idSequence(): () -> String {
        var counter = 0
        return { "id-${counter++}" }
    }

    private fun auditEvent(
        type: ApiKeyAuditEventType = ApiKeyAuditEventType.CREATED,
        timestampEpochMillis: Long = 1_000L,
    ) = ApiKeyAuditEvent(
        type = type,
        timestampEpochMillis = timestampEpochMillis,
    )
}
