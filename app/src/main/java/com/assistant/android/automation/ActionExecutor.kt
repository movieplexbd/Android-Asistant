package com.assistant.android.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast

class ActionExecutor(private val context: Context) {

    fun executeAction(intent: String, target: String?, message: String?, time: String?): Boolean {
        return when (intent) {
            "call" -> makePhoneCall(target)
            "open_app" -> openApp(target)
            "message" -> sendMessage(target, message)
            "reminder" -> setReminder(message, time)
            "automation" -> handleAutomation(target)
            "info" -> handleInfoRequest(message)
            else -> {
                Log.w("ActionExecutor", "Unknown intent: $intent")
                false
            }
        }
    }

    private fun makePhoneCall(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) {
            Toast.makeText(context, "Phone number not provided.", Toast.LENGTH_SHORT).show()
            return false
        }
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (callIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(callIntent)
            return true
        } else {
            Toast.makeText(context, "Cannot make a call. No app can handle this action.", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun openApp(appName: String?): Boolean {
        if (appName.isNullOrEmpty()) {
            Toast.makeText(context, "App name not provided.", Toast.LENGTH_SHORT).show()
            return false
        }
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(appName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } else {
            Toast.makeText(context, "App '$appName' not found.", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun sendMessage(phoneNumber: String?, message: String?): Boolean {
        if (phoneNumber.isNullOrEmpty() || message.isNullOrEmpty()) {
            Toast.makeText(context, "Phone number or message not provided.", Toast.LENGTH_SHORT).show()
            return false
        }
        try {
            val smsManager: SmsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(context, "Message sent to $phoneNumber.", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error sending SMS: ${e.message}")
            Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun setReminder(message: String?, time: String?): Boolean {
        // This is a placeholder. Real implementation would involve Calendar/AlarmManager.
        if (message.isNullOrEmpty() || time.isNullOrEmpty()) {
            Toast.makeText(context, "Reminder message or time not provided.", Toast.LENGTH_SHORT).show()
            return false
        }
        Toast.makeText(context, "Reminder set for '$message' at '$time'. (Placeholder)", Toast.LENGTH_LONG).show()
        return true
    }

    private fun handleAutomation(action: String?): Boolean {
        return when (action) {
            "toggle_flashlight" -> toggleFlashlight()
            "control_volume" -> controlVolume()
            "open_settings" -> openSettings()
            else -> {
                Toast.makeText(context, "Unknown automation action: $action", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun toggleFlashlight(): Boolean {
        // Placeholder for flashlight toggle. Requires Camera permission and CameraManager.
        Toast.makeText(context, "Flashlight toggle (Placeholder)", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun controlVolume(): Boolean {
        // Placeholder for volume control. Requires AudioManager.
        Toast.makeText(context, "Volume control (Placeholder)", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun openSettings(): Boolean {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun handleInfoRequest(message: String?): Boolean {
        // This intent is handled by Gemini, so we just acknowledge it here.
        Toast.makeText(context, "Information request: $message (Handled by AI)", Toast.LENGTH_SHORT).show()
        return true
    }
}
