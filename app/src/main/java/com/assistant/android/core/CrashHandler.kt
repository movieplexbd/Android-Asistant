package com.assistant.android.core

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught-exception handler that writes the full crash trace to a file
 * so the user can read it on next launch and copy-share it. Without this, crashes
 * vanish before the UI can show them.
 */
class CrashHandler private constructor(
    private val appContext: Context,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val trace = e.stackTraceToString()
            val report = buildString {
                appendLine("=== JARVIS CRASH REPORT ===")
                appendLine("Time:    $ts")
                appendLine("Thread:  ${t.name}")
                appendLine("Class:   ${e.javaClass.name}")
                appendLine("Message: ${e.message}")
                appendLine()
                appendLine("--- Full stacktrace ---")
                append(trace)
            }
            crashFile(appContext).writeText(report)
            Log.e(TAG, "Crash recorded to ${crashFile(appContext).absolutePath}")
        } catch (writeError: Exception) {
            Log.e(TAG, "Failed to record crash: ${writeError.message}")
        }
        previous?.uncaughtException(t, e)
    }

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_FILENAME = "last_crash.txt"

        fun install(appContext: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext.applicationContext, current))
        }

        fun crashFile(context: Context): File =
            File(context.filesDir, CRASH_FILENAME)

        /** Returns the contents of the last crash report, or null if no crash recorded. */
        fun readLastCrash(context: Context): String? {
            val f = crashFile(context)
            return if (f.exists() && f.length() > 0) f.readText() else null
        }

        fun clearLastCrash(context: Context) {
            crashFile(context).delete()
        }
    }
}
