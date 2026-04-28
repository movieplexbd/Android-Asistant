package com.assistant.android.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.generativeai.GenerativeModel
import com.google.generativeai.type.BlockThreshold
import com.google.generativeai.type.HarmCategory
import com.google.generativeai.type.SafetySetting
import com.google.generativeai.type.Tool
import com.google.generativeai.type.defineFunction
import com.google.generativeai.type.Schema
import com.google.generativeai.type.generationConfig
import com.google.generativeai.type.content

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

    private val textModel: GenerativeModel by lazy {
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

    private val visionModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )
    }

    suspend fun getGeminiResponse(prompt: String): String? {
        return try {
            val response = textModel.generateContent(prompt)
            val functionCalls = response.candidates.first().content.parts.filterIsInstance<com.google.generativeai.type.FunctionCallPart>()
            if (functionCalls.isNotEmpty()) {
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

    suspend fun analyzeImage(prompt: String, bitmap: Bitmap): String? {
        return try {
            val inputContent = content {
                image(bitmap)
                text(prompt)
            }
            val response = visionModel.generateContent(inputContent)
            response.text
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error analyzing image: ${e.message}")
            null
        }
    }
}
