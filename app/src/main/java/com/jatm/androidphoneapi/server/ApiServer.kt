package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
import com.jatm.androidphoneapi.audit.ApiAuditLogger
import com.jatm.androidphoneapi.audit.NoOpApiAuditLogger
import com.jatm.androidphoneapi.capabilities.AudioRecordingProvider
import com.jatm.androidphoneapi.capabilities.BatteryInfoProvider
import com.jatm.androidphoneapi.capabilities.CameraProvider
import com.jatm.androidphoneapi.capabilities.ClipboardProvider
import com.jatm.androidphoneapi.capabilities.DeviceInfoProvider
import com.jatm.androidphoneapi.capabilities.LocationProvider
import com.jatm.androidphoneapi.capabilities.NotificationSender
import com.jatm.androidphoneapi.capabilities.TtsProvider
import com.jatm.androidphoneapi.capabilities.SmsSender
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
    private val apiKeyAuthenticator: ApiKeyAuthenticator = DisabledApiKeyAuthenticator(),
    private val batteryInfoProvider: BatteryInfoProvider? = null,
    private val deviceInfoProvider: DeviceInfoProvider? = null,
    private val notificationSender: NotificationSender? = null,
    private val clipboardProvider: ClipboardProvider? = null,
    private val locationProvider: LocationProvider? = null,
    private val ttsProvider: TtsProvider? = null,
    private val cameraProvider: CameraProvider? = null,
    private val auditLogger: ApiAuditLogger = NoOpApiAuditLogger,
    private val audioRecordingProvider: AudioRecordingProvider? = null,
    private val smsSender: SmsSender? = null,
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
                apiKeyAuthenticator = apiKeyAuthenticator,
                batteryInfoProvider = batteryInfoProvider,
                deviceInfoProvider = deviceInfoProvider,
                notificationSender = notificationSender,
                clipboardProvider = clipboardProvider,
                locationProvider = locationProvider,
                ttsProvider = ttsProvider,
                cameraProvider = cameraProvider,
                auditLogger = auditLogger,
                audioRecordingProvider = audioRecordingProvider,
                smsSender = smsSender,
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
