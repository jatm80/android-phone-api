package com.jatm.androidphoneapi.capabilities

interface AudioRecordingProvider {
    fun startRecording(request: RecordingRequest): RecordingResult
}

data class RecordingRequest(
    val durationSeconds: Int = 10,
    val format: String = "mp4",
    val quality: String = "medium",
)

data class RecordingResult(
    val success: Boolean,
    val filePath: String? = null,
    val durationMillis: Long? = null,
    val sizeBytes: Long? = null,
    val errorReason: String? = null,
)
