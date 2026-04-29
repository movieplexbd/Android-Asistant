package com.assistant.android.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Direct REST client for Gemini.  Avoids SDK abstraction so we can surface real HTTP errors
 * (404 model-not-found, 403 leaked-key, 429 quota, network issues) all the way to the UI.
 *
 * Returns sealed Result so the caller can show + speak detailed reasons.
 */
class GeminiClient(
    @Volatile var apiKey: String,
    @Volatile var modelName: String = "gemini-2.5-flash"
) {

    sealed class Result {
        data class Success(val text: String) : Result()
        data class Failure(
            val short: String,         // 1-line user-friendly reason
            val detail: String,        // full HTTP body / stacktrace for debug console
            val httpCode: Int? = null,
            val needsNewKey: Boolean = false
        ) : Result()
    }

    /** Quick health check — does this key+model actually work? */
    suspend fun ping(): Result =
        callGenerate(buildBody("Reply only with: OK"))

    suspend fun getGeminiResponseDetailed(prompt: String): Result =
        callGenerate(buildBody(prompt))

    /** Backward-compat: returns text on success, null on failure. */
    suspend fun getGeminiResponse(prompt: String): String? =
        when (val r = getGeminiResponseDetailed(prompt)) {
            is Result.Success -> r.text
            is Result.Failure -> { Log.e(TAG, "Gemini failure: ${r.short} | ${r.detail.take(400)}"); null }
        }

    suspend fun analyzeImage(prompt: String, bitmap: Bitmap): String? {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray()
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/jpeg").put("data", b64)))
                    .put(JSONObject().put("text", prompt)))
            }))
        }
        return when (val r = callGenerate(body)) { is Result.Success -> r.text; is Result.Failure -> null }
    }

    suspend fun translate(text: String, targetLanguage: String): String? {
        val prompt = "Translate the following text to $targetLanguage. Return ONLY the translated text.\n\n$text"
        return getGeminiResponse(prompt)?.trim()
    }

    /** List models endpoint — used to auto-discover a working model when the chosen one fails. */
    suspend fun listModels(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val conn = url.openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val code = conn.responseCode
            if (code !in 200..299) return emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("models") ?: return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                var supports = false
                if (methods != null) for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") supports = true
                if (supports) out.add(m.getString("name").removePrefix("models/"))
            }
            out
        } catch (e: Exception) { emptyList() }
        }
    }

    private fun buildBody(prompt: String): JSONObject = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        }))
        put("generationConfig", JSONObject().apply {
            put("temperature", 0.2)
            put("topK", 32)
            put("topP", 1.0)
            put("maxOutputTokens", 2048)
        })
    }

    private suspend fun callGenerate(body: JSONObject): Result {
        return withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return Result.Failure(
                short = "No Gemini API key set. Open Settings and paste your free key.",
                detail = "The app ships without an API key. Get a free key at https://aistudio.google.com/apikey then paste it in the Settings screen and tap Save.",
                needsNewKey = true
            )
        }
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 12000; conn.readTimeout = 25000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.readText() ?: ""
            if (code in 200..299) {
                val text = parseText(raw) ?: return Result.Failure(
                    "Empty response from Gemini.",
                    "HTTP 200 but no candidate text. Body: ${raw.take(800)}",
                    code
                )
                Result.Success(text)
            } else {
                val parsed = parseError(raw)
                val short = when (code) {
                    400 -> "Bad request to Gemini: ${parsed.first}"
                    401, 403 -> {
                        if (parsed.first.contains("leaked", true) || parsed.first.contains("disabled", true))
                            "API key blocked (reported as leaked). Open Settings and paste a fresh key."
                        else "API key rejected (403): ${parsed.first}"
                    }
                    404 -> "Model '$modelName' not available. Open Settings → Auto-detect model."
                    429 -> "Quota exceeded for free tier. Wait a few minutes or use a different key."
                    500, 502, 503 -> "Gemini server error ($code). Trying again may help."
                    else -> "Gemini HTTP $code: ${parsed.first}"
                }
                Result.Failure(
                    short = short,
                    detail = "HTTP $code  model=$modelName\n${parsed.second}",
                    httpCode = code,
                    needsNewKey = code == 401 || code == 403
                )
            }
        } catch (e: java.net.UnknownHostException) {
            Result.Failure("No internet connection.",
                "UnknownHostException: ${e.message}\nCheck Wi-Fi or mobile data.")
        } catch (e: java.net.SocketTimeoutException) {
            Result.Failure("Network timeout reaching Gemini.",
                "SocketTimeoutException: ${e.message}")
        } catch (e: Exception) {
            Result.Failure("Unexpected error: ${e.javaClass.simpleName}",
                "${e.javaClass.name}: ${e.message}\n${e.stackTraceToString().take(1500)}")
        }
        }
    }

    private fun parseText(raw: String): String? {
        return try {
            val candidates = JSONObject(raw).optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text"))
            }
            sb.toString().trim().ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseError(raw: String): Pair<String, String> = try {
        val err = JSONObject(raw).optJSONObject("error")
        val msg = err?.optString("message") ?: raw.take(300)
        msg to raw
    } catch (e: Exception) {
        raw.take(200) to raw
    }

    companion object {
        private const val TAG = "GeminiClient"
    }
}
