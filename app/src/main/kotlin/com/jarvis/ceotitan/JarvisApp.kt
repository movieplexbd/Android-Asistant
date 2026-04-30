package com.jarvis.ceotitan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JarvisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val listeningChannel = NotificationChannel(
                CHANNEL_LISTENING,
                "JARVIS Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JARVIS is actively listening for commands"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "JARVIS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts from JARVIS"
            }

            val silentChannel = NotificationChannel(
                CHANNEL_SILENT,
                "JARVIS Background",
                NotificationManager.IMPORTANCE_NONE
            ).apply {
                description = "JARVIS background operations"
                setShowBadge(false)
            }

            manager.createNotificationChannels(
                listOf(listeningChannel, alertChannel, silentChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_LISTENING = "jarvis_listening"
        const val CHANNEL_ALERTS = "jarvis_alerts"
        const val CHANNEL_SILENT = "jarvis_silent"
    }
}
