package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthResult
import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
import com.jatm.androidphoneapi.audit.ApiAuditLogger
import com.jatm.androidphoneapi.capabilities.BatteryInfo
import com.jatm.androidphoneapi.capabilities.BatteryInfoProvider
import com.jatm.androidphoneapi.capabilities.ClipboardContent
import com.jatm.androidphoneapi.capabilities.ClipboardProvider
import com.jatm.androidphoneapi.capabilities.DeviceInfo
import com.jatm.androidphoneapi.capabilities.DeviceInfoProvider
import com.jatm.androidphoneapi.capabilities.LocationInfo
import com.jatm.androidphoneapi.capabilities.LocationProvider
import com.jatm.androidphoneapi.capabilities.NotificationRequest
import com.jatm.androidphoneapi.capabilities.NotificationResult
import com.jatm.androidphoneapi.capabilities.NotificationSender
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun healthReturnsStableSchemaAndEchoesValidRequestId() = testApplication {
        application {
            apiServerModule(timeProvider = TimeProvider { 123_456L })
        }

        val response = client.get("$ApiVersionPrefix/health") {
            header(RequestIdHeader, "req-123")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("req-123", response.headers[RequestIdHeader])

        val body = json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", body.status)
        assertEquals("android-phone-api", body.service)
        assertEquals("v1", body.apiVersion)
        assertEquals(123_456L, body.serverTimeEpochMillis)
        assertEquals("req-123", body.requestId)
    }

    @Test
    fun invalidRequestIdIsReplaced() = testApplication {
        application {
            apiServerModule(timeProvider = TimeProvider { 123_456L })
        }

        val response = client.get("$ApiVersionPrefix/health") {
            header(RequestIdHeader, "bad request id")
        }

        val requestId = response.headers[RequestIdHeader].orEmpty()
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotEquals("bad request id", requestId)
        assertTrue(requestId.matches(Regex("[A-Za-z0-9._-]{1,64}")))
    }

    @Test
    fun unknownRouteReturnsConsistentErrorShape() = testApplication {
        application {
            apiServerModule(timeProvider = TimeProvider { 123_456L })
        }

        val response = client.get("$ApiVersionPrefix/missing") {
            header(RequestIdHeader, "req-missing")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("req-missing", response.headers[RequestIdHeader])

        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.NOT_FOUND, body.error.code)
        assertEquals("Route not found", body.error.message)
        assertEquals("req-missing", body.error.requestId)
    }

    @Test
    fun defaultServerConfigRequiresTls() {
        val server = EmbeddedKtorApiServer(
            config = ApiServerConfig(),
            timeProvider = TimeProvider { 123_456L },
        )

        assertThrows(TlsConfigurationRequiredException::class.java) {
            server.start()
        }
    }

    @Test
    fun debugBuildConfigUsesLoopbackPlaintextOnly() {
        val config = ApiServerConfig.forBuild(isDebug = true)

        assertEquals("127.0.0.1", config.bindHost)
        assertEquals(8080, config.port)
        assertEquals(TransportMode.PLAINTEXT_DEBUG_ONLY, config.transportMode)
    }

    @Test
    fun protectedRouteRejectsMissingAuthorizationHeader() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.MissingOrInvalid),
            )
        }

        val response = client.get("$ApiVersionPrefix/auth/check") {
            header(RequestIdHeader, "auth-missing")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Bearer", response.headers["WWW-Authenticate"])
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.UNAUTHORIZED, body.error.code)
        assertEquals("auth-missing", body.error.requestId)
    }

    @Test
    fun protectedRouteRejectsDisabledApi() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Disabled),
            )
        }

        val response = client.get("$ApiVersionPrefix/auth/check") {
            header(RequestIdHeader, "auth-disabled")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.API_DISABLED, body.error.code)
        assertEquals("auth-disabled", body.error.requestId)
    }

    @Test
    fun protectedRouteAllowsValidApiKey() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
            )
        }

        val response = client.get("$ApiVersionPrefix/auth/check") {
            header(RequestIdHeader, "auth-ok")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("authenticated"))
    }

    @Test
    fun batteryEndpointRequiresAuthentication() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.MissingOrInvalid),
                batteryInfoProvider = FakeBatteryInfoProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/battery") {
            header(RequestIdHeader, "bat-unauth")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.UNAUTHORIZED, body.error.code)
        assertEquals("bat-unauth", body.error.requestId)
    }

    @Test
    fun batteryEndpointReturnsInfoForAuthenticatedRequest() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                batteryInfoProvider = FakeBatteryInfoProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/battery") {
            header(RequestIdHeader, "bat-ok")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<BatteryInfoResponse>(response.bodyAsText())
        assertEquals(75, body.level)
        assertEquals(100, body.scale)
        assertEquals(75, body.percentage)
        assertEquals("discharging", body.status)
        assertEquals("good", body.health)
        assertEquals("none", body.plugged)
        assertEquals("Li-ion", body.technology)
        assertEquals(25.0f, body.temperatureCelsius)
        assertEquals(3.8f, body.voltageVolts)
        assertEquals("bat-ok", body.requestId)
    }

    @Test
    fun batteryEndpointReturnsNotImplementedWhenProviderNull() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                batteryInfoProvider = null,
            )
        }

        val response = client.get("$ApiVersionPrefix/battery") {
            header(RequestIdHeader, "bat-null")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.NotImplemented, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.NOT_IMPLEMENTED, body.error.code)
        assertEquals("bat-null", body.error.requestId)
    }

    @Test
    fun deviceEndpointRequiresAuthentication() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.MissingOrInvalid),
                deviceInfoProvider = FakeDeviceInfoProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/device") {
            header(RequestIdHeader, "dev-unauth")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.UNAUTHORIZED, body.error.code)
        assertEquals("dev-unauth", body.error.requestId)
    }

    @Test
    fun deviceEndpointReturnsInfoForAuthenticatedRequest() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                deviceInfoProvider = FakeDeviceInfoProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/device") {
            header(RequestIdHeader, "dev-ok")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<DeviceInfoResponse>(response.bodyAsText())
        assertEquals("TestMfg", body.manufacturer)
        assertEquals("TestModel", body.model)
        assertEquals("TestBrand", body.brand)
        assertEquals("testdevice", body.device)
        assertEquals("testproduct", body.product)
        assertEquals("14", body.androidVersion)
        assertEquals(34, body.sdkVersion)
        assertEquals("2024-01-01", body.securityPatch)
        assertEquals(123_456L, body.uptimeMillis)
        assertEquals("dev-ok", body.requestId)
    }

    @Test
    fun deviceEndpointReturnsNotImplementedWhenProviderNull() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                deviceInfoProvider = null,
            )
        }

        val response = client.get("$ApiVersionPrefix/device") {
            header(RequestIdHeader, "dev-null")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.NotImplemented, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.NOT_IMPLEMENTED, body.error.code)
        assertEquals("dev-null", body.error.requestId)
    }

    @Test
    fun notifyEndpointRequiresAuthentication() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.MissingOrInvalid),
                notificationSender = FakeNotificationSender(),
            )
        }

        val response = client.post("$ApiVersionPrefix/notify") {
            header(RequestIdHeader, "notify-unauth")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Test","body":"Hello"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.UNAUTHORIZED, body.error.code)
        assertEquals("notify-unauth", body.error.requestId)
    }

    @Test
    fun notifyEndpointReturnsNotImplementedWhenSenderNull() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                notificationSender = null,
            )
        }

        val response = client.post("$ApiVersionPrefix/notify") {
            header(RequestIdHeader, "notify-null")
            header("Authorization", "Bearer apa_live_test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Test","body":"Hello"}""")
        }

        assertEquals(HttpStatusCode.NotImplemented, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.NOT_IMPLEMENTED, body.error.code)
        assertEquals("notify-null", body.error.requestId)
    }

    @Test
    fun notifyEndpointSendsNotificationForValidPayload() = testApplication {
        val fakeSender = FakeNotificationSender()
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                notificationSender = fakeSender,
            )
        }

        val response = client.post("$ApiVersionPrefix/notify") {
            header(RequestIdHeader, "notify-ok")
            header("Authorization", "Bearer apa_live_test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Test Title","body":"Hello World","channel":"homelab","priority":"high"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<SendNotificationResponse>(response.bodyAsText())
        assertEquals(42, body.notificationId)
        assertTrue(body.delivered)
        assertEquals("notify-ok", body.requestId)

        val sent = fakeSender.lastRequest
        assertEquals("Test Title", sent?.title)
        assertEquals("Hello World", sent?.body)
        assertEquals("homelab", sent?.channel)
        assertEquals("high", sent?.priority)
    }

    @Test
    fun notifyEndpointRejectsEmptyTitle() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                notificationSender = FakeNotificationSender(),
            )
        }

        val response = client.post("$ApiVersionPrefix/notify") {
            header(RequestIdHeader, "notify-empty-title")
            header("Authorization", "Bearer apa_live_test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"  ","body":"Hello"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.INVALID_REQUEST, body.error.code)
        assertTrue(body.error.message.contains("title"))
    }

    @Test
    fun authenticatedBatteryRequestAuditsAccess() = testApplication {
        val auditLogger = CapturingApiAuditLogger()
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                batteryInfoProvider = FakeBatteryInfoProvider(),
                auditLogger = auditLogger,
            )
        }

        val response = client.get("$ApiVersionPrefix/battery") {
            header(RequestIdHeader, "bat-audit")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, auditLogger.events.size)
        val captured = auditLogger.events.single()
        assertEquals("BATTERY_READ", captured.type)
        assertEquals("bat-audit", captured.requestId)
        assertEquals("/api/v1/battery", captured.path)
    }

    @Test
    fun notifyEndpointRejectsTooLongBody() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                notificationSender = FakeNotificationSender(),
            )
        }

        val longBody = "x".repeat(2001)
        val response = client.post("$ApiVersionPrefix/notify") {
            header(RequestIdHeader, "notify-long-body")
            header("Authorization", "Bearer apa_live_test")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Valid","body":"$longBody"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.INVALID_REQUEST, body.error.code)
        assertTrue(body.error.message.contains("body"))
    }

    private class CapturingApiAuditLogger : ApiAuditLogger {
        val events = mutableListOf<CapturedAuditEvent>()
        override fun logAccess(type: String, requestId: String?, path: String?, reason: String?) {
            events.add(CapturedAuditEvent(type, requestId, path, reason))
        }
    }

    private data class CapturedAuditEvent(
        val type: String,
        val requestId: String?,
        val path: String?,
        val reason: String?,
    )

    private class FakeApiKeyAuthenticator(
        private val result: ApiKeyAuthResult,
    ) : ApiKeyAuthenticator {
        override fun authenticate(
            candidate: String?,
            requestId: String?,
            path: String?,
        ): ApiKeyAuthResult = result
    }

    private class FakeBatteryInfoProvider(
        private val info: BatteryInfo = BatteryInfo(
            level = 75,
            scale = 100,
            percentage = 75,
            status = "discharging",
            health = "good",
            plugged = "none",
            technology = "Li-ion",
            temperature = 25.0f,
            voltage = 3.8f,
        ),
    ) : BatteryInfoProvider {
        override fun batteryInfo(): BatteryInfo = info
    }

    private class FakeDeviceInfoProvider(
        private val info: DeviceInfo = DeviceInfo(
            manufacturer = "TestMfg",
            model = "TestModel",
            brand = "TestBrand",
            device = "testdevice",
            product = "testproduct",
            androidVersion = "14",
            sdkVersion = 34,
            securityPatch = "2024-01-01",
            uptimeMillis = 123_456L,
        ),
    ) : DeviceInfoProvider {
        override fun deviceInfo(): DeviceInfo = info
    }

    private class FakeNotificationSender(
        private val result: NotificationResult = NotificationResult(
            notificationId = 42,
            delivered = true,
        ),
    ) : NotificationSender {
        var lastRequest: NotificationRequest? = null
            private set

        override fun send(request: NotificationRequest): NotificationResult {
            lastRequest = request
            return result
        }
    }

    private class FakeClipboardProvider(
        private val content: ClipboardContent = ClipboardContent(text = "hello", hasContent = true),
    ) : ClipboardProvider {
        var lastWritten: String? = null
            private set

        override fun read(): ClipboardContent = content
        override fun write(text: String) {
            lastWritten = text
        }
    }

    private class FakeLocationProvider(
        private val location: LocationInfo? = LocationInfo(
            latitude = 37.7749,
            longitude = -122.4194,
            accuracy = 10.0f,
            altitude = 16.0,
            bearing = 90.0f,
            speed = 1.5f,
            timestampEpochMillis = 1_700_000_000_000L,
            provider = "gps",
        ),
    ) : LocationProvider {
        override fun lastKnownLocation(): LocationInfo? = location
    }

    // --- Clipboard tests ---

    @Test
    fun clipboardReadRequiresAuth() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.MissingOrInvalid),
                clipboardProvider = FakeClipboardProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/clipboard") {
            header(RequestIdHeader, "clip-unauth")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.UNAUTHORIZED, body.error.code)
        assertEquals("clip-unauth", body.error.requestId)
    }

    @Test
    fun clipboardReadReturnsContent() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                clipboardProvider = FakeClipboardProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/clipboard") {
            header(RequestIdHeader, "clip-read")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ClipboardReadResponse>(response.bodyAsText())
        assertEquals("hello", body.text)
        assertTrue(body.hasContent)
        assertEquals("clip-read", body.requestId)
    }

    @Test
    fun clipboardWriteRequiresAuth() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.MissingOrInvalid),
                clipboardProvider = FakeClipboardProvider(),
            )
        }

        val response = client.post("$ApiVersionPrefix/clipboard") {
            header(RequestIdHeader, "clip-write-unauth")
            contentType(ContentType.Application.Json)
            setBody("""{"text":"hello"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.UNAUTHORIZED, body.error.code)
        assertEquals("clip-write-unauth", body.error.requestId)
    }

    @Test
    fun clipboardWriteRejectsLongText() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                clipboardProvider = FakeClipboardProvider(),
            )
        }

        val longText = "x".repeat(10_001)
        val response = client.post("$ApiVersionPrefix/clipboard") {
            header(RequestIdHeader, "clip-long")
            header("Authorization", "Bearer apa_live_test")
            contentType(ContentType.Application.Json)
            setBody("""{"text":"$longText"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.INVALID_REQUEST, body.error.code)
        assertTrue(body.error.message.contains("text"))
    }

    @Test
    fun clipboardWriteSucceeds() = testApplication {
        val fakeClipboard = FakeClipboardProvider()
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                clipboardProvider = fakeClipboard,
            )
        }

        val response = client.post("$ApiVersionPrefix/clipboard") {
            header(RequestIdHeader, "clip-write-ok")
            header("Authorization", "Bearer apa_live_test")
            contentType(ContentType.Application.Json)
            setBody("""{"text":"copied text"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<ClipboardWriteResponse>(response.bodyAsText())
        assertTrue(body.written)
        assertEquals("clip-write-ok", body.requestId)
        assertEquals("copied text", fakeClipboard.lastWritten)
    }

    // --- Location tests ---

    @Test
    fun locationRequiresAuth() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.MissingOrInvalid),
                locationProvider = FakeLocationProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/location") {
            header(RequestIdHeader, "loc-unauth")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.UNAUTHORIZED, body.error.code)
        assertEquals("loc-unauth", body.error.requestId)
    }

    @Test
    fun locationReturnsData() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                locationProvider = FakeLocationProvider(),
            )
        }

        val response = client.get("$ApiVersionPrefix/location") {
            header(RequestIdHeader, "loc-ok")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<LocationResponse>(response.bodyAsText())
        assertEquals(37.7749, body.latitude, 0.0001)
        assertEquals(-122.4194, body.longitude, 0.0001)
        assertEquals(10.0f, body.accuracy)
        assertEquals(16.0, body.altitude)
        assertEquals(90.0f, body.bearing)
        assertEquals(1.5f, body.speed)
        assertEquals(1_700_000_000_000L, body.timestampEpochMillis)
        assertEquals("gps", body.provider)
        assertEquals("loc-ok", body.requestId)
    }

    @Test
    fun locationReturnsNotAvailableWhenNull() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                locationProvider = FakeLocationProvider(location = null),
            )
        }

        val response = client.get("$ApiVersionPrefix/location") {
            header(RequestIdHeader, "loc-null")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.NOT_AVAILABLE, body.error.code)
        assertEquals("loc-null", body.error.requestId)
    }

    @Test
    fun locationReturnsNotImplementedWhenProviderNull() = testApplication {
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                apiKeyAuthenticator = FakeApiKeyAuthenticator(ApiKeyAuthResult.Authenticated),
                locationProvider = null,
            )
        }

        val response = client.get("$ApiVersionPrefix/location") {
            header(RequestIdHeader, "loc-not-impl")
            header("Authorization", "Bearer apa_live_test")
        }

        assertEquals(HttpStatusCode.NotImplemented, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.NOT_IMPLEMENTED, body.error.code)
        assertEquals("loc-not-impl", body.error.requestId)
    }
}
