package com.assistant.android.automation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * NEW FEATURE #1 — Smart Routines.
 *
 * Executes a chain of intents (open WiFi, then play music, then read calendar) sequentially with
 * a small inter-step delay so the OS has time to settle UI state.
 */
class RoutineEngine(context: Context) {

    private val executor = ActionExecutor(context)
    private val tag = "RoutineEngine"

    suspend fun runRoutine(steps: JSONArray, onStep: ((Int, JSONObject) -> Unit)? = null): Int {
        var success = 0
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            Log.d(tag, "Routine step $i: $step")
            onStep?.invoke(i, step)
            if (executor.executeIntent(step)) success++
            delay(700) // give Android UI time to react before next step
        }
        return success
    }

    suspend fun runNextStep(next: JSONObject?): Boolean {
        next ?: return false
        Log.d(tag, "Chained next_step: $next")
        delay(400)
        return executor.executeIntent(next)
    }
}
