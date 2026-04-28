package com.assistant.android.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Speech recognizer wrapper. Supports continuous listening mode (auto-restart) and partial results.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val listener: RecognitionListener
) {

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile var continuous: Boolean = true

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(listener)
        } else {
            Log.e(TAG, "Speech recognition not available on this device")
        }
    }

    fun startListening(language: String? = null) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed: ${e.message}")
        }
    }

    fun stopListening() {
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
    }

    fun cancel() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
    }

    fun destroy() {
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
    }

    companion object {
        private const val TAG = "SpeechRecognizer"
    }
}
