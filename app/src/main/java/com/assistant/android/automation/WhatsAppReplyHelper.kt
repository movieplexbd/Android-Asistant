package com.assistant.android.automation

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.assistant.android.core.MasterController
import com.assistant.android.notif.JarvisNotificationListener

/**
 * Sends a reply to the latest captured WhatsApp / Messenger / Telegram notification by
 * filling its RemoteInput and firing the action's PendingIntent.
 *
 * Requires Notification Listener permission to be granted by the user (Settings →
 * Notification access → Jarvis).
 */
object WhatsAppReplyHelper {

    private const val TAG = "WhatsAppReply"

    fun lastMessageSummary(): String? {
        val msgs = JarvisNotificationListener.lastMessages(2)
        if (msgs.isEmpty()) return null
        return msgs.joinToString("\n") { c ->
            val app = friendlyName(c.packageName)
            val from = c.title.ifBlank { "(unknown)" }
            "From [$app/$from]: ${c.text}"
        }
    }

    fun reply(context: Context, replyText: String): Boolean {
        if (replyText.isBlank()) {
            MasterController.recordError("Empty reply text",
                "I had nothing to send — Gemini did not produce a reply body.")
            return false
        }
        val target = JarvisNotificationListener.latestMessage()
        if (target == null) {
            MasterController.recordError("No recent message to reply to",
                "Either Notification Access isn't granted, or no messaging notification has arrived yet.")
            return false
        }
        val action = target.replyAction
        if (action == null) {
            MasterController.recordError("No inline reply action on this notification",
                "This notification from ${target.packageName} doesn't expose a Direct-Reply action. " +
                "Try replying right after the message arrives, before tapping/dismissing it.")
            return false
        }
        val remoteInputs = action.remoteInputs ?: emptyArray()
        if (remoteInputs.isEmpty()) {
            MasterController.recordError("Action has no RemoteInput", "Cannot fill a text field.")
            return false
        }

        return try {
            val intent = Intent()
            val bundle = Bundle()
            for (ri in remoteInputs) bundle.putCharSequence(ri.resultKey, replyText)
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            action.actionIntent.send(context, 0, intent)
            MasterController.recordLog("✓ Reply sent to ${friendlyName(target.packageName)}: \"$replyText\"")
            true
        } catch (e: Exception) {
            Log.e(TAG, "reply failed", e)
            MasterController.recordError("Reply failed", "${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun friendlyName(pkg: String): String = when (pkg) {
        "com.whatsapp" -> "WhatsApp"
        "com.whatsapp.w4b" -> "WhatsApp Business"
        "com.facebook.orca" -> "Messenger"
        "org.telegram.messenger" -> "Telegram"
        "org.thunderdog.challegram" -> "Telegram X"
        else -> pkg
    }
}
