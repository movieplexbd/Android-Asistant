package com.assistant.android.ai

import android.util.Log
import com.google.generativeai.client.GenerativeModel
import com.google.generativeai.client.GenerationConfig
import com.google.generativeai.client.SafetySetting
import com.google.generativeai.client.HarmCategory
import com.google.generativeai.client.HarmBlockThreshold

class GeminiClient(private val apiKey: String) {

    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-pro", // Or gemini-ultra, gemini-flash depending on availability and need
            apiKey = apiKey,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                topK = 40,
                topP = 0.95f,
                maxOutputTokens = 1024
            ),
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.MEDIUM_AND_ABOVE)
            )
        )
    }

    suspend fun getGeminiResponse(prompt: String): String? {
        return try {
            val response = generativeModel.generateContent(prompt)
            response.text
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error getting Gemini response: ${e.message}")
            null
        }
    }
}
