package com.assistant.android.automation

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

/**
 * Opens the camera app and (when accessibility is enabled) auto-presses the shutter.
 *
 * Strategy:
 *   1. Launch the system camera (or selfie camera) intent in a new task.
 *   2. After ~1.6 s wait — letting the camera UI fully appear — try to tap the visible
 *      "Shutter" / "Capture" / "Take" / "Photo" / "Take photo" text via the AccessibilityService.
 *   3. If that fails, the photo is still ready for one tap by the user.
 */
object CameraController {

    private const val TAG = "CameraController"
    private val handler = Handler(Looper.getMainLooper())

    fun openCameraApp(context: Context): Boolean {
        return launchCamera(context, useFront = false, video = false)
    }

    fun openVideoCamera(context: Context): Boolean {
        return launchCamera(context, useFront = false, video = true)
    }

    fun capturePhotoAuto(context: Context, useFrontCamera: Boolean = false): Boolean {
        val ok = launchCamera(context, useFront = useFrontCamera, video = false)
        if (ok) {
            // Try repeated shutter taps — different OEM cameras label the button differently.
            val labels = listOf("Shutter", "Capture", "Take photo", "Take Photo", "Photo", "Take")
            handler.postDelayed({
                val svc = AssistantAccessibilityService.instance
                if (svc == null) {
                    Toast.makeText(context, "Camera open. Enable Accessibility for auto-shutter.", Toast.LENGTH_LONG).show()
                    return@postDelayed
                }
                for (label in labels) {
                    if (svc.clickOnText(label)) {
                        Log.d(TAG, "Auto-shutter via '$label'")
                        return@postDelayed
                    }
                }
                // Try once more after another short delay (some cameras render late)
                handler.postDelayed({
                    for (label in labels) if (svc.clickOnText(label)) return@postDelayed
                }, 900)
            }, 1600)
        }
        return ok
    }

    private fun launchCamera(context: Context, useFront: Boolean, video: Boolean): Boolean {
        val action = if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (useFront) {
                // Hint to many OEMs to open front camera
                putExtra("android.intent.extras.CAMERA_FACING", 1)
                putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            }
        }
        return try {
            context.startActivity(intent); true
        } catch (e: Exception) {
            Log.e(TAG, "Camera launch failed: ${e.message}")
            // Fall back to launching default camera app
            try {
                context.startActivity(Intent("android.media.action.STILL_IMAGE_CAMERA")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: Exception) { false }
        }
    }
}
