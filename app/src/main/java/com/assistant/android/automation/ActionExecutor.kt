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
        Log.d("ActionExecutor", "Executing: $intent on $target")
        return when (intent) {
            "call" -> makePhoneCall(target)
            "open_app" -> openApp(target)
            "message" -> sendMessage(target, message)
            "reminder" -> setReminder(message, time)
            "automation" -> handleAutomation(target)
            "smart_home" -> handleSmartHome(target, message)
            "chain_task" -> handleTaskChaining(message)
            "info" -> true // Handled by AI
            else -> false
        }
    }

    private fun makePhoneCall(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) return false
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(callIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openApp(appName: String?): Boolean {
        if (appName.isNullOrEmpty()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(appName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    private fun sendMessage(phoneNumber: String?, message: String?): Boolean {
        if (phoneNumber.isNullOrEmpty() || message.isNullOrEmpty()) return false
        return try {
            val smsManager: SmsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setReminder(message: String?, time: String?): Boolean {
        // Placeholder for AlarmManager/Calendar integration
        Log.d("ActionExecutor", "Reminder set: $message at $time")
        return true
    }

    private fun handleAutomation(action: String?): Boolean {
        return when (action) {
            "toggle_flashlight" -> true // Requires CameraManager
            "open_settings" -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
            else -> false
        }
    }

    private fun handleSmartHome(device: String?, action: String?): Boolean {
        // Placeholder for Google Home / Alexa integration
        Log.d("ActionExecutor", "Smart Home: $action on $device")
        Toast.makeText(context, "Smart Home: $action $device", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun handleTaskChaining(tasks: String?): Boolean {
        // Logic to parse and execute multiple tasks sequentially
        Log.d("ActionExecutor", "Chaining tasks: $tasks")
        return true
    }
}
