package com.jatm.androidphoneapi.audit

interface ApiAuditLogger {
    fun logAccess(
        type: String,
        requestId: String? = null,
        path: String? = null,
        reason: String? = null,
    )
}

object NoOpApiAuditLogger : ApiAuditLogger {
    override fun logAccess(type: String, requestId: String?, path: String?, reason: String?) = Unit
}
