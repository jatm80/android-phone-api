package com.jatm.androidphoneapi

import android.content.Context
import com.jatm.androidphoneapi.pairing.PairingRepository
import com.jatm.androidphoneapi.pairing.SharedPreferencesPairingStore
import com.jatm.androidphoneapi.server.SystemTimeProvider

object AppGraph {
    @Volatile
    private var pairingRepository: PairingRepository? = null

    fun pairingRepository(context: Context): PairingRepository =
        pairingRepository ?: synchronized(this) {
            pairingRepository ?: PairingRepository(
                store = SharedPreferencesPairingStore(context.applicationContext),
                timeProvider = SystemTimeProvider,
            ).also { pairingRepository = it }
        }
}
