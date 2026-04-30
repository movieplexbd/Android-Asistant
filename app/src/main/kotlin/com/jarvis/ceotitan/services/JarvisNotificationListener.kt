package com.jarvis.ceotitan.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisNotificationListener : NotificationListenerService() {

    companion object {
        private val _notifications = MutableStateFlow<List<JarvisNotification>>(emptyList())
        val notifications: StateFlow<List<JarvisNotification>> = _notifications.asStateFlow()

        fun getUnreadCount(): Int = _notifications.value.size
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val appName = packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(sbn.packageName, 0)
        ).toString()

        val jarvisNotif = JarvisNotification(
            id = sbn.id.toLong(),
            appName = appName,
            packageName = sbn.packageName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        _notifications.value = (_notifications.value + jarvisNotif).takeLast(50)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        _notifications.value = _notifications.value.filter { it.id != sbn.id.toLong() }
    }
}

data class JarvisNotification(
    val id: Long,
    val appName: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
