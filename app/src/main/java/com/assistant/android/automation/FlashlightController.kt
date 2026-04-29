package com.assistant.android.automation

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import com.assistant.android.core.MasterController

/**
 * Controls the device torch (back-camera flash) via Camera2's setTorchMode API.
 *
 * Tracks state in a local volatile so toggle() can flip without querying the system.
 */
object FlashlightController {

    private const val TAG = "Flashlight"
    @Volatile private var isOn: Boolean = false

    fun on(context: Context): Boolean = setMode(context, true)
    fun off(context: Context): Boolean = setMode(context, false)
    fun toggle(context: Context): Boolean = setMode(context, !isOn)

    private fun setMode(context: Context, enable: Boolean): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: run {
            MasterController.recordError("Flashlight unavailable", "CameraManager is null on this device.")
            return false
        }
        return try {
            val backCameraId = cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull()
            if (backCameraId == null) {
                MasterController.recordError("No flash on this device", "No camera with FLASH_INFO_AVAILABLE was found.")
                return false
            }
            cm.setTorchMode(backCameraId, enable)
            isOn = enable
            true
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight toggle failed: ${e.message}", e)
            MasterController.recordError("Flashlight failed", "${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
