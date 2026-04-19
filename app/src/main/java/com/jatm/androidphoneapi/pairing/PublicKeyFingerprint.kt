package com.jatm.androidphoneapi.pairing

import java.security.MessageDigest

fun interface PublicKeyFingerprint {
    fun fingerprintSha256(publicKeyMaterial: String): String
}

object Sha256PublicKeyFingerprint : PublicKeyFingerprint {
    override fun fingerprintSha256(publicKeyMaterial: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyMaterial.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString(":") { byte -> "%02x".format(byte) }
    }
}
