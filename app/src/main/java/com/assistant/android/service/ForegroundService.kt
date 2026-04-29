package com.assistant.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
        if (sttSupported) speechRecognizer?.startListening()
        else MasterController.recordLog("STT unavailable — chat input is the only path")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
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
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            processCommand(matches[0], MasterController.Source.VOICE)
        } else {
            MasterController.recordLog("No speech detected — restarting listener")
            startListeningIfPossible()
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
        startListeningIfPossible()
    }

    override fun onError(error: Int) {
        Log.d(TAG, "STT error code=$error")
        startListeningIfPossible()
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

    override fun onTtsInit(success: Boolean) {
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
