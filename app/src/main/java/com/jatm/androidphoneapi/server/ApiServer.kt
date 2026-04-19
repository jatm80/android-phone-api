package com.jatm.androidphoneapi.server

import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

interface ApiServer {
    fun start()
    fun stop()
}

class EmbeddedKtorApiServer(
    private val config: ApiServerConfig = ApiServerConfig(),
    private val logger: RequestOutcomeLogger = NoOpRequestOutcomeLogger,
    private val timeProvider: TimeProvider = SystemTimeProvider,
) : ApiServer {
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    override fun start() {
        if (engine != null) return

        if (config.transportMode == TransportMode.HTTPS_REQUIRED) {
            throw TlsConfigurationRequiredException()
        }

        engine = embeddedServer(
            factory = CIO,
            host = config.bindHost,
            port = config.port,
        ) {
            apiServerModule(
                timeProvider = timeProvider,
                logger = logger,
            )
        }.start(wait = false)
    }

    override fun stop() {
        engine?.stop(
            gracePeriodMillis = 1_000,
            timeoutMillis = 3_000,
        )
        engine = null
    }
}

class TlsConfigurationRequiredException : IllegalStateException(
    "HTTPS transport is required until an explicit debug plaintext mode is selected.",
)
