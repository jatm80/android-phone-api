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
    const val INTERNAL_ERROR = "internal_error"
    const val INVALID_REQUEST = "invalid_request"
    const val NOT_FOUND = "not_found"
    const val PAIRING_EXPIRED = "pairing_expired"
    const val PAIRING_NOT_FOUND = "pairing_not_found"
    const val PLAINTEXT_DISABLED = "plaintext_disabled"
}
