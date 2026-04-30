package com.jarvis.ceotitan.automation.agents.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.ceotitan.automation.accessibility.JarvisAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppAgent @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
    }

    suspend fun sendMessage(contact: String, message: String): String {
        val encodedMessage = Uri.encode(message)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/?text=$encodedMessage")
            setPackage(WHATSAPP_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            delay(2000)

            val accessService = JarvisAccessibilityService.getInstance()
            if (accessService != null) {
                accessService.findAndClickByText(contact)
                delay(1500)
                accessService.findAndTypeText(message)
                delay(500)
                accessService.findAndClickByText("Send")
                "$contact কে মেসেজ পাঠানো হয়েছে।"
            } else {
                "WhatsApp খোলা হয়েছে। Accessibility Service চালু করলে স্বয়ংক্রিয় মেসেজ পাঠানো যাবে।"
            }
        } catch (e: Exception) {
            "WhatsApp পাওয়া যাচ্ছে না। ইন্সটল করুন।"
        }
    }

    fun openWhatsApp(): String {
        val intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "WhatsApp খোলা হয়েছে।"
        } else {
            "WhatsApp পাওয়া যাচ্ছে না।"
        }
    }

    fun openChat(contact: String): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$contact")
            setPackage(WHATSAPP_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            "$contact এর চ্যাট খোলা হয়েছে।"
        } catch (e: Exception) {
            "WhatsApp চ্যাট খুলতে পারছি না।"
        }
    }
}
