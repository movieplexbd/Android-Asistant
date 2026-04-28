package com.assistant.android

import android.app.Application
import com.assistant.android.core.CrashHandler

/**
 * Application bootstrap. Installs the crash handler so any uncaught exception is
 * preserved across launches and surfaced in the UI on next start.
 */
class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
