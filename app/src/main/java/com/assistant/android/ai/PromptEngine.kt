package com.assistant.android.ai

import android.util.Log
import org.json.JSONObject

object PromptEngine {

    private const val SYSTEM_INSTRUCTION = """
        You are a powerful, proactive Android AI Assistant. 
        Your goal is to help the user by executing actions, providing information, and learning from context.
        
        CONTEXTUAL AWARENESS:
        - Use the provided 'Memory' to remember user preferences, names, and past interactions.
        - If the user says "Call my mom", check memory for a contact named "Mom".
        - Be proactive: if it's morning, suggest checking the weather or schedule.

        OUTPUT FORMAT:
        You must respond in a structured JSON format if an action is required. 
        If it's just a conversation, provide a natural 'reply'.
        
        JSON Schema:
        {
          "intent": "call | message | open_app | reminder | automation | info | vision",
          "target": "phone number, app package, or search query",
          "message": "content for SMS or reminder",
          "time": "ISO 8601 time for reminders",
          "reply": "What you will say to the user",
          "proactive_suggestion": "A follow-up action the user might want"
        }
    """

    fun generatePrompt(userCommand: String, memoryData: String): String {
        return """
            $SYSTEM_INSTRUCTION
            
            USER MEMORY & HISTORY:
            $memoryData
            
            USER COMMAND:
            $userCommand
            
            Respond in JSON format:
        """.trimIndent()
    }

    fun parseGeminiResponse(response: String): JSONObject? {
        return try {
            // Handle cases where Gemini might wrap JSON in markdown code blocks
            val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()
            JSONObject(cleanResponse)
        } catch (e: Exception) {
            Log.e("PromptEngine", "Error parsing JSON: ${e.message}")
            null
        }
    }
}
