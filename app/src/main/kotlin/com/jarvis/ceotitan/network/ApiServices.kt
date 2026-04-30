package com.jarvis.ceotitan.network

import com.jarvis.ceotitan.brain.cloud.*
import retrofit2.http.*

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") apiKey: String,
        @Body request: GroqRequest
    ): GroqResponse
}

interface OpenRouterApiService {
    @POST("api/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") apiKey: String,
        @Header("HTTP-Referer") referer: String = "https://jarvis.ceotitan.app",
        @Header("X-Title") title: String = "JARVIS CEO TITAN",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}
