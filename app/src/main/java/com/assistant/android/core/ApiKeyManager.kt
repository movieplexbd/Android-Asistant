package com.assistant.android.core

import android.content.Context
import android.content.SharedPreferences
import com.assistant.android.BuildConfig

/**
 * Stores the user's Gemini API key(s) + chosen model in SharedPreferences.
 *
 * v4.4: supports MULTIPLE keys (newline-separated) so the orchestrator can rotate when
 * one key hits its 20-req/day free quota. getApiKey() returns the first one for back-compat.
 *
 * Falls back to BuildConfig.GEMINI_API_KEY (compile-time) if the user hasn't entered one yet.
 */
object ApiKeyManager {
    private const val PREFS = "jarvis_prefs"
    private const val KEY_API = "gemini_api_key"        // multi-line list (one key per line)
    private const val KEY_MODEL = "gemini_model"
    private const val DEFAULT_MODEL = "gemini-2.5-flash"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** First saved key, or BuildConfig fallback. */
    fun getApiKey(ctx: Context): String {
        val all = getAllKeys(ctx)
        if (all.isNotEmpty()) return all.first()
        return BuildConfig.GEMINI_API_KEY
    }

    /** All saved keys, in priority order. Empty if none. */
    fun getAllKeys(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_API, null)?.trim().orEmpty()
        if (raw.isBlank()) {
            val cc = BuildConfig.GEMINI_API_KEY
            return if (cc.isBlank()) emptyList() else listOf(cc)
        }
        return raw.split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun hasUserKey(ctx: Context): Boolean =
        !prefs(ctx).getString(KEY_API, null).isNullOrBlank()

    /** Single-key setter — kept for back-compat with any old callers. */
    fun setApiKey(ctx: Context, key: String) = setApiKeys(ctx, key)

    /** Multi-line / comma / semicolon separated keys, all stored as newline-separated. */
    fun setApiKeys(ctx: Context, raw: String) {
        val cleaned = raw.split("\n", ",", ";")
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
            .joinToString("\n")
        prefs(ctx).edit().putString(KEY_API, cleaned).apply()
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
