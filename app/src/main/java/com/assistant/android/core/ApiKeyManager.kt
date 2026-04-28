package com.assistant.android.core

import android.content.Context
import android.content.SharedPreferences
import com.assistant.android.BuildConfig

/**
 * Stores the user's Gemini API key + chosen model in SharedPreferences.
 *
 * Falls back to BuildConfig.GEMINI_API_KEY (compile-time) if the user hasn't entered one yet.
 * Falls back to a sensible default model if none chosen.
 */
object ApiKeyManager {
    private const val PREFS = "jarvis_prefs"
    private const val KEY_API = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model"
    private const val DEFAULT_MODEL = "gemini-2.5-flash"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getApiKey(ctx: Context): String {
        val saved = prefs(ctx).getString(KEY_API, null)
        if (!saved.isNullOrBlank()) return saved
        return BuildConfig.GEMINI_API_KEY
    }

    fun hasUserKey(ctx: Context): Boolean =
        !prefs(ctx).getString(KEY_API, null).isNullOrBlank()

    fun setApiKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString(KEY_API, key.trim()).apply()
    }

    fun clearApiKey(ctx: Context) {
        prefs(ctx).edit().remove(KEY_API).apply()
    }

    fun getModel(ctx: Context): String =
        prefs(ctx).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun setModel(ctx: Context, model: String) {
        prefs(ctx).edit().putString(KEY_MODEL, model).apply()
    }

    /** Mask a key for log display: shows first 6 + last 4 chars. */
    fun mask(key: String): String {
        if (key.length < 14) return "***"
        return "${key.take(6)}...${key.takeLast(4)}"
    }
}
