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
import com.assistant.android.memory.entity.History
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
        geminiClient = GeminiClient(getString(R.string.gemini_api_key))
        actionExecutor = ActionExecutor(this)
        memoryRepository = MemoryRepository(AppDatabase.getDatabase(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        speechRecognizerManager.startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizerManager.destroy()
        ttsManager.shutdown()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Assistant Pro Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getNotification(): Notification {
        val notificationIntent = Intent(this, Class.forName("com.assistant.android.ui.MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Assistant Pro Active")
            .setContentText("Learning and helping in the background...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            processCommand(matches[0])
        }
        speechRecognizerManager.startListening()
    }

    private fun processCommand(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val memoryData = memoryRepository.getAllHistory().takeLast(10).joinToString("\n") { 
                "User: ${it.command} | AI: ${it.aiResponse}" 
            }
            val prompt = PromptEngine.generatePrompt(command, memoryData)
            val geminiResponse = geminiClient.getGeminiResponse(prompt)

            geminiResponse?.let {
                val jsonResponse = PromptEngine.parseGeminiResponse(it)
                jsonResponse?.let { json ->
                    val intent = json.optString("intent")
                    val target = json.optString("target")
                    val message = json.optString("message")
                    val time = json.optString("time")
                    val reply = json.optString("reply")
                    val proactive = json.optString("proactive_suggestion")

                    memoryRepository.insertHistory(History(command = command, aiResponse = reply, timestamp = System.currentTimeMillis()))

                    if (actionExecutor.executeAction(intent, target, message, time)) {
                        ttsManager.speak(reply)
                        if (proactive.isNotEmpty()) {
                            Log.d("Proactive", "Suggesting: $proactive")
                            // Logic to show proactive suggestion on UI
                        }
                    } else {
                        ttsManager.speak(reply.ifEmpty { "I'm on it." })
                    }
                } ?: run {
                    ttsManager.speak(it) // Fallback to raw text if not JSON
                }
            }
        }
    }

    // Other RecognitionListener stubs...
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) { speechRecognizerManager.startListening() }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onTTSInitialized(success: Boolean) {}
}
