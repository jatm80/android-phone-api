package com.jatm.androidphoneapi

import android.content.Context
import com.jatm.androidphoneapi.apikey.AndroidKeystoreAesGcmCipher
import com.jatm.androidphoneapi.apikey.ApiKeyRepository
import com.jatm.androidphoneapi.apikey.SharedPreferencesApiKeyStore
import com.jatm.androidphoneapi.audit.AuditRepository
import com.jatm.androidphoneapi.audit.SharedPreferencesAuditStore
import com.jatm.androidphoneapi.server.SystemTimeProvider

object AppGraph {
    @Volatile
    private var apiKeyRepository: ApiKeyRepository? = null
    @Volatile
    private var auditRepository: AuditRepository? = null

    fun auditRepository(context: Context): AuditRepository =
        auditRepository ?: synchronized(this) {
            auditRepository ?: AuditRepository(
                store = SharedPreferencesAuditStore(context.applicationContext),
            ).also { auditRepository = it }
        }

    fun apiKeyRepository(context: Context): ApiKeyRepository =
        apiKeyRepository ?: synchronized(this) {
            apiKeyRepository ?: ApiKeyRepository(
                store = SharedPreferencesApiKeyStore(context.applicationContext),
                cipher = AndroidKeystoreAesGcmCipher(),
                timeProvider = SystemTimeProvider,
                auditLogger = auditRepository(context),
            ).also { apiKeyRepository = it }
        }
}
