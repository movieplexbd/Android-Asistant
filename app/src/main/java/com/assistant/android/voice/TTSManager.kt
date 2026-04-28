package com.assistant.android.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager(context: Context, private val initListener: OnInitListener) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    interface OnInitListener {
        fun onTTSInitialized(success: Boolean)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Default language not supported, falling back to default")
            }
            isInitialized = true
            initListener.onTTSInitialized(true)
        } else {
            Log.e(TAG, "TTS initialization failed: $status")
            initListener.onTTSInitialized(false)
        }
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexus_${System.currentTimeMillis()}")
    }

    fun setLanguage(locale: Locale): Boolean {
        if (!isInitialized) return false
        val r = tts?.setLanguage(locale)
        return r != null && r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun setVoiceByName(voiceName: String) {
        if (!isInitialized) return
        val voices = tts?.voices ?: return
        voices.firstOrNull { it.name.contains(voiceName, ignoreCase = true) }?.let { tts?.voice = it }
    }

    fun getAvailableVoices(): List<String> = tts?.voices?.map { it.name } ?: emptyList()

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
