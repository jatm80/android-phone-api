package com.jatm.androidphoneapi

import android.content.Context
import com.jatm.androidphoneapi.apikey.AndroidKeystoreAesGcmCipher
import com.jatm.androidphoneapi.apikey.ApiKeyRepository
import com.jatm.androidphoneapi.apikey.SharedPreferencesApiKeyStore
import com.jatm.androidphoneapi.pairing.PairingRepository
import com.jatm.androidphoneapi.pairing.SharedPreferencesPairingStore
import com.jatm.androidphoneapi.server.SystemTimeProvider

object AppGraph {
    @Volatile
    private var pairingRepository: PairingRepository? = null
    @Volatile
    private var apiKeyRepository: ApiKeyRepository? = null

    fun pairingRepository(context: Context): PairingRepository =
        pairingRepository ?: synchronized(this) {
            pairingRepository ?: PairingRepository(
                store = SharedPreferencesPairingStore(context.applicationContext),
                timeProvider = SystemTimeProvider,
            ).also { pairingRepository = it }
        }

    fun apiKeyRepository(context: Context): ApiKeyRepository =
        apiKeyRepository ?: synchronized(this) {
            apiKeyRepository ?: ApiKeyRepository(
                store = SharedPreferencesApiKeyStore(context.applicationContext),
                cipher = AndroidKeystoreAesGcmCipher(),
                timeProvider = SystemTimeProvider,
            ).also { apiKeyRepository = it }
        }
}
