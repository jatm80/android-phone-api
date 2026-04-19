package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.pairing.CreatePairingResponse
import com.jatm.androidphoneapi.pairing.CreatePairingRequest
import com.jatm.androidphoneapi.pairing.InMemoryPairingStore
import com.jatm.androidphoneapi.pairing.PairingRepository
import com.jatm.androidphoneapi.pairing.PairingStatusResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILocalHomelabClientKey"

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
    fun createPairingRequestReturnsPendingWithoutGrantingTrust() = testApplication {
        val repository = pairingRepository(ids = mutableListOf("pairing-1", "client-1"))
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                pairingRepository = repository,
            )
        }

        val response = client.post("$ApiVersionPrefix/pairing/requests") {
            header(RequestIdHeader, "pairing-req")
            contentType(ContentType.Application.Json)
            setBody("""{"clientName":"workstation","clientPublicKey":"$publicKey"}""")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        val body = json.decodeFromString<CreatePairingResponse>(response.bodyAsText())
        assertEquals("pairing-1", body.pairingId)
        assertEquals("pending", body.status)
        assertEquals("123456", body.verificationCode)
        assertEquals("pairing-req", body.requestId)
        assertTrue(repository.state.value.trustedClients.isEmpty())
    }

    @Test
    fun invalidPairingRequestReturnsConsistentError() = testApplication {
        val repository = pairingRepository()
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                pairingRepository = repository,
            )
        }

        val response = client.post("$ApiVersionPrefix/pairing/requests") {
            header(RequestIdHeader, "pairing-invalid")
            contentType(ContentType.Application.Json)
            setBody("""{"clientName":"","clientPublicKey":"short"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.INVALID_REQUEST, body.error.code)
        assertEquals("pairing-invalid", body.error.requestId)
    }

    @Test
    fun pairingStatusShowsApprovedClientOnlyAfterPhoneSideApproval() = testApplication {
        val repository = pairingRepository(ids = mutableListOf("pairing-1", "client-1"))
        repository.createPending(
            CreatePairingRequest(
                clientName = "workstation",
                clientPublicKey = publicKey,
            ),
        )
        repository.approvePairing("pairing-1")
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                pairingRepository = repository,
            )
        }

        val response = client.get("$ApiVersionPrefix/pairing/requests/pairing-1") {
            header(RequestIdHeader, "pairing-status")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<PairingStatusResponse>(response.bodyAsText())
        assertEquals("approved", body.status)
        assertEquals("client-1", body.clientId)
        assertEquals("pairing-status", body.requestId)
    }

    @Test
    fun unknownPairingStatusReturnsNotFound() = testApplication {
        val repository = pairingRepository()
        application {
            apiServerModule(
                timeProvider = TimeProvider { 123_456L },
                pairingRepository = repository,
            )
        }

        val response = client.get("$ApiVersionPrefix/pairing/requests/missing") {
            header(RequestIdHeader, "pairing-missing")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = json.decodeFromString<ApiErrorResponse>(response.bodyAsText())
        assertEquals(ApiErrorCodes.PAIRING_NOT_FOUND, body.error.code)
        assertEquals("pairing-missing", body.error.requestId)
    }

    private fun pairingRepository(
        ids: MutableList<String> = mutableListOf("pairing-1", "client-1"),
    ): PairingRepository =
        PairingRepository(
            store = InMemoryPairingStore(),
            timeProvider = TimeProvider { 123_456L },
            idGenerator = { ids.removeAt(0) },
            verificationCodeGenerator = { "123456" },
        )
}
