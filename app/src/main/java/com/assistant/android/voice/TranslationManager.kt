package com.assistant.android.voice

import android.util.Log
import com.assistant.android.ai.GeminiClient
import java.util.Locale

/**
 * NEW FEATURE #2 — Real-time Multi-language Translation.
 *
 * Uses Gemini for translation and TTSManager for speaking the result in the target language.
 */
class TranslationManager(
    private val gemini: GeminiClient,
    private val tts: TTSManager
) {

    /**
     * Translate `text` into `targetLang` (BCP-47 code or language name) and speak it aloud.
     * @return the translated string or null on failure.
     */
    suspend fun translateAndSpeak(text: String, targetLang: String): String? {
        val translated = gemini.translate(text, targetLang) ?: return null
        val locale = resolveLocale(targetLang)
        if (locale != null) tts.setLanguage(locale)
        tts.speak(translated)
        return translated
    }

    private fun resolveLocale(target: String): Locale? {
        val key = target.trim().lowercase()
        return LANG_MAP[key] ?: runCatching { Locale.forLanguageTag(target) }.getOrNull()?.takeIf { it.language.isNotEmpty() }
    }

    companion object {
        private const val TAG = "TranslationManager"
        private val LANG_MAP = mapOf(
            "english" to Locale.US,
            "en" to Locale.US,
            "bangla" to Locale("bn", "BD"),
            "bengali" to Locale("bn", "BD"),
            "bn" to Locale("bn", "BD"),
            "hindi" to Locale("hi", "IN"),
            "hi" to Locale("hi", "IN"),
            "arabic" to Locale("ar"),
            "ar" to Locale("ar"),
            "spanish" to Locale("es", "ES"),
            "es" to Locale("es", "ES"),
            "french" to Locale.FRENCH,
            "fr" to Locale.FRENCH,
            "german" to Locale.GERMAN,
            "de" to Locale.GERMAN,
            "japanese" to Locale.JAPANESE,
            "ja" to Locale.JAPANESE,
            "chinese" to Locale.CHINESE,
            "zh" to Locale.CHINESE
        )
    }
}
