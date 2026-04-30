package com.jarvis.ceotitan.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.jarvis.ceotitan.JarvisApp
import com.jarvis.ceotitan.MainActivity
import com.jarvis.ceotitan.R
import com.jarvis.ceotitan.core.utils.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class JarvisListenerService : Service() {

    @Inject lateinit var settingsManager: SettingsManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false

    private val _commandFlow = MutableSharedFlow<String>()
    val commandFlow: SharedFlow<String> = _commandFlow.asSharedFlow()

    companion object {
        const val ACTION_START_LISTENING = "start_listening"
        const val ACTION_STOP_LISTENING = "stop_listening"
        const val ACTION_SPEAK = "speak"
        const val EXTRA_TEXT = "text"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        initTextToSpeech()
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return START_STICKY
                speak(text)
            }
        }
        return START_STICKY
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.apply {
                    language = Locale("bn", "BD")
                    setSpeechRate(1.0f)
                    setPitch(1.0f)
                }
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) {
                    isListening = false
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        serviceScope.launch { delay(1000); startListeningIfNeeded() }
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: return
                    serviceScope.launch { _commandFlow.emit(text) }
                    serviceScope.launch { delay(500); startListeningIfNeeded() }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
    }

    fun startListening() {
        if (isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    fun speak(text: String) {
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "utterance_${System.currentTimeMillis()}"
        )
    }

    private suspend fun startListeningIfNeeded() {
        if (settingsManager.getAlwaysListen() && !isListening) {
            delay(500)
            startListening()
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, JarvisApp.CHANNEL_LISTENING)
            .setContentTitle("JARVIS CEO TITAN")
            .setContentText("আপনার সহকারী প্রস্তুত")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        serviceScope.cancel()
    }
}
