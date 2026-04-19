package com.jatm.androidphoneapi.apikey

import kotlinx.serialization.Serializable

@Serializable
data class PersistedApiKeyState(
    val enabled: Boolean,
    val keyId: String,
    val keyHashSha256: String,
    val saltBase64: String,
    val encryptedApiKey: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ApiKeyUiState(
    val enabled: Boolean = false,
    val keyId: String = "",
    val hasKey: Boolean = false,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val presentedKey: String? = null,
)

sealed interface ApiKeyAuthResult {
    data object Authenticated : ApiKeyAuthResult
    data object Disabled : ApiKeyAuthResult
    data object MissingOrInvalid : ApiKeyAuthResult
}

enum class ApiKeyAuditEventType {
    CREATED,
    ENABLED,
    DISABLED,
    RESET,
    PRESENTED,
    AUTH_FAILED,
}

data class ApiKeyAuditEvent(
    val type: ApiKeyAuditEventType,
    val timestampEpochMillis: Long,
    val requestId: String? = null,
    val path: String? = null,
    val reason: String? = null,
)

fun interface ApiKeyAuditLogger {
    fun log(event: ApiKeyAuditEvent)
}

object NoOpApiKeyAuditLogger : ApiKeyAuditLogger {
    override fun log(event: ApiKeyAuditEvent) = Unit
}
