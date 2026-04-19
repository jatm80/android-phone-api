package com.jatm.androidphoneapi.server

enum class TransportMode {
    HTTPS_REQUIRED,
    PLAINTEXT_DEBUG_ONLY,
}

data class ApiServerConfig(
    val bindHost: String = "0.0.0.0",
    val port: Int = 8443,
    val transportMode: TransportMode = TransportMode.HTTPS_REQUIRED,
) {
    companion object {
        fun forBuild(isDebug: Boolean): ApiServerConfig =
            if (isDebug) {
                ApiServerConfig(
                    bindHost = "127.0.0.1",
                    port = 8080,
                    transportMode = TransportMode.PLAINTEXT_DEBUG_ONLY,
                )
            } else {
                ApiServerConfig()
            }
    }
}
