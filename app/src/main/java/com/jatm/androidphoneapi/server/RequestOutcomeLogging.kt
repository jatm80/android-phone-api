package com.jatm.androidphoneapi.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path

data class RequestOutcome(
    val requestId: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val failure: String? = null,
)

fun interface RequestOutcomeLogger {
    fun log(outcome: RequestOutcome)
}

object NoOpRequestOutcomeLogger : RequestOutcomeLogger {
    override fun log(outcome: RequestOutcome) = Unit
}

private class RequestOutcomeLoggingConfig {
    var logger: RequestOutcomeLogger = NoOpRequestOutcomeLogger
}

private val RequestOutcomeLoggingPlugin = createApplicationPlugin(
    name = "RequestOutcomeLogging",
    createConfiguration = ::RequestOutcomeLoggingConfig,
) {
    val logger = pluginConfig.logger
    onCallRespond { call, _ ->
        logger.log(call.toRequestOutcome())
    }
}

fun Application.installRequestOutcomeLogging(logger: RequestOutcomeLogger) {
    install(RequestOutcomeLoggingPlugin) {
        this.logger = logger
    }
}

private fun ApplicationCall.toRequestOutcome(): RequestOutcome =
    RequestOutcome(
        requestId = requestId(),
        method = request.httpMethod.value,
        path = request.path(),
        statusCode = response.status()?.value ?: 0,
    )
