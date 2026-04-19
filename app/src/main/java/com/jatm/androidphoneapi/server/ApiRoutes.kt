package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
import com.jatm.androidphoneapi.audit.ApiAuditLogger
import com.jatm.androidphoneapi.audit.NoOpApiAuditLogger
import com.jatm.androidphoneapi.capabilities.AudioRecordingProvider
import com.jatm.androidphoneapi.capabilities.BatteryInfoProvider
import com.jatm.androidphoneapi.capabilities.CameraProvider
import com.jatm.androidphoneapi.capabilities.CaptureRequest
import com.jatm.androidphoneapi.capabilities.ClipboardProvider
import com.jatm.androidphoneapi.capabilities.DeviceInfoProvider
import com.jatm.androidphoneapi.capabilities.LocationProvider
import com.jatm.androidphoneapi.capabilities.NotificationRequest
import com.jatm.androidphoneapi.capabilities.NotificationSender
import com.jatm.androidphoneapi.capabilities.TtsProvider
import com.jatm.androidphoneapi.capabilities.TtsRequest
import com.jatm.androidphoneapi.capabilities.RecordingRequest
import com.jatm.androidphoneapi.capabilities.SmsSender
import com.jatm.androidphoneapi.capabilities.SmsRequest
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
    clipboardProvider: ClipboardProvider? = null,
    locationProvider: LocationProvider? = null,
    ttsProvider: TtsProvider? = null,
    cameraProvider: CameraProvider? = null,
    auditLogger: ApiAuditLogger = NoOpApiAuditLogger,
    audioRecordingProvider: AudioRecordingProvider? = null,
    smsSender: SmsSender? = null,
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
                auditLogger.logAccess("AUTH_CHECK", call.requestId(), call.request.path())
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
                auditLogger.logAccess("BATTERY_READ", call.requestId(), call.request.path())
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
                auditLogger.logAccess("DEVICE_READ", call.requestId(), call.request.path())
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
                auditLogger.logAccess("NOTIFICATION_SENT", call.requestId(), call.request.path())
            }

            get("/clipboard") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@get
                val provider = clipboardProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Clipboard access is not available",
                    )
                    return@get
                }
                val content = provider.read()
                call.respond(
                    ClipboardReadResponse(
                        text = content.text,
                        hasContent = content.hasContent,
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("CLIPBOARD_READ", call.requestId(), call.request.path())
            }

            post("/clipboard") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@post
                val provider = clipboardProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Clipboard access is not available",
                    )
                    return@post
                }
                val request = call.receive<ClipboardWriteRequest>()
                if (request.text.length > 10_000) {
                    call.respondError(
                        status = HttpStatusCode.BadRequest,
                        code = ApiErrorCodes.INVALID_REQUEST,
                        message = "text must be at most 10000 characters",
                    )
                    return@post
                }
                provider.write(request.text)
                call.respond(
                    ClipboardWriteResponse(
                        written = true,
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("CLIPBOARD_WRITE", call.requestId(), call.request.path())
            }

            get("/location") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@get
                val provider = locationProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Location is not available",
                    )
                    return@get
                }
                val location = provider.lastKnownLocation()
                if (location == null) {
                    call.respondError(
                        status = HttpStatusCode.NotFound,
                        code = ApiErrorCodes.NOT_AVAILABLE,
                        message = "Location not available",
                    )
                    return@get
                }
                call.respond(
                    LocationResponse(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = location.altitude,
                        bearing = location.bearing,
                        speed = location.speed,
                        timestampEpochMillis = location.timestampEpochMillis,
                        provider = location.provider,
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("LOCATION_READ", call.requestId(), call.request.path())
            }

            post("/tts/speak") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@post
                val provider = ttsProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Text-to-speech is not available",
                    )
                    return@post
                }
                val request = call.receive<TtsSpeakRequest>()
                val errors = validateTtsSpeakRequest(request)
                if (errors.isNotEmpty()) {
                    call.respondError(
                        status = HttpStatusCode.BadRequest,
                        code = ApiErrorCodes.INVALID_REQUEST,
                        message = errors.joinToString("; "),
                    )
                    return@post
                }
                val result = provider.speak(
                    TtsRequest(
                        text = request.text,
                        locale = request.locale,
                        rate = request.rate,
                        pitch = request.pitch,
                    ),
                )
                call.respond(
                    TtsSpeakResponse(
                        spoken = result.spoken,
                        errorReason = result.errorReason,
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("TTS_SPEAK", call.requestId(), call.request.path())
            }

            get("/tts/engines") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@get
                val provider = ttsProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Text-to-speech is not available",
                    )
                    return@get
                }
                val engineList = provider.engines()
                call.respond(
                    TtsEnginesResponse(
                        engines = engineList.map { engine ->
                            TtsEngineResponse(
                                name = engine.name,
                                label = engine.label,
                                isDefault = engine.isDefault,
                            )
                        },
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("TTS_ENGINES", call.requestId(), call.request.path())
            }

            get("/camera/list") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@get
                val provider = cameraProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Camera is not available",
                    )
                    return@get
                }
                val cameras = provider.cameras()
                call.respond(
                    CameraListResponse(
                        cameras = cameras.map { cam ->
                            CameraDescriptionResponse(
                                id = cam.id,
                                facing = cam.facing,
                                megapixels = cam.megapixels,
                            )
                        },
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("CAMERA_LIST", call.requestId(), call.request.path())
            }

            post("/camera/capture") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@post
                val provider = cameraProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Camera is not available",
                    )
                    return@post
                }
                val request = call.receive<CameraCaptureRequest>()
                if (request.facing !in validFacings) {
                    call.respondError(
                        status = HttpStatusCode.BadRequest,
                        code = ApiErrorCodes.INVALID_REQUEST,
                        message = "facing must be one of: front, back",
                    )
                    return@post
                }
                val result = provider.capture(
                    CaptureRequest(
                        cameraId = request.cameraId,
                        facing = request.facing,
                    ),
                )
                call.respond(
                    CameraCaptureResponse(
                        success = result.success,
                        filePath = result.filePath,
                        mimeType = result.mimeType,
                        sizeBytes = result.sizeBytes,
                        errorReason = result.errorReason,
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("CAMERA_CAPTURE", call.requestId(), call.request.path())
            }

            post("/audio/record") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@post
                val provider = audioRecordingProvider
                if (provider == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "Audio recording is not available",
                    )
                    return@post
                }
                val request = call.receive<AudioRecordRequest>()
                val errors = validateAudioRecordRequest(request)
                if (errors.isNotEmpty()) {
                    call.respondError(
                        status = HttpStatusCode.BadRequest,
                        code = ApiErrorCodes.INVALID_REQUEST,
                        message = errors.joinToString("; "),
                    )
                    return@post
                }
                val result = provider.startRecording(
                    RecordingRequest(
                        durationSeconds = request.durationSeconds,
                        format = request.format,
                        quality = request.quality,
                    ),
                )
                call.respond(
                    AudioRecordResponse(
                        success = result.success,
                        filePath = result.filePath,
                        durationMillis = result.durationMillis,
                        sizeBytes = result.sizeBytes,
                        errorReason = result.errorReason,
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess("AUDIO_RECORD", call.requestId(), call.request.path())
            }

            post("/sms/send") {
                if (!call.requireApiKey(apiKeyAuthenticator)) return@post
                val sender = smsSender
                if (sender == null) {
                    call.respondError(
                        status = HttpStatusCode.NotImplemented,
                        code = ApiErrorCodes.NOT_IMPLEMENTED,
                        message = "SMS sending is not available",
                    )
                    return@post
                }
                val request = call.receive<SmsSendRequest>()
                val errors = validateSmsSendRequest(request)
                if (errors.isNotEmpty()) {
                    call.respondError(
                        status = HttpStatusCode.BadRequest,
                        code = ApiErrorCodes.INVALID_REQUEST,
                        message = errors.joinToString("; "),
                    )
                    return@post
                }
                val result = sender.send(
                    SmsRequest(
                        recipients = request.recipients,
                        message = request.message,
                        simSlot = request.simSlot,
                    ),
                )
                call.respond(
                    SmsSendResponse(
                        sent = result.sent,
                        recipientCount = result.recipientCount,
                        errorReason = result.errorReason,
                        requestId = call.requestId(),
                    ),
                )
                auditLogger.logAccess(
                    "SMS_SEND",
                    call.requestId(),
                    call.request.path(),
                    reason = "${request.recipients.size} recipient(s)",
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

private fun validateTtsSpeakRequest(request: TtsSpeakRequest): List<String> {
    val errors = mutableListOf<String>()
    if (request.text.isEmpty() || request.text.length > 5000) {
        errors.add("text must be between 1 and 5000 characters")
    }
    if (request.rate < 0.1f || request.rate > 4.0f) {
        errors.add("rate must be between 0.1 and 4.0")
    }
    if (request.pitch < 0.1f || request.pitch > 4.0f) {
        errors.add("pitch must be between 0.1 and 4.0")
    }
    return errors
}

private val validFacings = setOf("front", "back")

private val validAudioFormats = setOf("mp4", "3gp", "wav")
private val validAudioQualities = setOf("low", "medium", "high")

private fun validateAudioRecordRequest(request: AudioRecordRequest): List<String> {
    val errors = mutableListOf<String>()
    if (request.durationSeconds < 1 || request.durationSeconds > 300) {
        errors.add("durationSeconds must be between 1 and 300")
    }
    if (request.format !in validAudioFormats) {
        errors.add("format must be one of: mp4, 3gp, wav")
    }
    if (request.quality !in validAudioQualities) {
        errors.add("quality must be one of: low, medium, high")
    }
    return errors
}

private val phoneNumberPattern = Regex("^\\+?[0-9]{3,15}$")

private fun validateSmsSendRequest(request: SmsSendRequest): List<String> {
    val errors = mutableListOf<String>()
    if (request.recipients.isEmpty()) {
        errors.add("recipients must not be empty")
    }
    if (request.recipients.size > 10) {
        errors.add("recipients must have at most 10 entries")
    }
    request.recipients.forEachIndexed { index, recipient ->
        if (!phoneNumberPattern.matches(recipient)) {
            errors.add("recipients[$index] is not a valid phone number")
        }
    }
    if (request.message.isEmpty() || request.message.length > 1600) {
        errors.add("message must be between 1 and 1600 characters")
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
