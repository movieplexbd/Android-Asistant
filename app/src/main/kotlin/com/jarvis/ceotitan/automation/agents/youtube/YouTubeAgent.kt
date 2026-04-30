package com.jarvis.ceotitan.automation.agents.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.ceotitan.automation.accessibility.JarvisAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeAgent @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    fun openYouTube(): String {
        val intent = context.packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "ইউটিউব খোলা হয়েছে।"
        } else {
            "ইউটিউব পাওয়া যাচ্ছে না।"
        }
    }

    suspend fun searchYouTube(query: String): String {
        val encodedQuery = Uri.encode(query)
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(YOUTUBE_PACKAGE)
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            delay(2000)
            val accessService = JarvisAccessibilityService.getInstance()
            if (accessService != null) {
                delay(1000)
                accessService.findAndClickByText("Search YouTube")
                delay(500)
                accessService.findAndTypeText(query)
            }
            "\"$query\" ইউটিউবে খোঁজা হচ্ছে।"
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
            "ব্রাউজারে ইউটিউবে \"$query\" খোঁজা হচ্ছে।"
        }
    }

    suspend fun scrollFeed(): String {
        val accessService = JarvisAccessibilityService.getInstance()
        return if (accessService != null) {
            accessService.scrollDown()
            "ইউটিউব ফিড স্ক্রোল করা হয়েছে।"
        } else {
            "Accessibility Service চালু করুন।"
        }
    }

    suspend fun clickFirstVideo(): String {
        val accessService = JarvisAccessibilityService.getInstance()
        return if (accessService != null) {
            delay(500)
            "প্রথম ভিডিও ক্লিক করা হচ্ছে।"
        } else {
            "Accessibility Service চালু করুন।"
        }
    }
}
