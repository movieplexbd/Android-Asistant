package com.assistant.android.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
                Log.e("TTSManager", "Language not supported")
                initListener.onTTSInitialized(false)
            } else {
                isInitialized = true
                initListener.onTTSInitialized(true)
            }
        } else {
            Log.e("TTSManager", "Initialization failed")
            initListener.onTTSInitialized(false)
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun setVoice(voiceName: String) {
        if (isInitialized) {
            val voices = tts?.voices
            val selectedVoice = voices?.find { it.name.contains(voiceName, ignoreCase = true) }
            selectedVoice?.let {
                tts?.voice = it
            }
        }
    }

    fun getAvailableVoices(): List<String> {
        return tts?.voices?.map { it.name } ?: emptyList()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
