package com.jatm.androidphoneapi

import com.jatm.androidphoneapi.server.ApiServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiBaseUrlDisplayTest {
    @Test
    fun apiBaseUrlUsesPhoneAddressAndConfiguredPort() {
        val url = apiBaseUrlForDisplay(
            hostAddress = "192.168.1.42",
            config = ApiServerConfig(port = 9090),
        )

        assertEquals("http://192.168.1.42:9090/api/v1", url)
    }

    @Test
    fun apiBaseUrlUsesPlaceholderWhenPhoneAddressIsUnknown() {
        val url = apiBaseUrlForDisplay(hostAddress = null)

        assertEquals("http://<phone ip>:8080/api/v1", url)
    }
}
