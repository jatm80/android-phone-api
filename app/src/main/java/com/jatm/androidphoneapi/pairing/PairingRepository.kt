package com.jatm.androidphoneapi.pairing

import com.jatm.androidphoneapi.server.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID

class PairingRepository(
    private val store: PairingStore,
    private val timeProvider: TimeProvider,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val verificationCodeGenerator: () -> String = { VerificationCodes.nextCode() },
    private val fingerprint: PublicKeyFingerprint = Sha256PublicKeyFingerprint,
    private val pairingTtlMillis: Long = DEFAULT_PAIRING_TTL_MILLIS,
) {
    private val mutableState = MutableStateFlow(expirePendingRequests(store.load()))

    val state: StateFlow<PairingState> = mutableState.asStateFlow()

    @Synchronized
    fun createPending(request: CreatePairingRequest): PairingRequestRecord {
        val now = timeProvider.nowEpochMillis()
        validatePairingRequest(request)

        val current = expirePendingRequests(mutableState.value, now)
        val publicKeyFingerprint = fingerprint.fingerprintSha256(request.clientPublicKey)
        if (current.trustedClients.any { it.isActive && it.publicKeyFingerprintSha256 == publicKeyFingerprint }) {
            throw PairingValidationException("Client public key is already trusted")
        }

        val pairingRequest = PairingRequestRecord(
            id = idGenerator(),
            displayName = request.clientName.trim(),
            publicKeyMaterial = request.clientPublicKey.trim(),
            publicKeyFingerprintSha256 = publicKeyFingerprint,
            keyType = request.clientKeyType,
            verificationCode = verificationCodeGenerator(),
            createdAtEpochMillis = now,
            expiresAtEpochMillis = now + pairingTtlMillis,
        )

        updateState(current.copy(pairingRequests = current.pairingRequests + pairingRequest))
        return pairingRequest
    }

    @Synchronized
    fun getPairingRequest(pairingId: String): PairingRequestRecord? {
        val current = expirePendingRequests(mutableState.value)
        updateState(current)
        return current.pairingRequests.firstOrNull { it.id == pairingId }
    }

    @Synchronized
    fun approvePairing(pairingId: String): TrustedClient {
        val now = timeProvider.nowEpochMillis()
        val current = expirePendingRequests(mutableState.value, now)
        val pairingRequest = current.pairingRequests.firstOrNull { it.id == pairingId }
            ?: throw PairingNotFoundException(pairingId)

        if (pairingRequest.status == PairingRequestStatus.EXPIRED) {
            updateState(current)
            throw PairingExpiredException(pairingId)
        }
        if (pairingRequest.status != PairingRequestStatus.PENDING) {
            throw PairingNotPendingException(pairingId)
        }
        if (current.trustedClients.any {
                it.isActive && it.publicKeyFingerprintSha256 == pairingRequest.publicKeyFingerprintSha256
            }
        ) {
            throw PairingValidationException("Client public key is already trusted")
        }

        val client = TrustedClient(
            id = idGenerator(),
            displayName = pairingRequest.displayName,
            publicKeyMaterial = pairingRequest.publicKeyMaterial,
            publicKeyFingerprintSha256 = pairingRequest.publicKeyFingerprintSha256,
            keyType = pairingRequest.keyType,
            approvedAtEpochMillis = now,
        )

        val updatedRequests = current.pairingRequests.map {
            if (it.id == pairingId) {
                it.copy(
                    status = PairingRequestStatus.APPROVED,
                    clientId = client.id,
                )
            } else {
                it
            }
        }
        updateState(
            current.copy(
                pairingRequests = updatedRequests,
                trustedClients = current.trustedClients + client,
            ),
        )
        return client
    }

    @Synchronized
    fun denyPairing(pairingId: String) {
        val current = expirePendingRequests(mutableState.value)
        val pairingRequest = current.pairingRequests.firstOrNull { it.id == pairingId }
            ?: throw PairingNotFoundException(pairingId)
        if (pairingRequest.status != PairingRequestStatus.PENDING) {
            throw PairingNotPendingException(pairingId)
        }

        updateState(
            current.copy(
                pairingRequests = current.pairingRequests.map {
                    if (it.id == pairingId) {
                        it.copy(status = PairingRequestStatus.DENIED)
                    } else {
                        it
                    }
                },
            ),
        )
    }

    @Synchronized
    fun revokeClient(clientId: String) {
        val now = timeProvider.nowEpochMillis()
        val current = mutableState.value
        val updatedClients = current.trustedClients.map {
            if (it.id == clientId && it.revokedAtEpochMillis == null) {
                it.copy(revokedAtEpochMillis = now)
            } else {
                it
            }
        }
        updateState(current.copy(trustedClients = updatedClients))
    }

    fun findActiveTrustedClientByFingerprint(fingerprintSha256: String): TrustedClient? =
        mutableState.value.trustedClients.firstOrNull {
            it.isActive && it.publicKeyFingerprintSha256 == fingerprintSha256
        }

    private fun expirePendingRequests(
        state: PairingState,
        now: Long = timeProvider.nowEpochMillis(),
    ): PairingState {
        val updatedRequests = state.pairingRequests.map {
            if (it.status == PairingRequestStatus.PENDING && it.expiresAtEpochMillis <= now) {
                it.copy(status = PairingRequestStatus.EXPIRED)
            } else {
                it
            }
        }
        return state.copy(pairingRequests = updatedRequests)
    }

    private fun updateState(state: PairingState) {
        mutableState.value = state
        store.save(state)
    }

    private fun validatePairingRequest(request: CreatePairingRequest) {
        val clientName = request.clientName.trim()
        val publicKey = request.clientPublicKey.trim()

        if (clientName.isEmpty() || clientName.length > MAX_CLIENT_NAME_LENGTH) {
            throw PairingValidationException("Client name must be 1-$MAX_CLIENT_NAME_LENGTH characters")
        }
        if (clientName.any { it.isISOControl() }) {
            throw PairingValidationException("Client name contains control characters")
        }
        if (publicKey.length !in MIN_PUBLIC_KEY_LENGTH..MAX_PUBLIC_KEY_LENGTH) {
            throw PairingValidationException("Client public key length is invalid")
        }
        if (publicKey.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
            throw PairingValidationException("Client public key contains invalid characters")
        }
        if (request.clientKeyType !in SUPPORTED_KEY_TYPES) {
            throw PairingValidationException("Unsupported client key type")
        }
    }

    companion object {
        const val DEFAULT_PAIRING_TTL_MILLIS = 5 * 60 * 1_000L
        private const val MAX_CLIENT_NAME_LENGTH = 64
        private const val MIN_PUBLIC_KEY_LENGTH = 32
        private const val MAX_PUBLIC_KEY_LENGTH = 4_096
        private val SUPPORTED_KEY_TYPES = setOf("ed25519")
    }
}

class PairingValidationException(message: String) : IllegalArgumentException(message)
class PairingNotFoundException(pairingId: String) : NoSuchElementException(pairingId)
class PairingExpiredException(pairingId: String) : IllegalStateException(pairingId)
class PairingNotPendingException(pairingId: String) : IllegalStateException(pairingId)

private object VerificationCodes {
    private val secureRandom = SecureRandom()

    fun nextCode(): String = secureRandom.nextInt(1_000_000).toString().padStart(6, '0')
}
