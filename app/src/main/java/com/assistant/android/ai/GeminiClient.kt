package com.assistant.android.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig

/**
 * Gemini AI client. Provides text reasoning + multimodal vision.
 * Uses gemini-1.5-flash for low-latency, on-device feel responses.
 */
class GeminiClient(private val apiKey: String) {

    private val safety = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
    )

    private val textModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.2f
                topK = 32
                topP = 1f
                maxOutputTokens = 2048
            },
            safetySettings = safety
        )
    }

    private val visionModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 1024
            },
            safetySettings = safety
        )
    }

    suspend fun getGeminiResponse(prompt: String): String? {
        return try {
            val response = textModel.generateContent(prompt)
            response.text
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Gemini response: ${e.message}", e)
            null
        }
    }

    suspend fun analyzeImage(prompt: String, bitmap: Bitmap): String? {
        return try {
            val input = content {
                image(bitmap)
                text(prompt)
            }
            val response = visionModel.generateContent(input)
            response.text
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image: ${e.message}", e)
            null
        }
    }

    /** Lightweight translation helper using the text model. */
    suspend fun translate(text: String, targetLanguage: String): String? {
        val prompt = """
            Translate the following text to $targetLanguage.
            Return ONLY the translated text, no explanations, no quotes.

            TEXT:
            $text
        """.trimIndent()
        return getGeminiResponse(prompt)?.trim()
    }

    companion object {
        private const val TAG = "GeminiClient"
    }
}
