package com.assistant.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.assistant.android.R
import com.assistant.android.ai.GeminiClient
import com.assistant.android.ai.PromptEngine
import com.assistant.android.automation.ActionExecutor
import com.assistant.android.automation.RoutineEngine
import com.assistant.android.core.ApiKeyManager
import com.assistant.android.core.MasterController
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
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The brain of the assistant. Wires together STT -> Gemini -> action -> TTS, plus routines and
 * translation, and reports state through MasterController. Speaks narration on every step.
 */
class ForegroundService : Service(), RecognitionListener, TTSManager.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizerManager
    private lateinit var ttsManager: TTSManager
    private lateinit var geminiClient: GeminiClient
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var routineEngine: RoutineEngine
    private lateinit var translationManager: TranslationManager
    private lateinit var memoryRepository: MemoryRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var ttsReady = false
    @Volatile private var pendingFirstSpeech: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Jarvis active", "Listening for commands…"))

        speechRecognizer = SpeechRecognizerManager(this, this)
        ttsManager = TTSManager(this, this)
        val key = ApiKeyManager.getApiKey(this)
        val model = ApiKeyManager.getModel(this)
        geminiClient = GeminiClient(key, model)
        actionExecutor = ActionExecutor(this)
        routineEngine = RoutineEngine(this)
        translationManager = TranslationManager(geminiClient, ttsManager)
        memoryRepository = MemoryRepository(AppDatabase.getDatabase(this))
        MasterController.recordLog("Assistant service started • model=$model • key=${ApiKeyManager.mask(key)}")

        // Validate the API key in the background so the user is told immediately if it's broken.
        scope.launch { validateKey() }
    }

    private suspend fun validateKey() {
        MasterController.recordLog("Validating Gemini key...")
        val result = geminiClient.ping()
        when (result) {
            is GeminiClient.Result.Success -> {
                MasterController.recordLog("✓ Gemini key OK (model=${geminiClient.modelName})")
                speakWhenReady("Jarvis is online and ready. Say Hey Jarvis or tap to talk.")
            }
            is GeminiClient.Result.Failure -> {
                MasterController.recordError(result.short, result.detail)
                MasterController.transitionTo(MasterController.State.ERROR)
                val spoken = if (result.needsNewKey) {
                    "Warning. ${result.short} Open Settings inside the app and paste a new key."
                } else {
                    "Warning. ${result.short}"
                }
                speakWhenReady(spoken)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_WAKE) {
            MasterController.recordLog("Woken by wake-word")
            speakWhenReady("Yes, I'm listening.")
        }
        MasterController.transitionTo(MasterController.State.LISTENING)
        speechRecognizer.startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
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
            processCommand(matches[0])
        } else {
            MasterController.recordLog("No speech detected — restarting listener")
            speechRecognizer.startListening()
        }
    }

    private fun processCommand(command: String) {
        Log.d(TAG, "Heard: $command")
        MasterController.recordCommand(command)
        MasterController.transitionTo(MasterController.State.THINKING)
        updateNotification("Thinking: $command")

        scope.launch {
            try {
                val memorySnippet = runCatching {
                    memoryRepository.getAllHistory().takeLast(10).joinToString("\n") {
                        "User: ${it.command} | JARVIS: ${it.aiResponse}"
                    }
                }.getOrDefault("")

                val prompt = PromptEngine.generatePrompt(command, memorySnippet)
                val resp = geminiClient.getGeminiResponseDetailed(prompt)
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
                    MasterController.recordReply(raw)
                    speakAndContinue(raw)
                    return@launch
                }

                val intent = json.optString("intent")
                val reply = json.optString("reply").ifEmpty { "On it." }
                val target = json.optString("target")
                val message = json.optString("message")

                runCatching {
                    memoryRepository.insertHistory(
                        History(command = command, aiResponse = reply, timestamp = System.currentTimeMillis())
                    )
                }

                MasterController.recordUnderstood(intent.ifEmpty { "chat" }, reply)
                MasterController.recordReply(reply)
                MasterController.transitionTo(MasterController.State.EXECUTING)

                when (intent) {
                    "routine" -> {
                        val steps = json.optJSONArray("routine_steps")
                        if (steps != null) {
                            val desc = "Running routine with ${steps.length()} step(s)"
                            MasterController.recordAction(desc)
                            ttsManager.speak("$reply Running ${steps.length()} steps now.")
                            routineEngine.runRoutine(steps) { idx, step ->
                                val stepIntent = step.optString("intent")
                                MasterController.recordAction("Step ${idx + 1}/${steps.length()}: $stepIntent")
                            }
                            MasterController.recordRoutine()
                        }
                    }
                    "translate" -> {
                        val desc = "Translating to $target: \"$message\""
                        MasterController.recordAction(desc)
                        ttsManager.speak("Translating to $target.")
                        val translated = translationManager.translateAndSpeak(message, target)
                        if (translated != null) MasterController.recordTranslation()
                        else MasterController.recordError("Translation failed",
                            "Gemini returned null for translation of '$message' to '$target'.")
                    }
                    else -> {
                        val desc = describeIntent(intent, json)
                        MasterController.recordAction(desc)
                        ttsManager.speak(reply)
                        val ok = actionExecutor.executeIntent(json)
                        if (!ok) {
                            val why = "Action '$intent' could not run. Reason: required permission missing, target not found, or intent unsupported on this device."
                            MasterController.recordError("Action failed: $intent", why)
                            ttsManager.speak("Sorry, I could not complete that action. $why")
                        }
                    }
                }

                json.optJSONObject("next_step")?.let {
                    val nextDesc = "Follow-up: ${it.optString("intent", "next")}"
                    MasterController.recordAction(nextDesc)
                    ttsManager.speak("Now $nextDesc")
                    routineEngine.runNextStep(it)
                }

            } catch (e: Exception) {
                Log.e(TAG, "processCommand crash", e)
                val detail = "${e.javaClass.name}: ${e.message}\n${e.stackTraceToString().take(2000)}"
                MasterController.recordError("Unexpected crash: ${e.javaClass.simpleName}", detail)
                MasterController.transitionTo(MasterController.State.ERROR)
                speakAndContinue("Something went wrong. ${e.javaClass.simpleName}. ${e.message ?: "no details"}.")
            } finally {
                MasterController.transitionTo(MasterController.State.LISTENING)
                updateNotification("Listening…")
                speechRecognizer.startListening()
            }
        }
    }

    private fun describeIntent(intent: String, json: JSONObject): String = when (intent) {
        "open_app" -> "Opening app: ${json.optString("target")}"
        "call" -> "Calling: ${json.optString("target")}"
        "sms", "send_sms" -> "Sending SMS to ${json.optString("target")}: \"${json.optString("message")}\""
        "alarm", "set_alarm" -> "Setting alarm: ${json.optString("target")}"
        "reminder" -> "Setting reminder: ${json.optString("message")}"
        "volume_up" -> "Volume up"
        "volume_down" -> "Volume down"
        "volume_set" -> "Setting volume to ${json.optString("target")}"
        "wifi_on", "wifi_off", "bluetooth_on", "bluetooth_off" -> "Toggling ${intent.replace('_',' ')}"
        "settings", "open_settings" -> "Opening settings panel"
        "search", "web_search" -> "Searching: ${json.optString("target")}"
        "navigate" -> "Navigating to: ${json.optString("target")}"
        "camera" -> "Opening camera"
        "screenshot" -> "Taking screenshot"
        "home" -> "Going to home screen"
        "back" -> "Pressing back"
        "scroll_up", "scroll_down" -> "Scrolling ${intent.removePrefix("scroll_")}"
        "tap_text" -> "Tapping on: \"${json.optString("target")}\""
        "" , "chat", "answer" -> "Answering"
        else -> "Executing: $intent"
    }

    private fun speakAndContinue(text: String) {
        MasterController.recordReply(text)
        ttsManager.speak(text)
    }

    override fun onError(error: Int) {
        Log.w(TAG, "STT error code=$error")
        val (short, full) = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched" to "STT could not understand the audio. Try speaking louder or closer to the mic."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout" to "No speech was detected within the listening window. Restarting."
            SpeechRecognizer.ERROR_AUDIO -> "Audio error" to "STT audio recording failed. The mic may be in use by another app."
            SpeechRecognizer.ERROR_CLIENT -> "STT client error" to "Internal speech recognizer client error. Restarting."
            SpeechRecognizer.ERROR_NETWORK -> "Network error" to "STT requires internet but the network failed. Check Wi-Fi or data."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout" to "STT network request timed out."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy" to "Speech recognizer is busy with another request."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission missing" to "RECORD_AUDIO permission is not granted. Tap the app icon and grant microphone access."
            else -> "STT error code $error" to "Unknown speech recognizer error code $error."
        }
        MasterController.recordLog("STT: $short")
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
            error == SpeechRecognizer.ERROR_AUDIO) {
            MasterController.recordError(short, full)
            ttsManager.speak(full)
        }
        speechRecognizer.startListening()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        MasterController.transitionTo(MasterController.State.LISTENING)
        MasterController.recordLog("Mic open — ready for speech")
    }
    override fun onBeginningOfSpeech() { MasterController.recordLog("Speech started") }
    override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        MasterController.setAudioLevel(normalized)
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        MasterController.recordLog("Speech ended — processing")
        MasterController.setAudioLevel(0f)
    }
    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let { MasterController.setPartial(it) }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onTTSInitialized(success: Boolean) {
        Log.d(TAG, "TTS ready=$success")
        MasterController.recordLog("TTS engine ready=$success")
        ttsReady = success
        pendingFirstSpeech?.let {
            ttsManager.speak(it)
            MasterController.recordReply(it)
            pendingFirstSpeech = null
        }
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_ID = "AssistantServiceChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_WAKE = "com.assistant.android.action.WAKE"
    }
}
