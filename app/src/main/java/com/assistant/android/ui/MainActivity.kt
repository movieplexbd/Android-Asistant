package com.assistant.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.assistant.android.R
import com.assistant.android.core.MasterController
import com.assistant.android.service.ForegroundService
import com.assistant.android.service.OverlayService
import com.assistant.android.service.WakeWordService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var stateDot: View
    private lateinit var statsTextView: TextView
    private lateinit var partialTextView: TextView
    private lateinit var heardTextView: TextView
    private lateinit var understoodTextView: TextView
    private lateinit var actionTextView: TextView
    private lateinit var replyTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var waveform: WaveformView
    private lateinit var voiceButton: MaterialButton
    private lateinit var wakeWordButton: MaterialButton
    private lateinit var overlayButton: MaterialButton
    private lateinit var testVoiceButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var copyDebugButton: MaterialButton
    private lateinit var clearDebugButton: MaterialButton
    private lateinit var errorShortTextView: TextView
    private lateinit var errorDetailTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        stateDot = findViewById(R.id.stateDot)
        statsTextView = findViewById(R.id.statsTextView)
        partialTextView = findViewById(R.id.partialTextView)
        heardTextView = findViewById(R.id.heardTextView)
        understoodTextView = findViewById(R.id.understoodTextView)
        actionTextView = findViewById(R.id.actionTextView)
        replyTextView = findViewById(R.id.replyTextView)
        logTextView = findViewById(R.id.logTextView)
        waveform = findViewById(R.id.waveform)
        voiceButton = findViewById(R.id.voiceButton)
        wakeWordButton = findViewById(R.id.wakeWordButton)
        overlayButton = findViewById(R.id.overlayButton)
        testVoiceButton = findViewById(R.id.testVoiceButton)
        settingsButton = findViewById(R.id.settingsButton)
        copyDebugButton = findViewById(R.id.copyDebugButton)
        clearDebugButton = findViewById(R.id.clearDebugButton)
        errorShortTextView = findViewById(R.id.errorShortTextView)
        errorDetailTextView = findViewById(R.id.errorDetailTextView)

        refreshButtonLabels()
        checkPermissions()

        voiceButton.setOnClickListener { toggleService(ForegroundService::class.java, voiceButton, "Assistant") }
        wakeWordButton.setOnClickListener { toggleService(WakeWordService::class.java, wakeWordButton, "Wake Word (Hey Jarvis)") }
        overlayButton.setOnClickListener { toggleOverlay() }
        testVoiceButton.setOnClickListener { runVoiceDiagnostics() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        copyDebugButton.setOnClickListener {
            val text = buildDebugBundle()
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Jarvis debug", text))
            Toast.makeText(this, "Debug info copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        clearDebugButton.setOnClickListener {
            MasterController.clearError()
            errorShortTextView.text = "No errors yet."
            errorDetailTextView.text = "—"
        }

        observeMaster()
    }

    override fun onResume() {
        super.onResume()
        refreshButtonLabels()
    }

    private fun buildDebugBundle(): String {
        val err = MasterController.lastError.value
        val state = MasterController.state.value
        val stats = MasterController.stats.value
        val logs = MasterController.logs.value
        return buildString {
            appendLine("=== JARVIS DEBUG SNAPSHOT ===")
            appendLine("State: $state")
            appendLine("Stats: cmd=${stats.commandsHandled} routines=${stats.routinesRun} translations=${stats.translationsDone} errors=${stats.errors}")
            appendLine("Last command: ${MasterController.lastCommand.value ?: "(none)"}")
            appendLine("Last reply  : ${MasterController.lastReply.value ?: "(none)"}")
            appendLine("Last action : ${MasterController.actionInfo.value ?: "(none)"}")
            appendLine("Last error  : ${err?.short ?: "(none)"}")
            appendLine()
            appendLine("--- Error detail ---")
            appendLine(err?.detail ?: "(none)")
            appendLine()
            appendLine("--- Recent activity log ---")
            logs.forEach { appendLine(it) }
        }
    }

    private fun observeMaster() {
        lifecycleScope.launch {
            MasterController.state.collect { state ->
                statusTextView.text = state.name
                val color = when (state) {
                    MasterController.State.IDLE -> R.color.state_idle
                    MasterController.State.LISTENING -> R.color.state_listening
                    MasterController.State.THINKING -> R.color.state_thinking
                    MasterController.State.EXECUTING -> R.color.state_executing
                    MasterController.State.SPEAKING -> R.color.state_speaking
                    MasterController.State.ERROR -> R.color.state_error
                }
                stateDot.setBackgroundColor(ContextCompat.getColor(this@MainActivity, color))
                waveform.setColor(ContextCompat.getColor(this@MainActivity, color))
            }
        }
        lifecycleScope.launch {
            MasterController.audioLevel.collect { lvl -> waveform.setLevel(lvl) }
        }
        lifecycleScope.launch {
            MasterController.partial.collect { p -> partialTextView.text = if (p.isBlank()) "—" else p }
        }
        lifecycleScope.launch {
            MasterController.lastCommand.collect { c -> heardTextView.text = c?.takeIf { it.isNotBlank() } ?: "—" }
        }
        lifecycleScope.launch {
            MasterController.understood.collect { u -> understoodTextView.text = u?.takeIf { it.isNotBlank() } ?: "—" }
        }
        lifecycleScope.launch {
            MasterController.actionInfo.collect { a -> actionTextView.text = a?.takeIf { it.isNotBlank() } ?: "—" }
        }
        lifecycleScope.launch {
            MasterController.lastReply.collect { r -> replyTextView.text = r?.takeIf { it.isNotBlank() } ?: "—" }
        }
        lifecycleScope.launch {
            MasterController.logs.collect { lines ->
                logTextView.text = if (lines.isEmpty()) "No activity yet."
                    else lines.take(15).joinToString("\n")
            }
        }
        lifecycleScope.launch {
            MasterController.stats.collect { s ->
                statsTextView.text = "Commands: ${s.commandsHandled}  •  Routines: ${s.routinesRun}  •  Translations: ${s.translationsDone}  •  Errors: ${s.errors}"
            }
        }
        lifecycleScope.launch {
            MasterController.lastError.collect { err ->
                if (err == null) {
                    errorShortTextView.text = "No errors yet."
                    errorDetailTextView.text = "—"
                } else {
                    errorShortTextView.text = err.short
                    errorDetailTextView.text = err.detail
                }
            }
        }
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) needed.add(it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Toast.makeText(this, if (granted) "Permissions granted" else "Some permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleService(cls: Class<*>, button: MaterialButton, name: String) {
        val serviceIntent = Intent(this, cls)
        if (isServiceRunning(cls)) {
            stopService(serviceIntent)
            button.text = "Start $name"
        } else {
            ContextCompat.startForegroundService(this, serviceIntent)
            button.text = "Stop $name"
        }
    }

    private fun refreshButtonLabels() {
        voiceButton.text = if (isServiceRunning(ForegroundService::class.java)) "Stop Assistant" else "Start Assistant"
        wakeWordButton.text = if (isServiceRunning(WakeWordService::class.java))
            "Stop Wake Word (Hey Jarvis)" else "Start Wake Word (Hey Jarvis)"
        overlayButton.text = if (isServiceRunning(OverlayService::class.java))
            "Hide Floating Bubble" else "Show Floating Bubble"
    }

    private fun toggleOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        toggleService(OverlayService::class.java, overlayButton, "Overlay")
    }

    private fun runVoiceDiagnostics() {
        val sb = StringBuilder()
        sb.appendLine("--- VOICE DIAGNOSTICS ---")

        // 1. Check Permissions
        val micPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        sb.appendLine("Microphone Permission: ${if (micPerm) "GRANTED ✓" else "DENIED ✗"}")

        // 2. Check if Speech Recognition is available
        val available = android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        sb.appendLine("System STT Available: ${if (available) "YES ✓" else "NO ✗"}")

        // 3. Check if Google app is enabled (common cause of STT failure)
        try {
            val googleAppEnabled = packageManager.getApplicationInfo("com.google.android.googlequicksearchbox", 0).enabled
            sb.appendLine("Google App (STT Engine): ${if (googleAppEnabled) "ENABLED ✓" else "DISABLED ✗"}")
        } catch (e: Exception) {
            sb.appendLine("Google App (STT Engine): NOT FOUND ✗")
        }

        // 4. Check Audio Manager
        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val isMicMuted = am.isMicrophoneMute
        sb.appendLine("Mic Hardware Muted: ${if (isMicMuted) "YES ✗" else "NO ✓"}")

        // 5. Check Service Status
        val isRunning = isServiceRunning(ForegroundService::class.java)
        sb.appendLine("Assistant Service: ${if (isRunning) "RUNNING ✓" else "STOPPED"}")

        val result = sb.toString()
        MasterController.recordLog("Diagnostics run")
        MasterController.recordError("Voice Diagnostics", result)

        Toast.makeText(this, "Diagnostics complete. Check Debug Console.", Toast.LENGTH_LONG).show()

        // If service is running, trigger a test listen
        if (isRunning) {
            val intent = Intent(this, ForegroundService::class.java).apply {
                action = ForegroundService.ACTION_WAKE
            }
            startService(intent)
        } else {
            Toast.makeText(this, "Start Assistant first to test live hearing.", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className == serviceClass.name }
    }

    companion object {
        private const val REQ_PERMS = 1001
    }
}
