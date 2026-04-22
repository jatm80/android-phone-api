package com.jatm.androidphoneapi.server

enum class TransportMode {
    PLAINTEXT_LOCAL_NETWORK,
}

data class ApiServerConfig(
    val bindHost: String = "0.0.0.0",
    val port: Int = 8080,
    val transportMode: TransportMode = TransportMode.PLAINTEXT_LOCAL_NETWORK,
) {
    companion object {
        fun localNetworkHttp(): ApiServerConfig =
            ApiServerConfig()
    }
}
