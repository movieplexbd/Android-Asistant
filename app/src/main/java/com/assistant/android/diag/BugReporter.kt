package com.assistant.android.diag

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.assistant.android.BuildConfig
import com.assistant.android.core.ApiKeyManager
import com.assistant.android.core.CrashHandler
import com.assistant.android.core.MasterController
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single-button "give me everything wrong with this app" generator.
 * Bundles state, recent activity log, last error, last crash, device + permission info,
 * and a tail of the system log into one big shareable text blob.
 */
object BugReporter {

    private const val LOGCAT_LINES = 250

    fun build(context: Context): String {
        val sb = StringBuilder()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        sb.appendLine("=== JARVIS FULL BUG REPORT ===")
        sb.appendLine("Generated: $ts")
        sb.appendLine("App version: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        sb.appendLine()

        sb.appendLine("--- DEVICE ---")
        sb.appendLine("Device   : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android  : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Build    : ${Build.DISPLAY}")
        sb.appendLine()

        sb.appendLine("--- API KEY / MODEL ---")
        sb.appendLine("Custom key set: ${ApiKeyManager.hasUserKey(context)}")
        sb.appendLine("Active key   : ${ApiKeyManager.mask(ApiKeyManager.getApiKey(context))}")
        sb.appendLine("Active model : ${ApiKeyManager.getModel(context)}")
        sb.appendLine()

        sb.appendLine("--- PERMISSIONS ---")
        listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ).forEach { p ->
            val granted = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            sb.appendLine("  ${if (granted) "✓" else "✗"} ${p.removePrefix("android.permission.")}")
        }
        val canDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        sb.appendLine("  ${if (canDrawOverlay) "✓" else "✗"} OVERLAY (draw over apps)")
        sb.appendLine()

        sb.appendLine("--- VOICE STACK ---")
        val sttAvailable = android.speech.SpeechRecognizer.isRecognitionAvailable(context)
        sb.appendLine("System STT: ${if (sttAvailable) "AVAILABLE" else "MISSING (install 'Speech Services by Google')"}")
        try {
            val info = context.packageManager.getApplicationInfo("com.google.android.googlequicksearchbox", 0)
            sb.appendLine("Google App: ${if (info.enabled) "ENABLED" else "DISABLED"}")
        } catch (e: Exception) {
            sb.appendLine("Google App: NOT INSTALLED")
        }
        sb.appendLine()

        sb.appendLine("--- ASSISTANT STATE ---")
        sb.appendLine("State    : ${MasterController.state.value}")
        val stats = MasterController.stats.value
        sb.appendLine("Stats    : cmd=${stats.commandsHandled}  routines=${stats.routinesRun}  translations=${stats.translationsDone}  errors=${stats.errors}")
        sb.appendLine("Last cmd : ${MasterController.lastCommand.value ?: "(none)"}")
        sb.appendLine("Last reply: ${MasterController.lastReply.value ?: "(none)"}")
        sb.appendLine("Last action: ${MasterController.actionInfo.value ?: "(none)"}")
        val err = MasterController.lastError.value
        sb.appendLine("Last error: ${err?.short ?: "(none)"}")
        sb.appendLine()

        if (err != null) {
            sb.appendLine("--- LAST ERROR DETAIL ---")
            sb.appendLine(err.detail)
            sb.appendLine()
        }

        val crash = CrashHandler.readLastCrash(context)
        if (crash != null) {
            sb.appendLine("--- LAST RECORDED CRASH ---")
            sb.appendLine(crash)
            sb.appendLine()
        }

        sb.appendLine("--- ACTIVITY LOG (newest first) ---")
        MasterController.logs.value.forEach { sb.appendLine(it) }
        sb.appendLine()

        sb.appendLine("--- LOGCAT TAIL (last $LOGCAT_LINES lines) ---")
        sb.appendLine(readLogcatTail())
        return sb.toString()
    }

    private fun readLogcatTail(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", LOGCAT_LINES.toString()))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val out = StringBuilder()
            reader.useLines { lines ->
                lines.forEach { out.appendLine(it) }
            }
            out.toString()
        } catch (e: Exception) {
            "Could not read logcat: ${e.message}"
        }
    }
}
