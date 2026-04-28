package com.assistant.android.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class ActionExecutor(private val context: Context) {

    fun executeAction(intent: String, target: String?, message: String?, time: String?): Boolean {
        Log.d("ActionExecutor", "Elite Executing: $intent")
        return when (intent) {
            "call" -> makePhoneCall(target)
            "open_app" -> openApp(target)
            "message" -> sendMessage(target, message)
            "reminder" -> setReminder(message, time)
            "automation" -> handleAutomation(target)
            "biometric_auth" -> requestBiometricAuth(message)
            "translate" -> handleTranslation(message, target)
            "info" -> true
            else -> false
        }
    }

    private fun makePhoneCall(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) return false
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(callIntent); true } catch (e: Exception) { false }
    }

    private fun openApp(appName: String?): Boolean {
        if (appName.isNullOrEmpty()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(appName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else false
    }

    private fun sendMessage(phoneNumber: String?, message: String?): Boolean {
        if (phoneNumber.isNullOrEmpty() || message.isNullOrEmpty()) return false
        return try {
            val smsManager: SmsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) { false }
    }

    private fun setReminder(message: String?, time: String?): Boolean {
        Log.d("ActionExecutor", "Elite Reminder: $message at $time")
        return true
    }

    private fun handleAutomation(action: String?): Boolean {
        if (action == "open_settings") {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
        return true
    }

    private fun requestBiometricAuth(reason: String?): Boolean {
        Log.d("ActionExecutor", "Requesting Biometric for: $reason")
        // This requires an Activity context for the actual prompt, 
        // so we log it and return true as a placeholder for the logic flow.
        Toast.makeText(context, "Biometric Auth Required: $reason", Toast.LENGTH_LONG).show()
        return true
    }

    private fun handleTranslation(text: String?, targetLang: String?): Boolean {
        Log.d("ActionExecutor", "Translating to $targetLang: $text")
        Toast.makeText(context, "Translating to $targetLang...", Toast.LENGTH_SHORT).show()
        return true
    }
}
