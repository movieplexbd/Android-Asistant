package com.assistant.android.ai

import android.content.Context
import android.util.Log
import com.assistant.android.core.ApiKeyManager
import com.assistant.android.core.MasterController

/**
 * v4.4 quota-saving brain. Every request is routed in this order:
 *
 *   1. ResponseCache  — instant, free.
 *   2. (key, primary model) for each saved key.
 *   3. (key, fallback model) chain — gemini-2.0-flash → 1.5-flash → 2.5-flash-lite → 1.5-flash-8b
 *      so the caller stops being killed by the 20-req/day per-model free tier.
 *
 * A 60s cooldown is applied to a (key, model) pair if it returns 429 or 403, so we don't
 * keep hammering it.
 */
class SmartGeminiOrchestrator(
    private val context: Context,
    private val primaryModel: String
) {

    private val tag = "Orchestrator"
    private val fallbackChain = listOf(
        primaryModel,
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-1.5-flash-8b"
    ).distinct()

    /** key:"<keyMask>|<model>" → epoch millis until which we should NOT use this combo. */
    private val cooldown = HashMap<String, Long>()

    suspend fun ping(): GeminiClient.Result {
        return generate("Reply only with: OK", cacheable = false)
    }

    /**
     * Generate against the smart routing chain.
     * @param cacheable when false, skip cache lookup AND skip storing (use for whatsapp-reply etc).
     */
    suspend fun generate(prompt: String, cacheable: Boolean = true): GeminiClient.Result {
        if (cacheable) {
            ResponseCache.get(context, prompt)?.let {
                MasterController.recordLog("✓ Cache hit (saved 1 API call)")
                return GeminiClient.Result.Success(it)
            }
        }

        val keys = ApiKeyManager.getAllKeys(context)
        if (keys.isEmpty()) {
            return GeminiClient.Result.Failure(
                "No Gemini API key set. Open Settings and paste your free key.",
                "User has no API keys configured.",
                needsNewKey = true
            )
        }

        var lastFailure: GeminiClient.Result.Failure? = null
        val now = System.currentTimeMillis()

        for (key in keys) {
            for (model in fallbackChain) {
                val combo = "${ApiKeyManager.mask(key)}|$model"
                val cdUntil = cooldown[combo] ?: 0L
                if (cdUntil > now) {
                    Log.d(tag, "Skipping $combo — cooldown ${cdUntil - now}ms remaining")
                    continue
                }
                MasterController.recordLog("→ Trying $combo")
                val client = GeminiClient(key, model)
                when (val r = client.getGeminiResponseDetailed(prompt)) {
                    is GeminiClient.Result.Success -> {
                        if (cacheable) ResponseCache.put(context, prompt, r.text)
                        MasterController.recordLog("✓ $combo succeeded")
                        return r
                    }
                    is GeminiClient.Result.Failure -> {
                        lastFailure = r
                        // Apply cooldown for quota / auth failures so we stop slamming this combo.
                        if (r.httpCode == 429 || r.httpCode == 403 || r.httpCode == 401) {
                            cooldown[combo] = now + 60_000L
                            MasterController.recordLog("✗ $combo failed (${r.httpCode}) — 60s cooldown")
                        } else if (r.httpCode == 404) {
                            // model unsupported on this key — skip this model permanently for this run
                            cooldown[combo] = now + 24L * 60L * 60L * 1000L
                            MasterController.recordLog("✗ $combo not available for this key — skipping")
                        } else {
                            MasterController.recordLog("✗ $combo failed: ${r.short}")
                        }
                    }
                }
            }
        }
        return lastFailure ?: GeminiClient.Result.Failure(
            "All Gemini routes are exhausted.",
            "Tried ${keys.size} key(s) × ${fallbackChain.size} model(s) — all failed or cooled down."
        )
    }
}
