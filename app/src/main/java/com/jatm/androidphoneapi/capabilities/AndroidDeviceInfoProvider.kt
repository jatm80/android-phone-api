package com.jatm.androidphoneapi.capabilities

import android.os.Build
import android.os.SystemClock

class AndroidDeviceInfoProvider : DeviceInfoProvider {
    override fun deviceInfo(): DeviceInfo = DeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        brand = Build.BRAND,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        androidVersion = Build.VERSION.RELEASE,
        sdkVersion = Build.VERSION.SDK_INT,
        securityPatch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else null,
        uptimeMillis = SystemClock.elapsedRealtime(),
    )
}
