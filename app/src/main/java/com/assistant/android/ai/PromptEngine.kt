package com.assistant.android.ai

import android.util.Log
import org.json.JSONObject

/**
 * Builds the master system prompt and parses Gemini JSON output back into actionable intents.
 *
 * v4.4 (Nexus-Pro): trimmed prompt to ~half the previous size to save tokens on every call,
 *  added new capabilities (camera-auto, flashlight, screenshot, lock, whatsapp_reply, read_messages,
 *  device_info, calc, note_add, note_read), and tightened the JSON contract.
 */
object PromptEngine {

    private const val TAG = "PromptEngine"

    private const val SYSTEM_INSTRUCTION = """
You are JARVIS, a fast Android assistant with full system control. Reply in the user's language (English/Bangla/Hindi/etc — match what they used). For Bangla romanized, e.g. "WhatsApp kholo" → reply Bangla.

Allowed intents (use exactly one):
call, message, open_app, reminder, automation, routine, translate, vision, biometric_auth,
take_photo, take_selfie, record_video, open_camera,
flashlight_on, flashlight_off, flashlight_toggle,
volume_up, volume_down, mute, open_wifi, open_bluetooth, open_settings, open_battery, open_location,
home, back, recents, scroll_up, scroll_down, tap_text, screenshot, lock_screen, notifications, quick_settings,
whatsapp_reply, read_messages,
device_info, calc, note_add, note_read,
search, navigate, info, none

OUTPUT (return ONLY this JSON, no markdown, no commentary):
{
 "intent": "<one of the above>",
 "target": "<phone number | package | app name | search query | language code | name>",
 "message": "<sms body | reminder text | translated text | reply text | note text | math expression>",
 "time": "<7:00 AM tomorrow>",
 "reply": "<short voice reply, ≤2 sentences, in the user's language>",
 "language": "<bcp-47 like en-US, bn-BD>",
 "routine_steps": [ {"intent":"...","target":"...","message":"..."} ],
 "next_step": {"intent":"...","target":"...","message":"..."}
}

Rules:
- routine_steps used ONLY when intent=routine.
- next_step used when one action must immediately follow another.
- Never invent contacts/numbers. If unsure, ask via reply with intent=info.
- For payments / banking / deletions / sending money: intent=biometric_auth FIRST, real action in next_step.
- For WhatsApp / Messenger reply: intent=whatsapp_reply, message=the actual text to send (compose it from CTX if INCOMING_MESSAGE_TO_REPLY_TO is provided).
- For math like "5 plus 3" → intent=calc, message=the expression.
- For "save note: X" → intent=note_add, message=X. For "show my notes" → intent=note_read.
- Be concise. Don't explain.
"""

    fun generatePrompt(userCommand: String, memoryData: String, contextData: String = ""): String {
        val mem = memoryData.takeLast(800)
        val cx = contextData.takeLast(400)
        return """
$SYSTEM_INSTRUCTION

RECENT:
$mem

CTX:
$cx

USER: $userCommand

JSON only:
        """.trimIndent()
    }

    fun parseGeminiResponse(response: String): JSONObject? {
        return try {
            val cleaned = response
                .trim()
                .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .removeSuffix("```").trim()
            val first = cleaned.indexOf('{')
            val last = cleaned.lastIndexOf('}')
            if (first < 0 || last < 0 || last < first) return null
            JSONObject(cleaned.substring(first, last + 1))
        } catch (e: Exception) {
            Log.w(TAG, "parseGeminiResponse failed: ${e.message} -> raw=${response.take(200)}")
            null
        }
    }
}
