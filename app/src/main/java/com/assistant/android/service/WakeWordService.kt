package com.assistant.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.content.ContextCompat
import com.assistant.android.voice.SpeechRecognizerManager

/**
 * NEW FEATURE #3 — Wake Word Detection ("Hey Nexus" / "Nexus" / "ok nexus").
 *
 * Lightweight always-on listener. When the wake phrase is detected in partial or final results,
 * it launches the main ForegroundService which takes over with full conversational mode.
 *
 * NOTE: This is a software wake-word using the system speech recognizer (auto-restart loop).
 * For production-grade always-on detection, swap this for Picovoice Porcupine; the trigger contract
 * (start ForegroundService.ACTION_WAKE) stays the same.
 */
class WakeWordService : Service(), RecognitionListener {

    private lateinit var recognizer: SpeechRecognizerManager
    private val channelId = "WakeWordChannel"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(2, buildNotification())
        recognizer = SpeechRecognizerManager(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WakeWordService listening for hot words")
        recognizer.startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        recognizer.destroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Wake Word", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Nexus is listening")
            .setContentText("Say 'Hey Nexus' to start")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun checkWakeWord(text: String?) {
        if (text.isNullOrBlank()) return
        val lower = text.lowercase()
        if (WAKE_WORDS.any { lower.contains(it) }) {
            Log.d(TAG, "Wake word detected in: $text")
            recognizer.cancel()
            val i = Intent(this, ForegroundService::class.java).apply {
                action = ForegroundService.ACTION_WAKE
            }
            ContextCompat.startForegroundService(this, i)
        }
    }

    // RecognitionListener
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.forEach { checkWakeWord(it) }
        recognizer.startListening()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let { checkWakeWord(it) }
    }

    override fun onError(error: Int) {
        // Quietly restart on any error so we keep listening.
        recognizer.startListening()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        private const val TAG = "WakeWordService"
        private val WAKE_WORDS = listOf("hey nexus", "ok nexus", "nexus", "hey assistant")
    }
}
