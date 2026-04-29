package com.assistant.android.notif

import android.app.Notification
import android.app.RemoteInput
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.assistant.android.core.MasterController
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Listens to incoming notifications system-wide and remembers the most recent ones — used by
 * the WhatsApp / Messenger auto-reply pipeline.
 *
 * Captures:
 *  - the visible text + sender of each notification
 *  - any RemoteInput action (so we can later reply directly without opening the app)
 */
class JarvisNotificationListener : NotificationListenerService() {

    data class Captured(
        val packageName: String,
        val title: String,
        val text: String,
        val timestampMs: Long,
        val replyAction: Notification.Action?
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return
            val extras = n.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) return

            // Find the first action that has a RemoteInput (= reply box).
            val replyAction = n.actions?.firstOrNull { a ->
                a.remoteInputs?.any { it.allowFreeFormInput } == true
            }

            val captured = Captured(
                packageName = sbn.packageName,
                title = title,
                text = text,
                timestampMs = sbn.postTime,
                replyAction = replyAction
            )
            recents.addFirst(captured)
            if (recents.size > MAX_KEEP) recents.pollLast()

            instance = this
            Log.d(TAG, "Captured ${sbn.packageName}: $title — $text  (reply=${replyAction != null})")
        } catch (e: Exception) {
            Log.w(TAG, "onNotificationPosted: ${e.message}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        MasterController.recordLog("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
        MasterController.recordLog("Notification listener disconnected")
    }

    companion object {
        private const val TAG = "JarvisNotifListener"
        private const val MAX_KEEP = 30

        @Volatile var instance: JarvisNotificationListener? = null

        /** Newest first. */
        val recents: ConcurrentLinkedDeque<Captured> = ConcurrentLinkedDeque()

        /** Returns the latest WhatsApp / Messenger / Telegram message we've seen, if any. */
        fun latestMessage(): Captured? {
            val packages = setOf(
                "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca",
                "org.telegram.messenger", "org.thunderdog.challegram"
            )
            return recents.firstOrNull { it.packageName in packages }
        }

        /** Newest 'count' messages from messaging apps. */
        fun lastMessages(count: Int = 2): List<Captured> {
            val packages = setOf(
                "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca",
                "org.telegram.messenger", "org.thunderdog.challegram"
            )
            return recents.filter { it.packageName in packages }.take(count)
        }
    }
}
