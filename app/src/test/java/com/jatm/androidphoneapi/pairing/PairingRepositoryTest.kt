package com.jatm.androidphoneapi.pairing

import com.jatm.androidphoneapi.server.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRepositoryTest {
    private val publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILocalHomelabClientKey"

    @Test
    fun creatingPairingRequestDoesNotCreateTrustedClient() {
        val repository = repository()

        val request = repository.createPending(createRequest())

        assertEquals(PairingRequestStatus.PENDING, request.status)
        assertEquals(1, repository.state.value.pendingRequests.size)
        assertTrue(repository.state.value.trustedClients.isEmpty())
    }

    @Test
    fun approvingPendingRequestCreatesTrustedClientAndPersists() {
        val store = InMemoryPairingStore()
        val repository = repository(store = store, ids = mutableListOf("pairing-1", "client-1"))
        repository.createPending(createRequest())

        val client = repository.approvePairing("pairing-1")

        assertEquals("client-1", client.id)
        assertEquals("workstation", client.displayName)
        assertTrue(client.isActive)
        assertEquals(PairingRequestStatus.APPROVED, store.storedState.pairingRequests.single().status)
        assertEquals("client-1", store.storedState.pairingRequests.single().clientId)
        assertEquals(client, store.storedState.trustedClients.single())
    }

    @Test
    fun denyingPendingRequestDoesNotCreateTrustedClient() {
        val repository = repository(ids = mutableListOf("pairing-1"))
        repository.createPending(createRequest())

        repository.denyPairing("pairing-1")

        assertEquals(PairingRequestStatus.DENIED, repository.state.value.pairingRequests.single().status)
        assertTrue(repository.state.value.trustedClients.isEmpty())
    }

    @Test
    fun expiredPendingRequestCannotBeApproved() {
        val clock = MutableTimeProvider(1_000L)
        val repository = repository(clock = clock, ids = mutableListOf("pairing-1"), ttlMillis = 100L)
        repository.createPending(createRequest())
        clock.now = 1_101L

        assertThrows(PairingExpiredException::class.java) {
            repository.approvePairing("pairing-1")
        }
        assertEquals(PairingRequestStatus.EXPIRED, repository.state.value.pairingRequests.single().status)
        assertTrue(repository.state.value.trustedClients.isEmpty())
    }

    @Test
    fun revokingClientRemovesItFromActiveLookup() {
        val repository = repository(ids = mutableListOf("pairing-1", "client-1"))
        val pending = repository.createPending(createRequest())
        val client = repository.approvePairing("pairing-1")

        assertNotNull(repository.findActiveTrustedClientByFingerprint(pending.publicKeyFingerprintSha256))

        repository.revokeClient(client.id)

        assertFalse(repository.state.value.trustedClients.single().isActive)
        assertNull(repository.findActiveTrustedClientByFingerprint(pending.publicKeyFingerprintSha256))
    }

    @Test
    fun duplicateActivePublicKeyIsRejected() {
        val repository = repository(ids = mutableListOf("pairing-1", "client-1", "pairing-2"))
        repository.createPending(createRequest())
        repository.approvePairing("pairing-1")

        assertThrows(PairingValidationException::class.java) {
            repository.createPending(createRequest())
        }
    }

    @Test
    fun persistedTrustedClientsReload() {
        val store = InMemoryPairingStore()
        val firstRepository = repository(store = store, ids = mutableListOf("pairing-1", "client-1"))
        firstRepository.createPending(createRequest())
        firstRepository.approvePairing("pairing-1")

        val secondRepository = repository(store = store)

        assertEquals("client-1", secondRepository.state.value.trustedClients.single().id)
    }

    private fun repository(
        store: InMemoryPairingStore = InMemoryPairingStore(),
        clock: MutableTimeProvider = MutableTimeProvider(1_000L),
        ids: MutableList<String> = mutableListOf("pairing-1", "client-1"),
        ttlMillis: Long = PairingRepository.DEFAULT_PAIRING_TTL_MILLIS,
    ): PairingRepository =
        PairingRepository(
            store = store,
            timeProvider = clock,
            idGenerator = { ids.removeAt(0) },
            verificationCodeGenerator = { "123456" },
            pairingTtlMillis = ttlMillis,
        )

    private fun createRequest(): CreatePairingRequest =
        CreatePairingRequest(
            clientName = "workstation",
            clientPublicKey = publicKey,
        )

    private class MutableTimeProvider(
        var now: Long,
    ) : TimeProvider {
        override fun nowEpochMillis(): Long = now
    }
}
