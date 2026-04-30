package com.jarvis.ceotitan.brain.local

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.jarvis.ceotitan.understanding.Intent as JarvisIntent
import com.jarvis.ceotitan.understanding.IntentResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBrain @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var isTorchOn = false

    suspend fun handle(intent: IntentResult): String? {
        return when (JarvisIntent.valueOf(intent.intent)) {
            JarvisIntent.FLASHLIGHT_ON -> handleFlashlightOn()
            JarvisIntent.FLASHLIGHT_OFF -> handleFlashlightOff()
            JarvisIntent.VOLUME_UP -> handleVolumeUp()
            JarvisIntent.VOLUME_DOWN -> handleVolumeDown()
            JarvisIntent.MUTE -> handleMute()
            JarvisIntent.VIBRATE -> handleVibrate()
            JarvisIntent.GO_HOME -> handleGoHome()
            JarvisIntent.GO_BACK -> handleGoBack()
            JarvisIntent.RECENT_APPS -> handleRecentApps()
            JarvisIntent.OPEN_APP -> handleOpenApp(intent.params["app"] ?: "")
            JarvisIntent.TAKE_PHOTO -> handleTakePhoto()
            JarvisIntent.OPEN_CAMERA -> handleOpenCamera()
            JarvisIntent.SET_ALARM -> handleSetAlarm(intent.params)
            JarvisIntent.SET_TIMER -> handleSetTimer(intent.params)
            JarvisIntent.OPEN_WIFI -> handleOpenWifi()
            JarvisIntent.OPEN_BLUETOOTH -> handleOpenBluetooth()
            JarvisIntent.OPEN_BRIGHTNESS -> handleOpenBrightness()
            JarvisIntent.LOCK_SCREEN -> handleLockScreen()
            JarvisIntent.TIME_DATE -> handleTimeDate()
            JarvisIntent.CALL_CONTACT -> handleCall(intent.params["contact"] ?: "")
            JarvisIntent.SEND_MESSAGE -> handleMessage(intent.params["contact"] ?: "", intent.params["message"] ?: "")
            else -> null
        }
    }

    private fun handleFlashlightOn(): String {
        return try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, true)
            isTorchOn = true
            "টর্চ জ্বালানো হয়েছে।"
        } catch (e: Exception) {
            "টর্চ জ্বালাতে পারছি না।"
        }
    }

    private fun handleFlashlightOff(): String {
        return try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, false)
            isTorchOn = false
            "টর্চ বন্ধ করা হয়েছে।"
        } catch (e: Exception) {
            "টর্চ বন্ধ করতে পারছি না।"
        }
    }

    private fun handleVolumeUp(): String {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        return "ভলিউম বাড়ানো হয়েছে।"
    }

    private fun handleVolumeDown(): String {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return "ভলিউম কমানো হয়েছে।"
    }

    private fun handleMute(): String {
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        return "সাইলেন্ট মোড চালু করা হয়েছে।"
    }

    private fun handleVibrate(): String {
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        return "ভাইব্রেট মোড চালু করা হয়েছে।"
    }

    private fun handleGoHome(): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "হোম স্ক্রিনে যাচ্ছি।"
    }

    private fun handleGoBack(): String {
        return "ব্যাক করা হচ্ছে।"
    }

    private fun handleRecentApps(): String {
        return "সাম্প্রতিক অ্যাপ দেখানো হচ্ছে।"
    }

    private fun handleOpenApp(appName: String): String {
        if (appName.isBlank()) return "কোন অ্যাপ খুলতে চান?"

        val normalizedApp = appName.lowercase().trim()
        val packageName = getPackageName(normalizedApp)

        if (packageName != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return "$appName খোলা হয়েছে।"
            }
        }

        val searchIntent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
        return "$appName অ্যাপ পাওয়া যাচ্ছে না।"
    }

    private fun getPackageName(appName: String): String? {
        val packageMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "camera" to "com.google.android.GoogleCamera",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "settings" to "com.android.settings",
            "play store" to "com.android.vending",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "chatgpt" to "com.openai.chatgpt",
            "snapchat" to "com.snapchat.android",
            "messenger" to "com.facebook.orca",
            "zoom" to "us.zoom.videomeetings",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "photos" to "com.google.android.apps.photos",
            "drive" to "com.google.android.apps.docs",
            "meet" to "com.google.android.apps.meetings",
            "amazon" to "com.amazon.mShop.android.shopping",
            "daraz" to "com.daraz.android",
            "shajgoj" to "com.shajgoj.app",
            "pathao" to "com.pathao.consumer",
            "bkash" to "com.bkash.banking",
            "nagad" to "com.nagad.android",
            "bikash" to "com.bkash.banking"
        )

        for ((key, pkg) in packageMap) {
            if (appName.contains(key)) return pkg
        }
        return null
    }

    private fun handleTakePhoto(): String {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "ক্যামেরা খোলা হয়েছে।"
    }

    private fun handleOpenCamera(): String {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "ক্যামেরা চালু করা হয়েছে।"
    }

    private fun handleSetAlarm(params: Map<String, String>): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "অ্যালার্ম সেট করা হচ্ছে।"
    }

    private fun handleSetTimer(params: Map<String, String>): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "টাইমার সেট করা হচ্ছে।"
    }

    private fun handleOpenWifi(): String {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "WiFi সেটিংস খোলা হয়েছে।"
    }

    private fun handleOpenBluetooth(): String {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "ব্লুটুথ সেটিংস খোলা হয়েছে।"
    }

    private fun handleOpenBrightness(): String {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "ব্রাইটনেস সেটিংস খোলা হয়েছে।"
    }

    private fun handleLockScreen(): String {
        return "স্ক্রিন লক করা হচ্ছে।"
    }

    private fun handleTimeDate(): String {
        val now = java.util.Date()
        val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val dateFormat = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
        return "এখন ${timeFormat.format(now)}, তারিখ ${dateFormat.format(now)}"
    }

    private fun handleCall(contact: String): String {
        if (contact.isBlank()) return "কাকে কল করবো?"
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = android.net.Uri.parse("tel:")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "$contact কে কল করা হচ্ছে।"
    }

    private fun handleMessage(contact: String, message: String): String {
        if (contact.isBlank()) return "কাকে মেসেজ পাঠাবো?"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://wa.me/")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return "$contact কে মেসেজ পাঠানো হচ্ছে।"
    }
}
