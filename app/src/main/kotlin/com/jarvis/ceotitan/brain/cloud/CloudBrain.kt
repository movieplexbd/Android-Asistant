package com.jarvis.ceotitan.brain.cloud

import com.jarvis.ceotitan.network.GeminiApiService
import com.jarvis.ceotitan.network.GroqApiService
import com.jarvis.ceotitan.network.OpenRouterApiService
import com.jarvis.ceotitan.core.utils.SettingsManager
import com.jarvis.ceotitan.understanding.Intent as JarvisIntent
import com.jarvis.ceotitan.understanding.IntentResult
import javax.inject.Inject
import javax.inject.Singleton

enum class CloudProvider { GEMINI, GROQ, OPENROUTER, AUTO }

@Singleton
class CloudBrain @Inject constructor(
    private val geminiService: GeminiApiService,
    private val groqService: GroqApiService,
    private val openRouterService: OpenRouterApiService,
    private val settingsManager: SettingsManager
) {

    suspend fun query(input: String, intent: IntentResult): String {
        val provider = selectProvider(intent)
        return try {
            when (provider) {
                CloudProvider.GEMINI -> queryGemini(input, intent)
                CloudProvider.GROQ -> queryGroq(input, intent)
                CloudProvider.OPENROUTER -> queryOpenRouter(input, intent)
                CloudProvider.AUTO -> {
                    val isComplex = isComplexQuery(intent)
                    if (isComplex) queryGemini(input, intent) else queryGroq(input, intent)
                }
            }
        } catch (e: Exception) {
            try {
                if (provider != CloudProvider.GROQ) queryGroq(input, intent)
                else queryGemini(input, intent)
            } catch (e2: Exception) {
                "দুঃখিত, এই মুহূর্তে উত্তর দিতে পারছি না। ইন্টারনেট সংযোগ চেক করুন।"
            }
        }
    }

    private suspend fun queryGemini(input: String, intent: IntentResult): String {
        val apiKey = settingsManager.getGeminiApiKey()
        if (apiKey.isBlank()) return fallbackResponse(input)

        val systemPrompt = buildSystemPrompt()
        val response = geminiService.generateContent(
            apiKey = apiKey,
            request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                    GeminiContent(role = "user", parts = listOf(GeminiPart(text = input)))
                )
            )
        )
        return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "উত্তর পাওয়া যাচ্ছে না।"
    }

    private suspend fun queryGroq(input: String, intent: IntentResult): String {
        val apiKey = settingsManager.getGroqApiKey()
        if (apiKey.isBlank()) return fallbackResponse(input)

        val response = groqService.chat(
            apiKey = "Bearer $apiKey",
            request = GroqRequest(
                model = "mixtral-8x7b-32768",
                messages = listOf(
                    GroqMessage("system", buildSystemPrompt()),
                    GroqMessage("user", input)
                ),
                maxTokens = 500,
                temperature = 0.7f
            )
        )
        return response.choices.firstOrNull()?.message?.content
            ?: "উত্তর পাওয়া যাচ্ছে না।"
    }

    private suspend fun queryOpenRouter(input: String, intent: IntentResult): String {
        val apiKey = settingsManager.getOpenRouterApiKey()
        if (apiKey.isBlank()) return queryGroq(input, intent)

        val response = openRouterService.chat(
            apiKey = "Bearer $apiKey",
            request = OpenRouterRequest(
                model = "google/gemma-3-27b-it:free",
                messages = listOf(
                    OpenRouterMessage("system", buildSystemPrompt()),
                    OpenRouterMessage("user", input)
                )
            )
        )
        return response.choices.firstOrNull()?.message?.content
            ?: "উত্তর পাওয়া যাচ্ছে না।"
    }

    private fun selectProvider(intent: IntentResult): CloudProvider {
        return when {
            isComplexQuery(intent) -> CloudProvider.GEMINI
            isSpeedRequired(intent) -> CloudProvider.GROQ
            else -> CloudProvider.AUTO
        }
    }

    private fun isComplexQuery(intent: IntentResult): Boolean {
        return intent.intent in listOf(
            JarvisIntent.ASK_AI.name, JarvisIntent.CHAT_AI.name,
            JarvisIntent.WEATHER.name, JarvisIntent.BUSINESS_NOTE.name
        )
    }

    private fun isSpeedRequired(intent: IntentResult): Boolean {
        return intent.intent in listOf(
            JarvisIntent.TIME_DATE.name, JarvisIntent.REMIND_ME.name
        )
    }

    private fun buildSystemPrompt(): String {
        return """You are JARVIS CEO TITAN, an ultra-smart Android AI assistant.
You assist in Bangla, English, and Banglish (mixed) languages.
Be concise, smart and helpful. Reply in the same language the user speaks.
For Bangla: use natural Bangla. For English: use clear English. For mixed: use mixed naturally.
Keep responses short (1-3 sentences max) unless detailed info is needed.
You help with: phone control, apps, messages, calls, business, general knowledge.
Be like a smart friend who understands even unclear speech and gets the point quickly."""
    }

    private fun fallbackResponse(input: String): String {
        return "API key সেটিংসে যোগ করুন। আমি অফলাইনে সাহায্য করতে পারি।"
    }
}

data class GeminiRequest(val contents: List<GeminiContent>)
data class GeminiContent(val role: String = "user", val parts: List<GeminiPart>)
data class GeminiPart(val text: String)
data class GeminiResponse(val candidates: List<GeminiCandidate>)
data class GeminiCandidate(val content: GeminiContent)

data class GroqRequest(val model: String, val messages: List<GroqMessage>, val maxTokens: Int = 500, val temperature: Float = 0.7f)
data class GroqMessage(val role: String, val content: String)
data class GroqResponse(val choices: List<GroqChoice>)
data class GroqChoice(val message: GroqMessage)

data class OpenRouterRequest(val model: String, val messages: List<OpenRouterMessage>)
data class OpenRouterMessage(val role: String, val content: String)
data class OpenRouterResponse(val choices: List<OpenRouterChoice>)
data class OpenRouterChoice(val message: OpenRouterMessage)
