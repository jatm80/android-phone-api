package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthResult
import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
import com.jatm.androidphoneapi.capabilities.BatteryInfo
import com.jatm.androidphoneapi.capabilities.BatteryInfoProvider
import com.jatm.androidphoneapi.capabilities.DeviceInfo
import com.jatm.androidphoneapi.capabilities.DeviceInfoProvider
import io.ktor.client.request.get
import io.ktor.client.request.header
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
}
