package com.jatm.androidphoneapi.apikey

import com.jatm.androidphoneapi.server.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyRepositoryTest {
    @Test
    fun firstInitializationCreatesPersistedVerifierWithoutRawKey() {
        val store = InMemoryApiKeyStore()
        val repository = repository(store = store)

        val persisted = store.storedState

        assertTrue(repository.state.value.hasKey)
        assertEquals("key-id-1", persisted?.keyId)
        assertFalse(persisted?.keyHashSha256.orEmpty().contains("apa_live_test_1"))
        assertFalse(persisted?.saltBase64.orEmpty().contains("apa_live_test_1"))
        assertFalse(persisted?.encryptedApiKey.orEmpty().contains("apa_live_test_1"))
    }

    @Test
    fun generatedKeyHasExpectedShapeAndChangesOnReset() {
        val repository = repository(keys = mutableListOf("apa_live_test_1", "apa_live_test_2"))

        val first = repository.presentKey()
        val second = repository.resetKey()

        assertTrue(first.startsWith("apa_live_"))
        assertTrue(second.startsWith("apa_live_"))
        assertNotEquals(first, second)
    }

    @Test
    fun validKeyAuthenticatesOnlyWhenEnabled() {
        val repository = repository()
        val key = repository.presentKey()

        assertEquals(ApiKeyAuthResult.Disabled, repository.authenticate(key))

        repository.setEnabled(true)

        assertEquals(ApiKeyAuthResult.Authenticated, repository.authenticate(key))
    }

    @Test
    fun wrongKeyFails() {
        val repository = repository()
        repository.setEnabled(true)

        assertEquals(ApiKeyAuthResult.MissingOrInvalid, repository.authenticate("wrong"))
    }

    @Test
    fun resetInvalidatesOldKeyImmediately() {
        val repository = repository(keys = mutableListOf("apa_live_test_1", "apa_live_test_2"))
        val oldKey = repository.presentKey()
        repository.setEnabled(true)

        val newKey = repository.resetKey()

        assertEquals(ApiKeyAuthResult.MissingOrInvalid, repository.authenticate(oldKey))
        assertEquals(ApiKeyAuthResult.Authenticated, repository.authenticate(newKey))
    }

    @Test
    fun enableDisableStateSurvivesReload() {
        val store = InMemoryApiKeyStore()
        repository(store = store).setEnabled(true)

        val reloaded = repository(store = store)

        assertTrue(reloaded.state.value.enabled)
        assertEquals("key-id-1", reloaded.state.value.keyId)
        assertNull(reloaded.state.value.presentedKey)
    }

    @Test
    fun revealDoesNotMutatePersistedVerifier() {
        val store = InMemoryApiKeyStore()
        val repository = repository(store = store)
        val before = store.storedState

        repository.presentKey()

        assertEquals(before, store.storedState)
    }

    @Test
    fun authFailureAuditDoesNotContainRawKey() {
        val auditEvents = mutableListOf<ApiKeyAuditEvent>()
        val repository = repository(auditEvents = auditEvents)
        repository.setEnabled(true)

        repository.authenticate("apa_live_secret", requestId = "req-1", path = "/api/v1/auth/check")

        val event = auditEvents.last()
        assertEquals(ApiKeyAuditEventType.AUTH_FAILED, event.type)
        assertEquals("req-1", event.requestId)
        assertEquals("/api/v1/auth/check", event.path)
        assertFalse(event.toString().contains("apa_live_secret"))
    }

    private fun repository(
        store: InMemoryApiKeyStore = InMemoryApiKeyStore(),
        keys: MutableList<String> = mutableListOf("apa_live_test_1", "apa_live_test_2"),
        ids: MutableList<String> = mutableListOf("key-id-1", "key-id-2"),
        auditEvents: MutableList<ApiKeyAuditEvent> = mutableListOf(),
    ): ApiKeyRepository =
        ApiKeyRepository(
            store = store,
            cipher = ReversingCipher,
            timeProvider = TimeProvider { 1_000L },
            keyGenerator = ApiKeyGenerator { keys.removeAt(0) },
            saltGenerator = SaltGenerator { "fixed-test-salt!".toByteArray(Charsets.UTF_8) },
            idGenerator = { ids.removeAt(0) },
            auditLogger = ApiKeyAuditLogger { auditEvents.add(it) },
        )

    private object ReversingCipher : SecretCipher {
        override fun encrypt(plaintext: String): String =
            plaintext.reversed()

        override fun decrypt(encodedCiphertext: String): String =
            encodedCiphertext.reversed()
    }
}
