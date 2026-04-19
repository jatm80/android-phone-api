package com.jatm.androidphoneapi.audit

import com.jatm.androidphoneapi.apikey.ApiKeyAuditEvent
import com.jatm.androidphoneapi.apikey.ApiKeyAuditLogger
import com.jatm.androidphoneapi.server.SystemTimeProvider
import com.jatm.androidphoneapi.server.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AuditRepository(
    private val store: AuditStore,
    private val timeProvider: TimeProvider = SystemTimeProvider,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ApiKeyAuditLogger, ApiAuditLogger {

    private val mutableEvents = MutableStateFlow(store.list())
    val eventsFlow: StateFlow<List<AuditEvent>> = mutableEvents.asStateFlow()

    override fun log(event: ApiKeyAuditEvent) {
        store.append(
            AuditEvent(
                id = idGenerator(),
                type = event.type.name,
                timestampEpochMillis = event.timestampEpochMillis,
                requestId = event.requestId,
                path = event.path,
                reason = event.reason,
            ),
        )
        mutableEvents.value = store.list()
    }

    override fun logAccess(type: String, requestId: String?, path: String?, reason: String?) {
        store.append(
            AuditEvent(
                id = idGenerator(),
                type = type,
                timestampEpochMillis = timeProvider.nowEpochMillis(),
                requestId = requestId,
                path = path,
                reason = reason,
            ),
        )
        mutableEvents.value = store.list()
    }

    fun events(): List<AuditEvent> = store.list()

    fun clear() {
        store.clear()
        mutableEvents.value = store.list()
    }
}
