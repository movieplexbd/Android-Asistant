package com.assistant.android.ai

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Disk-backed LRU cache for Gemini answers. Each entry has a 24h TTL.
 *
 * Keyed by SHA-256 of the prompt text (after a small normalisation), so the same question
 * asked again costs zero quota.
 */
object ResponseCache {

    private const val TAG = "ResponseCache"
    private const val DIR = "gemini_cache"
    private const val MAX_ENTRIES = 200
    private const val TTL_MS = 24L * 60L * 60L * 1000L // 24 hours

    private fun cacheDir(ctx: Context): File =
        File(ctx.cacheDir, DIR).apply { if (!exists()) mkdirs() }

    fun get(ctx: Context, prompt: String): String? {
        return try {
            val f = File(cacheDir(ctx), keyOf(prompt))
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > TTL_MS) {
                f.delete(); return null
            }
            val raw = f.readText()
            // Touch so LRU ordering reflects last read
            f.setLastModified(System.currentTimeMillis())
            JSONObject(raw).optString("text").ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "get failed: ${e.message}"); null
        }
    }

    fun put(ctx: Context, prompt: String, text: String) {
        try {
            val dir = cacheDir(ctx)
            val obj = JSONObject().apply { put("text", text); put("ts", System.currentTimeMillis()) }
            File(dir, keyOf(prompt)).writeText(obj.toString())
            evictIfNeeded(dir)
        } catch (e: Exception) {
            Log.w(TAG, "put failed: ${e.message}")
        }
    }

    fun clear(ctx: Context) {
        try {
            cacheDir(ctx).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    fun stats(ctx: Context): String {
        val files = cacheDir(ctx).listFiles() ?: return "empty"
        val total = files.sumOf { it.length() }
        return "${files.size} entries / ${total / 1024} KB"
    }

    private fun evictIfNeeded(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_ENTRIES)
            .forEach { it.delete() }
    }

    private fun keyOf(prompt: String): String {
        val normalized = prompt.trim().lowercase().replace(Regex("\\s+"), " ")
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
