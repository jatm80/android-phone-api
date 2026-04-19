package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthResult
import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
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

    private class FakeApiKeyAuthenticator(
        private val result: ApiKeyAuthResult,
    ) : ApiKeyAuthenticator {
        override fun authenticate(
            candidate: String?,
            requestId: String?,
            path: String?,
        ): ApiKeyAuthResult = result
    }
}
