package com.jatm.androidphoneapi.capabilities

class AndroidSmsSender : SmsSender {
    override fun send(request: SmsRequest): SmsResult =
        SmsResult(
            sent = false,
            recipientCount = 0,
            errorReason = "sms_requires_permission_and_approval",
        )
}
