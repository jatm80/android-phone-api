package com.jatm.androidphoneapi.audit

import com.jatm.androidphoneapi.apikey.ApiKeyAuditEvent
import com.jatm.androidphoneapi.apikey.ApiKeyAuditLogger
import java.util.UUID

class AuditRepository(
    private val store: AuditStore,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ApiKeyAuditLogger {
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
    }

    fun events(): List<AuditEvent> = store.list()

    fun clear() = store.clear()
}
