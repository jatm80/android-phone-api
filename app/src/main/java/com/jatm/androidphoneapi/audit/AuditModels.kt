package com.jatm.androidphoneapi.audit

import kotlinx.serialization.Serializable

@Serializable
data class AuditEvent(
    val id: String,
    val type: String,
    val timestampEpochMillis: Long,
    val requestId: String? = null,
    val path: String? = null,
    val reason: String? = null,
)
