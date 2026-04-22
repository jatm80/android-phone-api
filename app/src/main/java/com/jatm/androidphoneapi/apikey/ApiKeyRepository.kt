package com.jatm.androidphoneapi.apikey

import com.jatm.androidphoneapi.server.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import java.util.UUID

interface ApiKeyAuthenticator {
    fun authenticate(
        candidate: String?,
        requestId: String? = null,
        path: String? = null,
    ): ApiKeyAuthResult
}

class ApiKeyRepository(
    private val store: ApiKeyStore,
    private val cipher: SecretCipher,
    private val timeProvider: TimeProvider,
    private val keyGenerator: ApiKeyGenerator = SecureRandomApiKeyGenerator(),
    private val saltGenerator: SaltGenerator = SecureRandomSaltGenerator(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val auditLogger: ApiKeyAuditLogger = NoOpApiKeyAuditLogger,
) : ApiKeyAuthenticator {
    private val mutableState = MutableStateFlow(ApiKeyUiState())
    private var persistedState: PersistedApiKeyState? = null

    val state: StateFlow<ApiKeyUiState> = mutableState.asStateFlow()

    init {
        val loaded = store.load()
        if (loaded == null) {
            createInitialKey()
        } else {
            persistedState = loaded
            mutableState.value = loaded.toUiState()
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        val current = persistedState ?: createInitialKey()
        if (current.enabled == enabled) return

        val now = timeProvider.nowEpochMillis()
        val updated = current.copy(
            enabled = enabled,
            updatedAtEpochMillis = now,
        )
        save(updated, presentedKey = mutableState.value.presentedKey)
        auditLogger.log(
            ApiKeyAuditEvent(
                type = if (enabled) ApiKeyAuditEventType.ENABLED else ApiKeyAuditEventType.DISABLED,
                timestampEpochMillis = now,
            ),
        )
    }

    @Synchronized
    fun resetKey(): String {
        val enabled = persistedState?.enabled ?: false
        val generated = newPersistedState(enabled = enabled)
        val rawKey = cipher.decrypt(generated.encryptedApiKey)
        save(generated, presentedKey = rawKey)
        auditLogger.log(
            ApiKeyAuditEvent(
                type = ApiKeyAuditEventType.RESET,
                timestampEpochMillis = generated.updatedAtEpochMillis,
            ),
        )
        return rawKey
    }

    @Synchronized
    fun presentKey(): String {
        val current = persistedState ?: createInitialKey()
        val rawKey = cipher.decrypt(current.encryptedApiKey)
        mutableState.value = current.toUiState(presentedKey = rawKey)
        auditLogger.log(
            ApiKeyAuditEvent(
                type = ApiKeyAuditEventType.PRESENTED,
                timestampEpochMillis = timeProvider.nowEpochMillis(),
            ),
        )
        return rawKey
    }

    @Synchronized
    fun toggleKeyPresentation() {
        val current = persistedState ?: createInitialKey()
        if (mutableState.value.presentedKey != null) {
            mutableState.value = current.toUiState(presentedKey = null)
        } else {
            presentKey()
        }
    }

    @Synchronized
    override fun authenticate(
        candidate: String?,
        requestId: String?,
        path: String?,
    ): ApiKeyAuthResult {
        val current = persistedState ?: createInitialKey()
        if (!current.enabled) {
            auditAuthFailure(requestId, path, "disabled")
            return ApiKeyAuthResult.Disabled
        }

        val presented = candidate?.trim()
        if (presented.isNullOrEmpty()) {
            auditAuthFailure(requestId, path, "missing")
            return ApiKeyAuthResult.MissingOrInvalid
        }

        val salt = Base64.getDecoder().decode(current.saltBase64)
        val authenticated = ApiKeyHasher.matches(
            apiKey = presented,
            salt = salt,
            expectedHashHex = current.keyHashSha256,
        )
        if (!authenticated) {
            auditAuthFailure(requestId, path, "invalid")
            return ApiKeyAuthResult.MissingOrInvalid
        }
        return ApiKeyAuthResult.Authenticated
    }

    private fun createInitialKey(): PersistedApiKeyState {
        val created = newPersistedState(enabled = false)
        save(created, presentedKey = null)
        auditLogger.log(
            ApiKeyAuditEvent(
                type = ApiKeyAuditEventType.CREATED,
                timestampEpochMillis = created.createdAtEpochMillis,
            ),
        )
        return created
    }

    private fun newPersistedState(enabled: Boolean): PersistedApiKeyState {
        val now = timeProvider.nowEpochMillis()
        val rawKey = keyGenerator.generate()
        val salt = saltGenerator.generateSalt()
        return PersistedApiKeyState(
            enabled = enabled,
            keyId = idGenerator(),
            keyHashSha256 = ApiKeyHasher.hashSha256(rawKey, salt),
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            encryptedApiKey = cipher.encrypt(rawKey),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
    }

    private fun save(state: PersistedApiKeyState, presentedKey: String?) {
        persistedState = state
        store.save(state)
        mutableState.value = state.toUiState(presentedKey)
    }

    private fun PersistedApiKeyState.toUiState(presentedKey: String? = null): ApiKeyUiState =
        ApiKeyUiState(
            enabled = enabled,
            keyId = keyId,
            hasKey = true,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            presentedKey = presentedKey,
        )

    private fun auditAuthFailure(requestId: String?, path: String?, reason: String) {
        auditLogger.log(
            ApiKeyAuditEvent(
                type = ApiKeyAuditEventType.AUTH_FAILED,
                timestampEpochMillis = timeProvider.nowEpochMillis(),
                requestId = requestId,
                path = path,
                reason = reason,
            ),
        )
    }
}
