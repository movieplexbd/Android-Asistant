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
import com.assistant.android.BuildConfig
import com.assistant.android.R
import com.assistant.android.ai.GeminiClient
import com.assistant.android.ai.PromptEngine
import com.assistant.android.automation.ActionExecutor
import com.assistant.android.automation.RoutineEngine
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
import org.json.JSONObject

/**
 * The brain of the assistant. Wires together STT -> Gemini -> action -> TTS, plus routines and
 * translation, and reports state through MasterController.
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Jarvis active", "Listening for commands…"))

        speechRecognizer = SpeechRecognizerManager(this, this)
        ttsManager = TTSManager(this, this)
        geminiClient = GeminiClient(BuildConfig.GEMINI_API_KEY)
        actionExecutor = ActionExecutor(this)
        routineEngine = RoutineEngine(this)
        translationManager = TranslationManager(geminiClient, ttsManager)
        memoryRepository = MemoryRepository(AppDatabase.getDatabase(this))
        MasterController.recordLog("Assistant service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_WAKE) {
            MasterController.recordLog("Woken by wake-word")
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
                val raw = geminiClient.getGeminiResponse(prompt) ?: run {
                    MasterController.recordLog("Gemini returned no response")
                    speakAndContinue("Sorry, I couldn't reach my brain right now.")
                    MasterController.transitionTo(MasterController.State.ERROR)
                    return@launch
                }
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
                            MasterController.recordAction("Running routine with ${steps.length()} step(s)")
                            ttsManager.speak(reply)
                            routineEngine.runRoutine(steps)
                            MasterController.recordRoutine()
                        }
                    }
                    "translate" -> {
                        MasterController.recordAction("Translating to $target: \"$message\"")
                        val translated = translationManager.translateAndSpeak(message, target)
                        if (translated != null) MasterController.recordTranslation()
                    }
                    else -> {
                        MasterController.recordAction(describeIntent(intent, json))
                        actionExecutor.executeIntent(json)
                        ttsManager.speak(reply)
                    }
                }

                // Optional follow-up step (e.g. after biometric_auth -> send_money)
                json.optJSONObject("next_step")?.let {
                    MasterController.recordAction("Follow-up: ${it.optString("intent", "next")}")
                    routineEngine.runNextStep(it)
                }

            } catch (e: Exception) {
                Log.e(TAG, "processCommand crash", e)
                MasterController.recordLog("ERROR: ${e.message ?: e.javaClass.simpleName}")
                MasterController.transitionTo(MasterController.State.ERROR)
                speakAndContinue("Something went wrong. I'll try again.")
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
        ttsManager.speak(text)
    }

    override fun onError(error: Int) {
        Log.w(TAG, "STT error code=$error")
        val reason = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "no match"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
            SpeechRecognizer.ERROR_AUDIO -> "audio error"
            SpeechRecognizer.ERROR_CLIENT -> "client error"
            SpeechRecognizer.ERROR_NETWORK -> "network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "missing mic permission"
            else -> "code $error"
        }
        MasterController.recordLog("STT: $reason — restarting")
        speechRecognizer.startListening()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        MasterController.transitionTo(MasterController.State.LISTENING)
        MasterController.recordLog("Mic open — ready for speech")
    }
    override fun onBeginningOfSpeech() { MasterController.recordLog("Speech started") }
    override fun onRmsChanged(rmsdB: Float) {
        // RMS dB roughly ranges -2..10; normalize to 0..1
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
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_ID = "AssistantServiceChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_WAKE = "com.assistant.android.action.WAKE"
    }
}
