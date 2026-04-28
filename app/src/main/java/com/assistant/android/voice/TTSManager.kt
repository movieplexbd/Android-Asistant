package com.assistant.android.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager(private val context: Context, private val initListener: OnInitListener) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    interface OnInitListener {
        fun onTTSInitialized(success: Boolean)
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTSManager", "Language not supported")
                initListener.onTTSInitialized(false)
            } else {
                isInitialized = true
                initListener.onTTSInitialized(true)
            }
        } else {
            Log.e("TTSManager", "TTS Initialization failed")
            initListener.onTTSInitialized(false)
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        } else {
            Log.e("TTSManager", "TTS not initialized")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
