package com.jatm.androidphoneapi.capabilities

interface CameraProvider {
    fun cameras(): List<CameraDescription>
    fun capture(request: CaptureRequest): CaptureResult
}

data class CameraDescription(
    val id: String,
    val facing: String,
    val megapixels: Float?,
)

data class CaptureRequest(
    val cameraId: String? = null,
    val facing: String = "back",
)

data class CaptureResult(
    val success: Boolean,
    val filePath: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val errorReason: String? = null,
)
