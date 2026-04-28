package com.assistant.android.ai

import android.util.Log
import com.google.generativeai.GenerativeModel
import com.google.generativeai.type.BlockThreshold
import com.google.generativeai.type.HarmCategory
import com.google.generativeai.type.SafetySetting
import com.google.generativeai.type.Tool
import com.google.generativeai.type.defineFunction
import com.google.generativeai.type.Schema
import com.google.generativeai.type.generationConfig

class GeminiClient(private val apiKey: String) {

    private val tools = listOf(
        Tool(
            listOf(
                defineFunction(
                    name = "makePhoneCall",
                    description = "Make a phone call to a specific number or contact",
                    parameters = listOf(
                        Schema.str("phoneNumber", "The phone number to call")
                    )
                ),
                defineFunction(
                    name = "sendMessage",
                    description = "Send an SMS message to a specific number",
                    parameters = listOf(
                        Schema.str("phoneNumber", "The phone number to send the message to"),
                        Schema.str("message", "The content of the message")
                    )
                ),
                defineFunction(
                    name = "openApp",
                    description = "Open an application by its package name or common name",
                    parameters = listOf(
                        Schema.str("appName", "The name or package of the app to open")
                    )
                ),
                defineFunction(
                    name = "setReminder",
                    description = "Set a reminder for a specific time",
                    parameters = listOf(
                        Schema.str("message", "The reminder message"),
                        Schema.str("time", "The time for the reminder")
                    )
                ),
                defineFunction(
                    name = "toggleFlashlight",
                    description = "Turn the device flashlight on or off",
                    parameters = listOf(
                        Schema.bool("enabled", "True to turn on, false to turn off")
                    )
                )
            )
        )
    )

    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                topK = 32
                topP = 1f
                maxOutputTokens = 2048
            },
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
            ),
            tools = tools
        )
    }

    suspend fun getGeminiResponse(prompt: String): String? {
        return try {
            val response = generativeModel.generateContent(prompt)
            // Handle function calls if any
            val functionCalls = response.candidates.first().content.parts.filterIsInstance<com.google.generativeai.type.FunctionCallPart>()
            if (functionCalls.isNotEmpty()) {
                // For now, we return the function call as a JSON-like string for the service to handle
                // In a full implementation, we would execute the function and send the result back to Gemini
                val call = functionCalls.first()
                "{\"function\": \"${call.name}\", \"args\": ${call.args}}"
            } else {
                response.text
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error getting Gemini response: ${e.message}")
            null
        }
    }
}
