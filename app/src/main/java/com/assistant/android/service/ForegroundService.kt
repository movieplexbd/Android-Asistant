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
        startForeground(NOTIFICATION_ID, buildNotification("Assistant active", "Listening for commands…"))

        speechRecognizer = SpeechRecognizerManager(this, this)
        ttsManager = TTSManager(this, this)
        geminiClient = GeminiClient(BuildConfig.GEMINI_API_KEY)
        actionExecutor = ActionExecutor(this)
        routineEngine = RoutineEngine(this)
        translationManager = TranslationManager(geminiClient, ttsManager)
        memoryRepository = MemoryRepository(AppDatabase.getDatabase(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
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
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Nexus Assistant", NotificationManager.IMPORTANCE_LOW)
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
        nm.notify(NOTIFICATION_ID, buildNotification("Nexus", text))
    }

    // ------- RecognitionListener -------
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            processCommand(matches[0])
        } else {
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
                        "User: ${it.command} | NEXUS: ${it.aiResponse}"
                    }
                }.getOrDefault("")

                val prompt = PromptEngine.generatePrompt(command, memorySnippet)
                val raw = geminiClient.getGeminiResponse(prompt) ?: run {
                    speakAndContinue("Sorry, I couldn't reach my brain right now.")
                    MasterController.transitionTo(MasterController.State.ERROR)
                    return@launch
                }
                val json = PromptEngine.parseGeminiResponse(raw)
                if (json == null) {
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

                MasterController.recordReply(reply)
                MasterController.transitionTo(MasterController.State.EXECUTING)

                when (intent) {
                    "routine" -> {
                        val steps = json.optJSONArray("routine_steps")
                        if (steps != null) {
                            ttsManager.speak(reply)
                            routineEngine.runRoutine(steps)
                            MasterController.recordRoutine()
                        }
                    }
                    "translate" -> {
                        val translated = translationManager.translateAndSpeak(message, target)
                        if (translated != null) MasterController.recordTranslation()
                    }
                    else -> {
                        actionExecutor.executeIntent(json)
                        ttsManager.speak(reply)
                    }
                }

                // Optional follow-up step (e.g. after biometric_auth -> send_money)
                json.optJSONObject("next_step")?.let { routineEngine.runNextStep(it) }

            } catch (e: Exception) {
                Log.e(TAG, "processCommand crash", e)
                MasterController.transitionTo(MasterController.State.ERROR)
                speakAndContinue("Something went wrong. I'll try again.")
            } finally {
                MasterController.transitionTo(MasterController.State.LISTENING)
                updateNotification("Listening…")
                speechRecognizer.startListening()
            }
        }
    }

    private fun speakAndContinue(text: String) {
        ttsManager.speak(text)
    }

    override fun onError(error: Int) {
        Log.w(TAG, "STT error code=$error")
        speechRecognizer.startListening()
    }

    override fun onReadyForSpeech(params: Bundle?) { MasterController.transitionTo(MasterController.State.LISTENING) }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onTTSInitialized(success: Boolean) {
        Log.d(TAG, "TTS ready=$success")
    }

    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_CHANNEL_ID = "AssistantServiceChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_WAKE = "com.assistant.android.action.WAKE"
    }
}
