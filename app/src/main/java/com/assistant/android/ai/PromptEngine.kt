package com.assistant.android.ai

import org.json.JSONObject

object PromptEngine {

    private const val SYSTEM_INSTRUCTION = """
You are a helpful AI assistant. Your goal is to assist the user by understanding their commands and converting them into structured JSON actions. Maintain conversation context and use memory data when required.
Always return response in STRICT JSON format. Do not include any other text or explanation outside the JSON.
"""

    private const val JSON_FORMAT_EXAMPLE = """
{
  
  "intent": "call | open_app | message | reminder | info | automation | unknown",
  "target": "",
  "message": "",
  "time": "",
  "reply": ""
}
"""

    fun generatePrompt(userCommand: String, memoryData: String = ""): String {
        val promptBuilder = StringBuilder()
        promptBuilder.append(SYSTEM_INSTRUCTION)
        promptBuilder.append("\n\nHere's some relevant memory data: ")
        promptBuilder.append(memoryData.ifEmpty { "No memory data available." })
        promptBuilder.append("\n\nUser command: ")
        promptBuilder.append(userCommand)
        promptBuilder.append("\n\nRespond in the following STRICT JSON format:\n")
        promptBuilder.append(JSON_FORMAT_EXAMPLE)
        return promptBuilder.toString()
    }

    fun parseGeminiResponse(response: String): JSONObject? {
        return try {
            JSONObject(response)
        } catch (e: Exception) {
            null
        }
    }
}
