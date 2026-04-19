package com.jatm.androidphoneapi.capabilities

interface SmsSender {
    fun send(request: SmsRequest): SmsResult
}

data class SmsRequest(
    val recipients: List<String>,
    val message: String,
    val simSlot: Int? = null,
)

data class SmsResult(
    val sent: Boolean,
    val recipientCount: Int,
    val errorReason: String? = null,
)
