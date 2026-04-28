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
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.assistant.android.R
import com.assistant.android.core.CrashHandler
import com.assistant.android.core.MasterController
import com.assistant.android.diag.BugReporter
import com.assistant.android.service.ForegroundService
import com.assistant.android.service.OverlayService
import com.assistant.android.service.WakeWordService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private lateinit var exportBugButton: MaterialButton
    private lateinit var errorShortTextView: TextView
    private lateinit var errorDetailTextView: TextView

    // Chat UI
    private lateinit var chatScroll: ScrollView
    private lateinit var chatHistoryTextView: TextView
    private lateinit var chatInputEdit: EditText
    private lateinit var sendChatButton: MaterialButton
    private lateinit var clearChatButton: MaterialButton

    // STT warning + quick actions
    private lateinit var sttWarningCard: MaterialCardView
    private lateinit var installSttButton: MaterialButton
    private lateinit var quickActionsGroup: ChipGroup

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

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
        exportBugButton = findViewById(R.id.exportBugButton)
        errorShortTextView = findViewById(R.id.errorShortTextView)
        errorDetailTextView = findViewById(R.id.errorDetailTextView)

        chatScroll = findViewById(R.id.chatScroll)
        chatHistoryTextView = findViewById(R.id.chatHistoryTextView)
        chatInputEdit = findViewById(R.id.chatInputEdit)
        sendChatButton = findViewById(R.id.sendChatButton)
        clearChatButton = findViewById(R.id.clearChatButton)

        sttWarningCard = findViewById(R.id.sttWarningCard)
        installSttButton = findViewById(R.id.installSttButton)
        quickActionsGroup = findViewById(R.id.quickActionsGroup)

        refreshButtonLabels()
        checkPermissions()
        showStoredCrashIfAny()
        showSttWarningIfNeeded()
        buildQuickActions()

        // long-press copy on every important text — so user can grab anything that's wrong
        listOf(statusTextView, partialTextView, heardTextView, understoodTextView,
               actionTextView, replyTextView, statsTextView, logTextView,
               errorShortTextView, errorDetailTextView, chatHistoryTextView).forEach { tv ->
            tv.setOnLongClickListener {
                copyToClipboard("Jarvis text", tv.text.toString())
                true
            }
        }

        voiceButton.setOnClickListener { toggleService(ForegroundService::class.java, voiceButton, "Assistant") }
        wakeWordButton.setOnClickListener { toggleService(WakeWordService::class.java, wakeWordButton, "Wake Word (Hey Jarvis)") }
        overlayButton.setOnClickListener { toggleOverlay() }
        testVoiceButton.setOnClickListener { runVoiceDiagnostics() }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        copyDebugButton.setOnClickListener {
            copyToClipboard("Jarvis debug", buildDebugBundle())
            Toast.makeText(this, "Debug info copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        clearDebugButton.setOnClickListener {
            MasterController.clearError()
            errorShortTextView.text = "No errors yet."
            errorDetailTextView.text = "—"
        }
        exportBugButton.setOnClickListener { exportBugReport() }

        sendChatButton.setOnClickListener { sendChatCommand() }
        chatInputEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendChatCommand(); true } else false
        }
        clearChatButton.setOnClickListener {
            MasterController.clearChatHistory()
            chatHistoryTextView.text = "No messages yet. Type a command below to begin."
        }

        installSttButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        observeMaster()
    }

    override fun onResume() {
        super.onResume()
        refreshButtonLabels()
        showSttWarningIfNeeded()
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    /** Show a one-time dialog if the previous session crashed. */
    private fun showStoredCrashIfAny() {
        val crash = CrashHandler.readLastCrash(this) ?: return
        AlertDialog.Builder(this)
            .setTitle("Last session crashed")
            .setMessage(crash.take(2500))
            .setPositiveButton("Copy") { _, _ ->
                copyToClipboard("Jarvis crash", crash)
                CrashHandler.clearLastCrash(this)
            }
            .setNegativeButton("Dismiss") { d, _ ->
                CrashHandler.clearLastCrash(this); d.dismiss()
            }
            .show()
        MasterController.recordError("Previous crash detected", crash)
    }

    private fun showSttWarningIfNeeded() {
        val available = android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        sttWarningCard.visibility = if (available) View.GONE else View.VISIBLE
    }

    private fun buildQuickActions() {
        val actions = listOf(
            "Call ammu" to "ammu ke call koro",
            "Open WhatsApp" to "open whatsapp",
            "Open YouTube" to "open youtube",
            "Open Camera" to "open camera",
            "What time is it" to "what time is it",
            "Set alarm 7am" to "set alarm for 7 am",
            "Wifi settings" to "open wifi settings",
            "Volume up" to "volume up",
            "Volume down" to "volume down",
            "Take a selfie" to "open camera"
        )
        actions.forEach { (label, cmd) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = false
                setChipBackgroundColorResource(R.color.nexus_card_alt)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                setOnClickListener {
                    chatInputEdit.setText(cmd)
                    sendChatCommand()
                }
            }
            quickActionsGroup.addView(chip)
        }
    }

    /** Send the typed text to the foreground service so the same pipeline handles it. */
    private fun sendChatCommand() {
        val text = chatInputEdit.text.toString().trim()
        if (text.isEmpty()) return
        // Make sure the assistant service is running so the command can be processed.
        if (!isServiceRunning(ForegroundService::class.java)) {
            ContextCompat.startForegroundService(this, Intent(this, ForegroundService::class.java))
            refreshButtonLabels()
        }
        val intent = Intent(this, ForegroundService::class.java).apply {
            action = ForegroundService.ACTION_TEXT_COMMAND
            putExtra(ForegroundService.EXTRA_TEXT, text)
        }
        ContextCompat.startForegroundService(this, intent)
        chatInputEdit.setText("")
        // Also push it locally so the chat updates instantly; service will record reply.
        MasterController.appendChat(MasterController.ChatEntry(MasterController.Sender.USER, text))
    }

    /** Build, save, and share a comprehensive bug report. */
    private fun exportBugReport() {
        val report = BugReporter.build(this)
        try {
            val dir = File(filesDir, "reports").apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "jarvis_bug_$ts.txt").apply { writeText(report) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Jarvis bug report $ts")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, report.take(8000))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share bug report"))
        } catch (e: Exception) {
            // Fall back to plain copy if file sharing fails.
            copyToClipboard("Jarvis bug report", report)
            Toast.makeText(this, "Report copied (share failed: ${e.message})", Toast.LENGTH_LONG).show()
        }
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
        lifecycleScope.launch { MasterController.audioLevel.collect { lvl -> waveform.setLevel(lvl) } }
        lifecycleScope.launch { MasterController.partial.collect { p -> partialTextView.text = if (p.isBlank()) "—" else p } }
        lifecycleScope.launch { MasterController.lastCommand.collect { c -> heardTextView.text = c?.takeIf { it.isNotBlank() } ?: "—" } }
        lifecycleScope.launch { MasterController.understood.collect { u -> understoodTextView.text = u?.takeIf { it.isNotBlank() } ?: "—" } }
        lifecycleScope.launch { MasterController.actionInfo.collect { a -> actionTextView.text = a?.takeIf { it.isNotBlank() } ?: "—" } }
        lifecycleScope.launch { MasterController.lastReply.collect { r -> replyTextView.text = r?.takeIf { it.isNotBlank() } ?: "—" } }
        lifecycleScope.launch {
            MasterController.logs.collect { lines ->
                logTextView.text = if (lines.isEmpty()) "No activity yet." else lines.take(15).joinToString("\n")
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
        lifecycleScope.launch {
            MasterController.chatHistory.collect { entries ->
                if (entries.isEmpty()) {
                    chatHistoryTextView.text = "No messages yet. Type a command below to begin."
                } else {
                    chatHistoryTextView.text = entries.joinToString("\n\n") { e ->
                        val tag = when (e.sender) {
                            MasterController.Sender.USER -> "[${timeFmt.format(Date(e.timestampMs))}] YOU"
                            MasterController.Sender.JARVIS -> "[${timeFmt.format(Date(e.timestampMs))}] JARVIS"
                            MasterController.Sender.SYSTEM -> "[${timeFmt.format(Date(e.timestampMs))}] SYSTEM"
                        }
                        "$tag: ${e.text}"
                    }
                    chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
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
            Manifest.permission.READ_CONTACTS,
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

        val micPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        sb.appendLine("Microphone Permission: ${if (micPerm) "GRANTED ✓" else "DENIED ✗"}")

        val available = android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        sb.appendLine("System STT Available: ${if (available) "YES ✓" else "NO ✗"}")
        if (!available) {
            sb.appendLine("   -> ACTION: This device is missing a Speech-to-Text engine.")
            sb.appendLine("   -> FIX 1: Install 'Speech Services by Google' from Play Store.")
            sb.appendLine("   -> FIX 2: Use the chat box to type commands instead.")
        }

        try {
            val googleAppInfo = packageManager.getApplicationInfo("com.google.android.googlequicksearchbox", 0)
            sb.appendLine("Google App (STT Engine): ${if (googleAppInfo.enabled) "ENABLED ✓" else "DISABLED ✗"}")
            if (!googleAppInfo.enabled) sb.appendLine("   -> FIX: Enable the Google app in System Settings > Apps.")
        } catch (e: Exception) {
            sb.appendLine("Google App (STT Engine): NOT FOUND ✗")
            sb.appendLine("   -> FIX: Install the Google app or Speech Services by Google.")
        }

        val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        sb.appendLine("Mic Hardware Muted: ${if (am.isMicrophoneMute) "YES ✗" else "NO ✓"}")

        val isRunning = isServiceRunning(ForegroundService::class.java)
        sb.appendLine("Assistant Service: ${if (isRunning) "RUNNING ✓" else "STOPPED"}")

        val contactsPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        sb.appendLine("Contacts Permission: ${if (contactsPerm) "GRANTED ✓" else "DENIED ✗"}")
        if (!contactsPerm) sb.appendLine("   -> Without this, name-based calls (\"call ammu\") cannot find numbers.")

        val result = sb.toString()
        MasterController.recordLog("Diagnostics run")
        MasterController.recordError("Voice Diagnostics", result)

        Toast.makeText(this, "Diagnostics complete. Check Debug Console.", Toast.LENGTH_LONG).show()

        if (isRunning) {
            startService(Intent(this, ForegroundService::class.java).apply { action = ForegroundService.ACTION_WAKE })
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
