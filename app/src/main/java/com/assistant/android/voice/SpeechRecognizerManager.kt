package com.assistant.android.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Speech recognizer wrapper. Hardened against the "ERROR_RECOGNIZER_BUSY" flood:
 * caller can call startListening() as many times as it wants, but actual restarts are
 * debounced (250 ms) and gated by a busy flag that is cleared in onResults / onError.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val listener: RecognitionListener
) {

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile var continuous: Boolean = true
    /** When true (e.g. while TTS is speaking), startListening() is a no-op so we don't hear our own voice. */
    @Volatile private var muted: Boolean = false
    private val busy = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastStartMs = 0L
    private var pendingRestart: Runnable? = null

    fun applyMute(value: Boolean) {
        muted = value
        if (value) {
            pendingRestart?.let { mainHandler.removeCallbacks(it) }
            try { speechRecognizer?.cancel() } catch (_: Exception) {}
            busy.set(false)
        }
    }

    init { initializeRecognizer() }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun initializeRecognizer() {
        if (!isAvailable()) {
            Log.e(TAG, "Speech recognition not available on this device"); return
        }
        if (Looper.myLooper() == Looper.getMainLooper()) doInit()
        else mainHandler.post { doInit() }
    }

    private fun doInit() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(WrappedListener(listener))
    }

    fun startListening(language: String? = null) {
        if (muted) return
        val now = System.currentTimeMillis()
        if (busy.get() && (now - lastStartMs) < 250) return
        if (!busy.compareAndSet(false, true)) {
            pendingRestart?.let { mainHandler.removeCallbacks(it) }
            val r = Runnable { busy.set(false); startListening(language) }
            pendingRestart = r
            mainHandler.postDelayed(r, 350)
            return
        }
        lastStartMs = now

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (language != null) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            } else {
                // Multi-language: Bengali primary (matches user's region), with English (US/IN) and Hindi as alternates.
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                    arrayListOf("bn-BD", "bn-IN", "en-IN", "en-US", "hi-IN"))
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                    arrayOf("bn-IN", "en-IN", "en-US", "hi-IN"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        val launch = Runnable {
            try {
                if (speechRecognizer == null && isAvailable()) initializeRecognizer()
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed: ${e.message}")
                busy.set(false)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) launch.run() else mainHandler.post(launch)
    }

    fun stopListening() { try { speechRecognizer?.stopListening() } catch (_: Exception) {} }

    fun cancel() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        busy.set(false)
    }

    fun destroy() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        busy.set(false)
    }

    private inner class WrappedListener(private val inner: RecognitionListener) : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) { inner.onReadyForSpeech(params) }
        override fun onBeginningOfSpeech() { inner.onBeginningOfSpeech() }
        override fun onRmsChanged(rmsdB: Float) { inner.onRmsChanged(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) { inner.onBufferReceived(buffer) }
        override fun onEndOfSpeech() { inner.onEndOfSpeech() }
        override fun onError(error: Int) { busy.set(false); inner.onError(error) }
        override fun onResults(results: android.os.Bundle?) { busy.set(false); inner.onResults(results) }
        override fun onPartialResults(partialResults: android.os.Bundle?) { inner.onPartialResults(partialResults) }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) { inner.onEvent(eventType, params) }
    }

    companion object { private const val TAG = "SpeechRecognizer" }
}
