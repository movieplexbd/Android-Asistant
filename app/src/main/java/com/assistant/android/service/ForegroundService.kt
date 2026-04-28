package com.assistant.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.assistant.android.R
import com.assistant.android.ai.GeminiClient
import com.assistant.android.ai.PromptEngine
import com.assistant.android.automation.ActionExecutor
import com.assistant.android.memory.AppDatabase
import com.assistant.android.memory.MemoryRepository
import com.assistant.android.voice.SpeechRecognizerManager
import com.assistant.android.voice.TTSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class ForegroundService : Service(), RecognitionListener, TTSManager.OnInitListener {

    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private lateinit var ttsManager: TTSManager
    private lateinit var geminiClient: GeminiClient
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var memoryRepository: MemoryRepository

    private val NOTIFICATION_CHANNEL_ID = "AssistantServiceChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, getNotification())

        speechRecognizerManager = SpeechRecognizerManager(this, this)
        ttsManager = TTSManager(this, this)
        geminiClient = GeminiClient(getString(R.string.gemini_api_key)) // API Key from resources
        actionExecutor = ActionExecutor(this)
        memoryRepository = MemoryRepository(AppDatabase.getDatabase(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ForegroundService", "ForegroundService started")
        // Start listening for commands after wake word detection
        speechRecognizerManager.startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizerManager.destroy()
        ttsManager.shutdown()
        Log.d("ForegroundService", "ForegroundService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Assistant Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getNotification(): Notification {
        val notificationIntent = Intent(this, Class.forName("com.assistant.android.ui.MainActivity")) // Assuming MainActivity is the entry point
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AI Assistant Running")
            .setContentText("Listening for commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app icon
            .setContentIntent(pendingIntent)
            .build()
    }

    // RecognitionListener implementations
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d("ForegroundService", "onReadyForSpeech")
    }

    override fun onBeginningOfSpeech() {
        Log.d("ForegroundService", "onBeginningOfSpeech")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Log.d("ForegroundService", "onRmsChanged: $rmsdB")
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        Log.d("ForegroundService", "onBufferReceived")
    }

    override fun onEndOfSpeech() {
        Log.d("ForegroundService", "onEndOfSpeech")
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Other client side errors"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network related errors"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network operation timed out"
            SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "SpeechRecognizer service is busy"
            SpeechRecognizer.ERROR_SERVER -> "Server sends error status"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown speech recognition error"
        }
        Log.e("ForegroundService", "Speech recognition error: $errorMessage")
        // Restart listening after an error
        speechRecognizerManager.startListening()
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0]
            Log.d("ForegroundService", "Speech recognized: $spokenText")
            processCommand(spokenText)
        }
        // Restart listening after processing results
        speechRecognizerManager.startListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val partialText = matches[0]
            Log.d("ForegroundService", "Partial speech recognized: $partialText")
            // You can update UI with partial results if needed
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        Log.d("ForegroundService", "onEvent: $eventType")
    }

    // TTSManager.OnInitListener implementation
    override fun onTTSInitialized(success: Boolean) {
        if (success) {
            Log.d("ForegroundService", "TTS initialized successfully")
        } else {
            Log.e("ForegroundService", "TTS initialization failed")
        }
    }

    private fun processCommand(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Retrieve memory data (example: last 5 history items)
            val recentHistory = memoryRepository.getAllHistory().takeLast(5).joinToString("\n") { it.command + ": " + it.aiResponse }
            val prompt = PromptEngine.generatePrompt(command, recentHistory)
            val geminiResponse = geminiClient.getGeminiResponse(prompt)

            geminiResponse?.let {
                val jsonResponse = PromptEngine.parseGeminiResponse(it)
                jsonResponse?.let {
                    val intent = it.optString("intent")
                    val target = it.optString("target")
                    val message = it.optString("message")
                    val time = it.optString("time")
                    val reply = it.optString("reply")

                    // Store history
                    memoryRepository.insertHistory(History(command = command, aiResponse = it.toString(), timestamp = System.currentTimeMillis()))

                    if (actionExecutor.executeAction(intent, target, message, time)) {
                        ttsManager.speak(reply.ifEmpty { "Action completed." })
                    } else {
                        ttsManager.speak(reply.ifEmpty { "I couldn't perform that action." })
                    }
                } ?: run {
                    Log.e("ForegroundService", "Failed to parse Gemini JSON response: $it")
                    ttsManager.speak("I'm sorry, I couldn't understand that. Please try again.")
                }
            } ?: run {
                Log.e("ForegroundService", "Gemini response was null")
                ttsManager.speak("I'm having trouble connecting to my brain. Please check your internet connection.")
            }
        }
    }
}
