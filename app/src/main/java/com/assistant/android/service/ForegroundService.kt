package com.assistant.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.assistant.android.ai.GeminiClient
import com.assistant.android.ai.PromptEngine
import com.assistant.android.ai.SmartGeminiOrchestrator
import com.assistant.android.automation.ActionExecutor
import com.assistant.android.automation.RoutineEngine
import com.assistant.android.automation.WhatsAppReplyHelper
import com.assistant.android.core.ApiKeyManager
import com.assistant.android.core.MasterController
import com.assistant.android.feature.Calculator
import com.assistant.android.intent.LocalIntentMatcher
import com.assistant.android.memory.AppDatabase
import com.assistant.android.memory.MemoryRepository
import com.assistant.android.memory.entity.History
import com.assistant.android.voice.SpeechRecognizerManager
import com.assistant.android.voice.TTSManager
import com.assistant.android.voice.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The brain of the assistant. Wires together STT -> [Local fast-path | Gemini orchestrator]
 * -> action -> TTS, plus routines and translation, and reports state through MasterController.
 *
 * QUOTA STRATEGY (v4.4 Nexus-Pro):
 *   1. Calculator on-device  (free, instant)
 *   2. LocalIntentMatcher   (free, instant)
 *   3. ResponseCache lookup (free)
 *   4. SmartGeminiOrchestrator (multi-key × multi-model fallback chain)
 */
class ForegroundService : Service(), RecognitionListener, TTSManager.OnInitListener {

    private var speechRecognizer: SpeechRecognizerManager? = null
    private lateinit var ttsManager: TTSManager
    private lateinit var orchestrator: SmartGeminiOrchestrator
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var routineEngine: RoutineEngine
    private lateinit var translationManager: TranslationManager
    private lateinit var memoryRepository: MemoryRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var ttsReady = false
    @Volatile private var pendingFirstSpeech: String? = null
    @Volatile private var sttSupported = false

    // STT error throttling — prevents the "code=8" log flood when the recognizer is busy.
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var consecutiveErrors = 0
    @Volatile private var lastErrorCode = -1
    @Volatile private var lastErrorLogMs = 0L
    private var pendingRestart: Runnable? = null
    @Volatile private var ttsSpeaking = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Jarvis active", "Ready for commands…"))

        sttSupported = SpeechRecognizer.isRecognitionAvailable(this)
        if (sttSupported) {
            speechRecognizer = SpeechRecognizerManager(this, this)
        } else {
            MasterController.recordError(
                "Voice recognition not available",
                "This device has no Speech Recognition engine installed. Install 'Speech Services by Google' " +
                "from the Play Store, then enable it under Settings → Apps → Default apps → Digital assistant. " +
                "Until then, use the chat box to type commands — Jarvis will still answer."
            )
        }

        ttsManager = TTSManager(this, this)
        // CRITICAL: mute the mic while we're speaking, so Jarvis doesn't transcribe its own voice.
        ttsManager.onSpeechStart = {
            ttsSpeaking = true
            speechRecognizer?.applyMute(true)
        }
        ttsManager.onSpeechDone = {
            ttsSpeaking = false
            // Small delay so the speaker tail doesn't trigger the mic.
            mainHandler.postDelayed({
                if (!ttsSpeaking) {
                    speechRecognizer?.applyMute(false)
                    if (MasterController.state.value != MasterController.State.IDLE) startListeningIfPossible()
                }
            }, 350)
        }
        val model = ApiKeyManager.getModel(this)
        orchestrator = SmartGeminiOrchestrator(this, model)
        actionExecutor = ActionExecutor(this)
        routineEngine = RoutineEngine(this)
        val primaryClient = GeminiClient(ApiKeyManager.getApiKey(this), model)
        translationManager = TranslationManager(primaryClient, ttsManager)
        memoryRepository = MemoryRepository(AppDatabase.getDatabase(this))
        val keyCount = ApiKeyManager.getAllKeys(this).size
        MasterController.recordLog("Assistant service started • model=$model • keys=$keyCount • STT=$sttSupported")

        scope.launch { validateKey() }
    }

    private suspend fun validateKey() {
        MasterController.recordLog("Validating Gemini key…")
        when (val result = orchestrator.ping()) {
            is GeminiClient.Result.Success -> {
                MasterController.recordLog("✓ Gemini key OK")
                val sttHint = if (sttSupported) "Say Hey Jarvis or tap to talk." else "Voice unavailable — type in the chat box."
                speakWhenReady("Jarvis is online. $sttHint")
            }
            is GeminiClient.Result.Failure -> {
                MasterController.recordError(result.short, result.detail)
                MasterController.transitionTo(MasterController.State.ERROR)
                val spoken = if (result.needsNewKey)
                    "Warning. ${result.short} Open Settings inside the app and paste a new key."
                else
                    "Warning. ${result.short}"
                speakWhenReady(spoken)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_WAKE -> {
                MasterController.recordLog("Woken by wake-word")
                speakWhenReady("Yes, I'm listening.")
                startListeningIfPossible()
            }
            ACTION_TEXT_COMMAND -> {
                val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
                if (text.isNotEmpty()) processCommand(text, MasterController.Source.TEXT)
            }
            else -> {
                MasterController.transitionTo(MasterController.State.LISTENING)
                startListeningIfPossible()
            }
        }
        return START_STICKY
    }

    private fun startListeningIfPossible() {
        if (!sttSupported) {
            MasterController.recordLog("STT unavailable — chat input is the only path")
            return
        }
        if (ttsSpeaking) return  // never open mic while we're talking
        speechRecognizer?.startListening()
    }

    /** Schedule a single restart with exponential back-off (cancels any earlier pending restart). */
    private fun scheduleRestart(delayMs: Long) {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable { startListeningIfPossible() }
        pendingRestart = r
        mainHandler.postDelayed(r, delayMs)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        speechRecognizer?.destroy()
        ttsManager.shutdown()
        scope.cancel()
        MasterController.transitionTo(MasterController.State.IDLE)
        MasterController.recordLog("Assistant service stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Jarvis Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.assistant.android.ui.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("Jarvis", text))
    }

    private fun speakWhenReady(text: String) {
        if (ttsReady) {
            MasterController.transitionTo(MasterController.State.SPEAKING)
            MasterController.recordReply(text)
            ttsManager.speak(text)
            MasterController.transitionTo(MasterController.State.LISTENING)
        } else {
            pendingFirstSpeech = text
        }
    }

    // ------- RecognitionListener -------
    override fun onResults(results: Bundle?) {
        consecutiveErrors = 0
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            processCommand(matches[0], MasterController.Source.VOICE)
        } else {
            MasterController.recordLog("No speech detected — restarting listener")
            scheduleRestart(500L)
        }
    }

    private fun processCommand(command: String, source: MasterController.Source) {
        Log.d(TAG, "Heard ($source): $command")
        MasterController.recordCommand(command, source)
        MasterController.transitionTo(MasterController.State.THINKING)
        updateNotification("Thinking: $command")

        scope.launch {
            try {
                // === FAST PATH 1: Pure-math on-device — zero API calls ===
                if (Calculator.looksLikeMath(command)) {
                    val ans = Calculator.evaluate(command)
                    if (ans != null) {
                        val reply = "$command equals $ans"
                        MasterController.recordUnderstood("calc (local)", reply)
                        speakAndContinue(reply)
                        return@launch
                    }
                }

                // === FAST PATH 2: Local intent regex match — saves Gemini quota ===
                LocalIntentMatcher.match(command)?.let { localJson ->
                    MasterController.recordLog("⚡ Local fast-path matched — no API call")
                    handleParsedJson(command, localJson, fromCache = false, fromLocal = true)
                    return@launch
                }

                // === Special: "reply on whatsapp" needs Gemini to compose, then we send ===
                val isWhatsAppReply = command.lowercase().let { c ->
                    c.contains("whatsapp") && (c.contains("reply") || c.contains("ans"))
                }

                val memorySnippet = runCatching {
                    memoryRepository.getAllHistory().takeLast(8).joinToString("\n") {
                        "U: ${it.command} | J: ${it.aiResponse}"
                    }
                }.getOrDefault("")

                val contextSnippet = if (isWhatsAppReply) {
                    val s = WhatsAppReplyHelper.lastMessageSummary()
                    if (s != null) "INCOMING_MESSAGE_TO_REPLY_TO: $s" else ""
                } else ""

                val prompt = PromptEngine.generatePrompt(command, memorySnippet, contextSnippet)
                val resp = orchestrator.generate(prompt, cacheable = !isWhatsAppReply)
                if (resp is GeminiClient.Result.Failure) {
                    MasterController.recordError(resp.short, resp.detail)
                    val spoken = if (resp.needsNewKey)
                        "Sorry, I cannot reach Gemini. ${resp.short} Open Settings to fix it."
                    else
                        "Sorry, I cannot reach Gemini right now. ${resp.short}"
                    speakAndContinue(spoken)
                    MasterController.transitionTo(MasterController.State.ERROR)
                    return@launch
                }
                val raw = (resp as GeminiClient.Result.Success).text
                val json = PromptEngine.parseGeminiResponse(raw)
                if (json == null) {
                    MasterController.recordLog("Free-text reply (no JSON)")
                    speakAndContinue(raw)
                    return@launch
                }
                handleParsedJson(command, json, fromCache = false, fromLocal = false)
            } catch (e: Exception) {
                Log.e(TAG, "processCommand crash: ${e.message}", e)
                MasterController.recordError("Pipeline crash: ${e.javaClass.simpleName}", e.stackTraceToString().take(1500))
                speakAndContinue("Something went wrong inside me. Check the debug console.")
                MasterController.transitionTo(MasterController.State.ERROR)
            }
        }
    }

    private suspend fun handleParsedJson(command: String, json: JSONObject, fromCache: Boolean, fromLocal: Boolean) {
        val intent = json.optString("intent")
        val reply = json.optString("reply").ifEmpty { "On it." }
        val target = json.optString("target")
        val message = json.optString("message")

        runCatching {
            memoryRepository.insertHistory(
                History(command = command, aiResponse = reply, timestamp = System.currentTimeMillis())
            )
        }

        val tag = if (fromLocal) " (local)" else if (fromCache) " (cache)" else ""
        MasterController.recordUnderstood(intent.ifEmpty { "chat" } + tag, reply)
        MasterController.transitionTo(MasterController.State.EXECUTING)

        when (intent) {
            "routine" -> {
                val steps = json.optJSONArray("routine_steps")
                if (steps != null && steps.length() > 0) {
                    routineEngine.runRoutine(steps)
                    MasterController.recordRoutine()
                }
            }
            "translate" -> {
                translationManager.translateAndSpeak(message, target)
                MasterController.recordTranslation()
            }
            else -> {
                actionExecutor.executeIntent(json)
            }
        }

        speakAndContinue(reply)
        json.optJSONObject("next_step")?.let { nxt ->
            MasterController.recordLog("Chained next_step: ${nxt.optString("intent")}")
            actionExecutor.executeIntent(nxt)
        }
    }

    private fun speakAndContinue(text: String) {
        MasterController.transitionTo(MasterController.State.SPEAKING)
        MasterController.recordReply(text)
        ttsManager.speak(text)
        MasterController.transitionTo(MasterController.State.LISTENING)
        // DO NOT call startListeningIfPossible() here — the TTS onDone callback (in onCreate) restarts
        // the mic AFTER speech finishes, otherwise we hear our own voice and create an infinite echo loop.
    }

    override fun onError(error: Int) {
        // Throttle the log spam — only print the same error code once per 2 s.
        val now = System.currentTimeMillis()
        if (error != lastErrorCode || (now - lastErrorLogMs) > 2000) {
            Log.d(TAG, "STT error code=$error (${describeSttError(error)})")
            MasterController.recordLog("STT: ${describeSttError(error)}")
            lastErrorLogMs = now
        }
        if (error == lastErrorCode) consecutiveErrors++ else consecutiveErrors = 1
        lastErrorCode = error

        if (ttsSpeaking) return  // ignore everything while we're speaking — TTS done callback restarts

        when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(800L)
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(400L)
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> scheduleRestart(3000L)
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_AUDIO -> {
                // Exponential back-off, capped — and after 5 in a row, stop spamming and surface the issue.
                if (consecutiveErrors >= 5) {
                    MasterController.recordError(
                        "Speech recognizer keeps failing (code=$error)",
                        "After $consecutiveErrors retries the on-device recognizer is still erroring out. " +
                        "Tap Voice Diagnostics to see what's wrong, or use the chat box to type commands."
                    )
                    consecutiveErrors = 0
                    scheduleRestart(15000L)
                } else {
                    val delay = (1000L * (1 shl (consecutiveErrors - 1).coerceAtMost(4))).coerceAtMost(8000L)
                    scheduleRestart(delay)
                }
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                MasterController.recordError(
                    "Microphone permission denied",
                    "Open phone Settings → Apps → Jarvis → Permissions and grant the Microphone permission."
                )
            }
            else -> scheduleRestart(1500L)
        }
    }

    private fun describeSttError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "no mic permission"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no speech matched"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech heard"
        else -> "unknown STT error $code"
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let { MasterController.setPartial(it) }
    }

    override fun onReadyForSpeech(params: Bundle?) { MasterController.recordLog("Mic open — ready for speech") }
    override fun onBeginningOfSpeech() { MasterController.recordLog("Speech started") }
    override fun onEndOfSpeech() { MasterController.recordLog("Speech ended — processing") }

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        MasterController.setAudioLevel(normalized)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onTTSInitialized(success: Boolean) {
        ttsReady = success
        if (!success) {
            MasterController.recordError("TTS init failed", "TextToSpeech engine could not initialise — speech will be silent. Make sure a TTS engine is installed.")
            return
        }
        pendingFirstSpeech?.let { speakWhenReady(it); pendingFirstSpeech = null }
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "JarvisChannel"
        const val ACTION_WAKE = "com.assistant.android.ACTION_WAKE"
        const val ACTION_TEXT_COMMAND = "com.assistant.android.ACTION_TEXT_COMMAND"
        const val EXTRA_TEXT = "extra_text"
    }
}
