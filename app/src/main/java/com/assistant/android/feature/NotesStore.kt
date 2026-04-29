package com.assistant.android.feature

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NEW FEATURE — Voice notes. Persists to SharedPreferences as a small JSON array so users can
 * "save note: doctor appointment kal 5tay" then "show my notes" and Jarvis reads them back.
 */
object NotesStore {

    private const val PREFS = "jarvis_notes"
    private const val KEY = "notes_json"
    private const val MAX_NOTES = 100

    fun add(ctx: Context, text: String) {
        if (text.isBlank()) return
        val arr = readArray(ctx)
        val obj = JSONObject().apply {
            put("text", text.trim())
            put("ts", System.currentTimeMillis())
        }
        // newest first
        val merged = JSONArray().apply {
            put(obj)
            for (i in 0 until arr.length()) {
                if (length() >= MAX_NOTES) break
                put(arr.get(i))
            }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, merged.toString()).apply()
    }

    fun all(ctx: Context): List<Pair<Long, String>> {
        val arr = readArray(ctx)
        val out = mutableListOf<Pair<Long, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += o.optLong("ts") to o.optString("text")
        }
        return out
    }

    fun spokenSummary(ctx: Context, max: Int = 5): String {
        val list = all(ctx)
        if (list.isEmpty()) return "You don't have any notes saved yet."
        val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        val top = list.take(max)
        val lines = top.mapIndexed { i, (ts, text) -> "${i + 1}. ${sdf.format(Date(ts))} — $text" }
        return "You have ${list.size} note${if (list.size == 1) "" else "s"}.\n" + lines.joinToString("\n")
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun readArray(ctx: Context): JSONArray {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }
}
