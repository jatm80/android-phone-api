package com.jatm.androidphoneapi.capabilities

class AndroidAudioRecordingProvider : AudioRecordingProvider {
    override fun startRecording(request: RecordingRequest): RecordingResult =
        RecordingResult(
            success = false,
            errorReason = "recording_requires_foreground_service_and_permission",
        )
}
