package com.assistant.android.automation

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.assistant.android.contacts.ContactResolver
import com.assistant.android.core.MasterController
import com.assistant.android.feature.Calculator
import com.assistant.android.feature.DeviceInfoProvider
import com.assistant.android.feature.NotesStore
import org.json.JSONObject

/**
 * Executes a single intent. Supports the v4.4 NEXUS contract — chained intents,
 * camera-auto-shutter, flashlight, screenshot, lock screen, WhatsApp auto-reply,
 * and the new feature intents (calc, device_info, note_add, note_read).
 *
 * Resolves contact names (e.g. "ammu", "boss") to phone numbers via ContactResolver
 * before dialing or texting.
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
            "sms", "send_sms", "message" -> sendMessage(target, message)
            "open_app" -> openApp(target)
            "reminder", "alarm", "set_alarm" -> setReminder(message ?: target, time)
            "automation" -> handleAutomation(target)
            "open_settings", "settings" -> startSettings(Settings.ACTION_SETTINGS)
            "open_wifi", "wifi_on", "wifi_off" -> startSettings(Settings.ACTION_WIFI_SETTINGS)
            "open_bluetooth", "bluetooth_on", "bluetooth_off" -> startSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            "open_location" -> startSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "open_battery" -> startSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "volume_up" -> changeVolume(AudioManager.ADJUST_RAISE)
            "volume_down" -> changeVolume(AudioManager.ADJUST_LOWER)
            "mute" -> changeVolume(AudioManager.ADJUST_MUTE)

            // Camera family
            "camera", "open_camera" -> CameraController.openCameraApp(context)
            "take_photo", "capture_photo" -> CameraController.capturePhotoAuto(context, useFrontCamera = false)
            "take_selfie" -> CameraController.capturePhotoAuto(context, useFrontCamera = true)
            "record_video" -> CameraController.openVideoCamera(context)

            // Flashlight
            "flashlight_on", "torch_on" -> FlashlightController.on(context)
            "flashlight_off", "torch_off" -> FlashlightController.off(context)
            "flashlight_toggle", "torch_toggle" -> FlashlightController.toggle(context)

            "search", "web_search" -> webSearch(target ?: message)
            "navigate" -> navigate(target)
            "home" -> a11yAction { it.goHome() }
            "back" -> a11yAction { it.goBack() }
            "recents" -> a11yAction { it.openRecents() }
            "scroll_up" -> a11yAction { it.scrollUp() }
            "scroll_down" -> a11yAction { it.scrollDown() }
            "tap_text" -> if (target != null) a11yAction { it.clickOnText(target) } else false
            "screenshot" -> a11yAction { it.takeScreenshot() }
            "lock_screen" -> a11yAction { it.lockScreen() }
            "notifications" -> a11yAction { it.openNotifications() }
            "quick_settings" -> a11yAction { it.openQuickSettings() }

            // WhatsApp / messaging auto-reply (uses NotificationListener + RemoteInput)
            "whatsapp_reply", "auto_reply" -> WhatsAppReplyHelper.reply(context, message ?: "")
            "read_messages" -> readLastMessages()

            // ── NEW FEATURES ────────────────────────────────────────────────
            "calc", "calculate" -> doCalc(message ?: target)
            "device_info", "battery", "ram", "storage" -> doDeviceInfo()
            "note_add", "save_note" -> doNoteAdd(message ?: target)
            "note_read", "show_notes", "list_notes" -> doNoteRead()

            "routine" -> true // Routine engine handles steps separately.
            "translate" -> handleTranslation(message, target)
            "vision" -> true
            "biometric_auth" -> requestBiometricAuth(message)
            "info_time", "info_date", "info", "answer", "chat", "none", "" -> true
            else -> {
                Log.w(tag, "Unknown intent: $intent")
                false
            }
        }
    }

    private fun doCalc(expression: String?): Boolean {
        val expr = expression ?: return false
        val result = Calculator.evaluate(expr)
        return if (result != null) {
            MasterController.recordLog("🧮 $expr = $result")
            MasterController.recordReply("$expr equals $result")
            true
        } else {
            MasterController.recordError("Could not evaluate", "Calculator could not parse: $expr")
            false
        }
    }

    private fun doDeviceInfo(): Boolean {
        val summary = DeviceInfoProvider.summary(context)
        MasterController.recordLog("📱 $summary")
        MasterController.recordReply(DeviceInfoProvider.spokenSummary(context))
        return true
    }

    private fun doNoteAdd(text: String?): Boolean {
        if (text.isNullOrBlank()) {
            MasterController.recordError("Empty note", "I had no text to save.")
            return false
        }
        NotesStore.add(context, text)
        MasterController.recordLog("📝 Note saved: $text")
        return true
    }

    private fun doNoteRead(): Boolean {
        val summary = NotesStore.spokenSummary(context, max = 5)
        MasterController.recordLog("📒 ${summary.lineSequence().count()} note line(s)")
        MasterController.recordReply(summary)
        return true
    }

    private fun readLastMessages(): Boolean {
        val summary = WhatsAppReplyHelper.lastMessageSummary()
        if (summary == null) {
            MasterController.recordError(
                "No recent messages captured",
                "Either Notification Access isn't granted, or no message has arrived since I started listening."
            )
            return false
        }
        MasterController.recordLog("📬 Last message: $summary")
        return true
    }

    private fun makePhoneCall(rawTarget: String?): Boolean {
        if (rawTarget.isNullOrBlank()) return false
        val phoneNumber = if (ContactResolver.isPhoneNumber(rawTarget)) {
            rawTarget
        } else {
            val resolved = ContactResolver.resolve(context, rawTarget)
            if (resolved == null) {
                MasterController.recordError(
                    "Contact not found: \"$rawTarget\"",
                    "I could not find anyone called \"$rawTarget\" in your contacts. Save them in your phonebook (or grant Contacts permission) and try again."
                )
                Toast.makeText(context, "No contact named \"$rawTarget\"", Toast.LENGTH_LONG).show()
                return false
            }
            resolved
        }
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(callIntent); true }.getOrElse {
            MasterController.recordError("Call failed", "${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    private fun openApp(appNameOrPackage: String?): Boolean {
        if (appNameOrPackage.isNullOrEmpty()) return false
        val byPackage = context.packageManager.getLaunchIntentForPackage(appNameOrPackage)
        if (byPackage != null) {
            byPackage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(byPackage)
            return true
        }
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
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$appNameOrPackage"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(web); true }.getOrDefault(false)
    }

    private fun sendMessage(rawTarget: String?, message: String?): Boolean {
        if (rawTarget.isNullOrEmpty() || message.isNullOrEmpty()) return false
        val phoneNumber = if (ContactResolver.isPhoneNumber(rawTarget)) rawTarget
            else ContactResolver.resolve(context, rawTarget) ?: run {
                MasterController.recordError(
                    "Contact not found: \"$rawTarget\"",
                    "I could not find a contact named \"$rawTarget\" to send the message to."
                )
                return false
            }
        return try {
            @Suppress("DEPRECATION")
            val smsManager: SmsManager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
                else SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) {
            Log.e(tag, "SMS failed: ${e.message}")
            MasterController.recordError("SMS failed", "${e.javaClass.name}: ${e.message}")
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
            "open_camera" -> CameraController.openCameraApp(context)
            "take_photo" -> CameraController.capturePhotoAuto(context)
            "take_selfie" -> CameraController.capturePhotoAuto(context, useFrontCamera = true)
            "flashlight_on" -> FlashlightController.on(context)
            "flashlight_off" -> FlashlightController.off(context)
            "screenshot" -> a11yAction { it.takeScreenshot() }
            else -> false
        }
    }

    private fun webSearch(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val i = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i); true }.getOrDefault(false)
    }

    private fun navigate(destination: String?): Boolean {
        if (destination.isNullOrBlank()) return false
        val i = Intent(Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=" + Uri.encode(destination)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i); true }.getOrElse {
            val web = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(destination)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(web); true }.getOrDefault(false)
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

    private fun a11yAction(block: (AssistantAccessibilityService) -> Boolean): Boolean {
        val svc = AssistantAccessibilityService.instance
        if (svc == null) {
            MasterController.recordError(
                "Accessibility service not enabled",
                "Settings → Accessibility → Jarvis Automation must be turned on for tap/scroll/back/home/screenshot actions."
            )
            return false
        }
        return runCatching { block(svc) }.getOrDefault(false)
    }

    private fun requestBiometricAuth(reason: String?): Boolean {
        Log.d(tag, "Biometric auth requested: $reason")
        Toast.makeText(context, "Authentication required: ${reason ?: "secure action"}", Toast.LENGTH_LONG).show()
        return true
    }

    private fun handleTranslation(text: String?, targetLang: String?): Boolean {
        Log.d(tag, "Translate to $targetLang: $text")
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
            "browser" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "mail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "settings" to "com.android.settings",
            "phone" to "com.google.android.dialer",
            "dialer" to "com.google.android.dialer",
            "camera" to "com.android.camera",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "alarm" to "com.google.android.deskclock",
            "contacts" to "com.google.android.contacts",
            "calculator" to "com.google.android.calculator",
            "drive" to "com.google.android.apps.docs",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "playstore" to "com.android.vending"
        )
    }
}
