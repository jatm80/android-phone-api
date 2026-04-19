package com.jatm.androidphoneapi.server

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    val error: ApiError,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String,
)

object ApiErrorCodes {
    const val API_DISABLED = "api_disabled"
    const val INTERNAL_ERROR = "internal_error"
    const val INVALID_REQUEST = "invalid_request"
    const val NOT_FOUND = "not_found"
    const val PLAINTEXT_DISABLED = "plaintext_disabled"
    const val NOT_AVAILABLE = "not_available"
    const val NOT_IMPLEMENTED = "not_implemented"
    const val UNAUTHORIZED = "unauthorized"
}
