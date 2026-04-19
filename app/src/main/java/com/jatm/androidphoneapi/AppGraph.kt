package com.jatm.androidphoneapi

import android.content.Context
import com.jatm.androidphoneapi.apikey.AndroidKeystoreAesGcmCipher
import com.jatm.androidphoneapi.apikey.ApiKeyRepository
import com.jatm.androidphoneapi.apikey.SharedPreferencesApiKeyStore
import com.jatm.androidphoneapi.server.SystemTimeProvider

object AppGraph {
    @Volatile
    private var apiKeyRepository: ApiKeyRepository? = null

    fun apiKeyRepository(context: Context): ApiKeyRepository =
        apiKeyRepository ?: synchronized(this) {
            apiKeyRepository ?: ApiKeyRepository(
                store = SharedPreferencesApiKeyStore(context.applicationContext),
                cipher = AndroidKeystoreAesGcmCipher(),
                timeProvider = SystemTimeProvider,
            ).also { apiKeyRepository = it }
        }
}
