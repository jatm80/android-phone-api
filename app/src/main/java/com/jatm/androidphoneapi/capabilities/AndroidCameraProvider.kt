package com.jatm.androidphoneapi.capabilities

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.SizeF

class AndroidCameraProvider(
    private val context: Context,
) : CameraProvider {
    override fun cameras(): List<CameraDescription> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.map { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            val facing = when (lensFacing) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                else -> "unknown"
            }
            val megapixels = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                ?.let { size: SizeF ->
                    val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val largest = configs?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                        ?.maxByOrNull { it.width * it.height }
                    largest?.let { (it.width * it.height) / 1_000_000f }
                }
            CameraDescription(id = id, facing = facing, megapixels = megapixels)
        }
    }

    override fun capture(request: CaptureRequest): CaptureResult {
        return CaptureResult(
            success = false,
            errorReason = "capture_requires_foreground_ui",
        )
    }
}
