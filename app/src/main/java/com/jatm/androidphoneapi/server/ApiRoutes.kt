package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.util.UUID

const val ApiVersionPrefix = "/api/v1"
const val RequestIdHeader = "X-Request-ID"

private val requestIdPattern = Regex("[A-Za-z0-9._-]{1,64}")

fun Application.apiServerModule(
    timeProvider: TimeProvider = SystemTimeProvider,
    logger: RequestOutcomeLogger = NoOpRequestOutcomeLogger,
    apiKeyAuthenticator: ApiKeyAuthenticator = DisabledApiKeyAuthenticator(),
) {
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }

    install(CallId) {
        retrieveFromHeader(RequestIdHeader)
        verify { requestIdPattern.matches(it) }
        generate { UUID.randomUUID().toString() }
        replyToHeader(RequestIdHeader)
    }

    installRequestOutcomeLogging(logger)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.log(
                RequestOutcome(
                    requestId = call.requestId(),
                    method = call.request.httpMethod.value,
                    path = call.request.path(),
                    statusCode = HttpStatusCode.InternalServerError.value,
                    failure = cause::class.simpleName,
                ),
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse(
                    ApiError(
                        code = ApiErrorCodes.INTERNAL_ERROR,
                        message = "Internal server error",
                        requestId = call.requestId(),
                    ),
                ),
            )
        }
    }

    routing {
        route(ApiVersionPrefix) {
            get("/health") {
                call.respond(
                    HealthResponse(
                        serverTimeEpochMillis = timeProvider.nowEpochMillis(),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/auth/check") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@get

                call.respond(mapOf("status" to "authenticated"))
            }
        }

        get("{...}") {
            call.respondError(
                status = HttpStatusCode.NotFound,
                code = ApiErrorCodes.NOT_FOUND,
                message = "Route not found",
            )
        }
    }
}

fun io.ktor.server.application.ApplicationCall.requestId(): String =
    callId ?: "unknown"

suspend fun io.ktor.server.application.ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respond(
        status,
        ApiErrorResponse(
            ApiError(
                code = code,
                message = message,
                requestId = requestId(),
            ),
        ),
    )
}
