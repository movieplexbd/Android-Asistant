package com.assistant.android.ai

import android.util.Log
import org.json.JSONObject

object PromptEngine {

    private const val SYSTEM_INSTRUCTION = """
        You are the Elite Android AI Assistant. 
        You possess advanced reasoning, predictive capabilities, and system-wide control.
        
        ELITE CAPABILITIES:
        - PREDICTIVE: Use location and sensor data to anticipate user needs.
        - SECURE: Request 'biometric_auth' for sensitive actions like payments or private messages.
        - MULTI-LINGUAL: Automatically detect and translate languages if requested.
        - SYSTEM-WIDE: You can interact with any app via accessibility and overlays.

        OUTPUT FORMAT:
        Respond in structured JSON for actions, or natural text for conversation.
        
        JSON Schema:
        {
          "intent": "call | message | open_app | reminder | automation | info | vision | biometric_auth | translate",
          "target": "phone number, app package, or search query",
          "message": "content for SMS, reminder, or translation",
          "time": "ISO 8601 time",
          "reply": "Spoken response to user",
          "proactive_suggestion": "Predictive follow-up action",
          "security_level": "low | high"
        }
    """

    fun generatePrompt(userCommand: String, memoryData: String, contextData: String = ""): String {
        return """
            $SYSTEM_INSTRUCTION
            
            USER MEMORY:
            $memoryData
            
            DEVICE CONTEXT (Location/Sensors):
            $contextData
            
            USER COMMAND:
            $userCommand
            
            Respond in JSON format:
        """.trimIndent()
    }

    fun parseGeminiResponse(response: String): JSONObject? {
        return try {
            val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()
            JSONObject(cleanResponse)
        } catch (e: Exception) {
            Log.e("PromptEngine", "Error parsing JSON: ${e.message}")
            null
        }
    }
}
