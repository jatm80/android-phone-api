package com.jatm.androidphoneapi.server

import com.jatm.androidphoneapi.apikey.ApiKeyAuthResult
import com.jatm.androidphoneapi.apikey.ApiKeyAuthenticator
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.header

class DisabledApiKeyAuthenticator : ApiKeyAuthenticator {
    override fun authenticate(
        candidate: String?,
        requestId: String?,
        path: String?,
    ): ApiKeyAuthResult = ApiKeyAuthResult.Disabled
}

suspend fun ApplicationCall.requireApiKey(authenticator: ApiKeyAuthenticator): Boolean {
    val token = bearerToken()
    val result = authenticator.authenticate(
        candidate = token,
        requestId = requestId(),
        path = request.path(),
    )
    return when (result) {
        ApiKeyAuthResult.Authenticated -> true
        ApiKeyAuthResult.Disabled -> {
            respondError(
                status = HttpStatusCode.Forbidden,
                code = ApiErrorCodes.API_DISABLED,
                message = "API access is disabled",
            )
            false
        }
        ApiKeyAuthResult.MissingOrInvalid -> {
            response.header(HttpHeaders.WWWAuthenticate, "Bearer")
            respondError(
                status = HttpStatusCode.Unauthorized,
                code = ApiErrorCodes.UNAUTHORIZED,
                message = "Authentication required",
            )
            false
        }
    }
}

private fun ApplicationCall.bearerToken(): String? {
    val authorization = request.headers[HttpHeaders.Authorization]?.trim() ?: return null
    val parts = authorization.split(Regex("\\s+"))
    if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return null
    return parts[1]
}
