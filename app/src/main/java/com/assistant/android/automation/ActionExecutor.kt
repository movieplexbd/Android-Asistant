package com.assistant.android.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import org.json.JSONObject

/**
 * Executes a single intent. Supports the v4 NEXUS contract — including routines (chained intents)
 * and next_step follow-ups.
 */
class ActionExecutor(private val context: Context) {

    private val tag = "ActionExecutor"

    fun executeIntent(json: JSONObject): Boolean {
        val intent = json.optString("intent")
        val target = json.optString("target").takeIf { it.isNotEmpty() }
        val message = json.optString("message").takeIf { it.isNotEmpty() }
        val time = json.optString("time").takeIf { it.isNotEmpty() }
        return executeAction(intent, target, message, time)
    }

    fun executeAction(intent: String, target: String?, message: String?, time: String?): Boolean {
        Log.d(tag, "Executing intent=$intent target=$target")
        return when (intent) {
            "call" -> makePhoneCall(target)
            "open_app" -> openApp(target)
            "message" -> sendMessage(target, message)
            "reminder" -> setReminder(message, time)
            "automation" -> handleAutomation(target)
            "routine" -> true // Routine engine handles steps separately.
            "translate" -> handleTranslation(message, target)
            "vision" -> true // Vision is triggered from UI/camera flow.
            "biometric_auth" -> requestBiometricAuth(message)
            "info", "none", "" -> true
            else -> {
                Log.w(tag, "Unknown intent: $intent")
                false
            }
        }
    }

    private fun makePhoneCall(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrEmpty()) return false
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(callIntent); true }.getOrDefault(false)
    }

    private fun openApp(appNameOrPackage: String?): Boolean {
        if (appNameOrPackage.isNullOrEmpty()) return false
        // Try as package id first.
        val byPackage = context.packageManager.getLaunchIntentForPackage(appNameOrPackage)
        if (byPackage != null) {
            byPackage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(byPackage)
            return true
        }
        // Fall back to common-name resolution.
        val resolved = COMMON_APP_PACKAGES[appNameOrPackage.lowercase()]
            ?: COMMON_APP_PACKAGES.entries.firstOrNull { appNameOrPackage.lowercase().contains(it.key) }?.value
        if (resolved != null) {
            val it = context.packageManager.getLaunchIntentForPackage(resolved)
            if (it != null) {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
                return true
            }
        }
        // Fall back to web search.
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$appNameOrPackage"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(web); true }.getOrDefault(false)
    }

    private fun sendMessage(phoneNumber: String?, message: String?): Boolean {
        if (phoneNumber.isNullOrEmpty() || message.isNullOrEmpty()) return false
        return try {
            @Suppress("DEPRECATION")
            val smsManager: SmsManager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
                else SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) {
            Log.e(tag, "SMS failed: ${e.message}")
            false
        }
    }

    private fun setReminder(message: String?, time: String?): Boolean {
        val msg = message ?: "Reminder"
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, msg)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Best effort: parse "7:30" / "07:30 AM"
            time?.let { parseTime(it) }?.let { (h, m) ->
                intent.putExtra(AlarmClock.EXTRA_HOUR, h)
                intent.putExtra(AlarmClock.EXTRA_MINUTES, m)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Reminder failed: ${e.message}")
            false
        }
    }

    private fun parseTime(text: String): Pair<Int, Int>? {
        val regex = Regex("""(\d{1,2})[:.](\d{2})\s*(AM|PM|am|pm)?""")
        val match = regex.find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val ampm = match.groupValues[3].uppercase()
        if (ampm == "PM" && hour < 12) hour += 12
        if (ampm == "AM" && hour == 12) hour = 0
        return hour to minute
    }

    private fun handleAutomation(action: String?): Boolean {
        return when (action) {
            "open_settings" -> startSettings(Settings.ACTION_SETTINGS)
            "open_wifi" -> startSettings(Settings.ACTION_WIFI_SETTINGS)
            "open_bluetooth" -> startSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            "open_location" -> startSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "open_battery" -> startSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "volume_up" -> changeVolume(AudioManager.ADJUST_RAISE)
            "volume_down" -> changeVolume(AudioManager.ADJUST_LOWER)
            "mute" -> changeVolume(AudioManager.ADJUST_MUTE)
            "open_camera" -> {
                val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(i); true }.getOrDefault(false)
            }
            else -> false
        }
    }

    private fun startSettings(action: String): Boolean {
        return runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false)
    }

    private fun changeVolume(direction: Int): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return true
    }

    private fun requestBiometricAuth(reason: String?): Boolean {
        Log.d(tag, "Biometric auth requested: $reason")
        Toast.makeText(context, "Authentication required: ${reason ?: "secure action"}", Toast.LENGTH_LONG).show()
        return true
    }

    private fun handleTranslation(text: String?, targetLang: String?): Boolean {
        Log.d(tag, "Translate to $targetLang: $text")
        // The actual translation is performed by GeminiClient (called from ForegroundService).
        return true
    }

    companion object {
        private val COMMON_APP_PACKAGES = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "messenger" to "com.facebook.orca",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "settings" to "com.android.settings",
            "phone" to "com.google.android.dialer",
            "camera" to "com.android.camera"
        )
    }
}
