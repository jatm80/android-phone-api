package com.jatm.androidphoneapi.pairing

import kotlinx.serialization.Serializable

@Serializable
data class PairingState(
    val pairingRequests: List<PairingRequestRecord> = emptyList(),
    val trustedClients: List<TrustedClient> = emptyList(),
) {
    val pendingRequests: List<PairingRequestRecord>
        get() = pairingRequests.filter { it.status == PairingRequestStatus.PENDING }
}

@Serializable
data class PairingRequestRecord(
    val id: String,
    val displayName: String,
    val publicKeyMaterial: String,
    val publicKeyFingerprintSha256: String,
    val keyType: String,
    val verificationCode: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val status: PairingRequestStatus = PairingRequestStatus.PENDING,
    val clientId: String? = null,
)

@Serializable
enum class PairingRequestStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
}

@Serializable
data class TrustedClient(
    val id: String,
    val displayName: String,
    val publicKeyMaterial: String,
    val publicKeyFingerprintSha256: String,
    val keyType: String,
    val approvedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null,
) {
    val isActive: Boolean
        get() = revokedAtEpochMillis == null
}

@Serializable
data class CreatePairingRequest(
    val clientName: String,
    val clientPublicKey: String,
    val clientKeyType: String = "ed25519",
)

@Serializable
data class CreatePairingResponse(
    val pairingId: String,
    val status: String,
    val verificationCode: String,
    val expiresAtEpochMillis: Long,
    val requestId: String,
)

@Serializable
data class PairingStatusResponse(
    val pairingId: String,
    val status: String,
    val clientId: String? = null,
    val expiresAtEpochMillis: Long,
    val requestId: String,
)

fun PairingRequestStatus.apiValue(): String = name.lowercase()
