package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
import com.jatm.androidphoneapi.capabilities.BatteryInfoProvider
import com.jatm.androidphoneapi.capabilities.DeviceInfoProvider
import com.jatm.androidphoneapi.capabilities.NotificationRequest
import com.jatm.androidphoneapi.capabilities.NotificationSender
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
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
    batteryInfoProvider: BatteryInfoProvider? = null,
    deviceInfoProvider: DeviceInfoProvider? = null,
    notificationSender: NotificationSender? = null,
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

            get("/battery") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@get
                val provider = batteryInfoProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Battery info is not available",
                    )
                    return@get
                }
                val info = provider.batteryInfo()
                call.respond(
                    BatteryInfoResponse(
                        level = info.level,
                        scale = info.scale,
                        percentage = info.percentage,
                        status = info.status,
                        health = info.health,
                        plugged = info.plugged,
                        technology = info.technology,
                        temperatureCelsius = info.temperature,
                        voltageVolts = info.voltage,
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/device") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@get
                val provider = deviceInfoProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Device info is not available",
                    )
                    return@get
                }
                val info = provider.deviceInfo()
                call.respond(
                    DeviceInfoResponse(
                        manufacturer = info.manufacturer,
                        model = info.model,
                        brand = info.brand,
                        device = info.device,
                        product = info.product,
                        androidVersion = info.androidVersion,
                        sdkVersion = info.sdkVersion,
                        securityPatch = info.securityPatch,
                        uptimeMillis = info.uptimeMillis,
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/notify") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@post
                val sender = notificationSender
                if (sender == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Notification sending is not available",
                    )
                    return@post
                }
                val request = call.receive<SendNotificationRequest>()
                val errors = validateNotificationRequest(request)
                if (errors.isNotEmpty()) {
                    call.respondError(
                        status = HttpStatusCode.BadRequest,
                        code = ApiErrorCodes.INVALID_REQUEST,
                        message = errors.joinToString("; "),
                    )
                    return@post
                }
                val result = sender.send(
                    NotificationRequest(
                        title = request.title,
                        body = request.body,
                        channel = request.channel,
                        priority = request.priority,
                    ),
                )
                call.respond(
                    SendNotificationResponse(
                        notificationId = result.notificationId,
                        delivered = result.delivered,
                        requestId = call.requestId(),
                    ),
                )
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

private val channelPattern = Regex("[A-Za-z0-9_-]{1,64}")
private val validPriorities = setOf("low", "default", "high")

private fun validateNotificationRequest(request: SendNotificationRequest): List<String> {
    val errors = mutableListOf<String>()
    if (request.title.isBlank()) errors.add("title must not be blank")
    if (request.title.length > 200) errors.add("title must be at most 200 characters")
    if (request.body.length > 2000) errors.add("body must be at most 2000 characters")
    if (!channelPattern.matches(request.channel)) {
        errors.add("channel must be 1-64 alphanumeric, hyphen, or underscore characters")
    }
    if (request.priority !in validPriorities) {
        errors.add("priority must be one of: low, default, high")
    }
    return errors
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
