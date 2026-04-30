package com.jarvis.ceotitan.brain

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ceotitan.brain.cache.CacheBrain
import com.jarvis.ceotitan.brain.cloud.CloudBrain
import com.jarvis.ceotitan.brain.local.LocalBrain
import com.jarvis.ceotitan.memory.MemoryManager
import com.jarvis.ceotitan.understanding.SpeechUnderstandingEngine
import com.jarvis.ceotitan.understanding.IntentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JarvisUiState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isOnline: Boolean = true,
    val lastCommand: String = "",
    val lastResponse: String = "",
    val currentBrainLayer: String = "LOCAL",
    val cacheHits: Int = 0,
    val totalCommands: Int = 0,
    val recentCommands: List<String> = emptyList(),
    val smartSuggestions: List<String> = listOf(
        "টর্চ জ্বালাও",
        "ইউটিউব খোলো",
        "রহিমকে কল দাও"
    ),
    val chatMessages: List<ChatMessage> = emptyList(),
    val isConfirmationNeeded: Boolean = false,
    val confirmationMessage: String = ""
)

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val brainLayer: String = "LOCAL",
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class MainBrainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localBrain: LocalBrain,
    private val cacheBrain: CacheBrain,
    private val cloudBrain: CloudBrain,
    private val memoryManager: MemoryManager,
    private val speechEngine: SpeechUnderstandingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var commandCount = 0
    private var cacheHitCount = 0

    fun processCommand(rawInput: String) {
        if (rawInput.isBlank()) return
        viewModelScope.launch {
            commandCount++
            _uiState.update {
                it.copy(
                    lastCommand = rawInput,
                    isThinking = true,
                    recentCommands = (listOf(rawInput) + it.recentCommands).take(10),
                    totalCommands = commandCount
                )
            }

            addChatMessage(rawInput, isUser = true)

            try {
                val intent = speechEngine.understand(rawInput)
                val response = processIntent(intent, rawInput)

                addChatMessage(response.text, isUser = false, brainLayer = response.layer)
                _uiState.update {
                    it.copy(
                        isThinking = false,
                        lastResponse = response.text,
                        currentBrainLayer = response.layer,
                        cacheHits = cacheHitCount
                    )
                }
                memoryManager.saveInteraction(rawInput, response.text)
            } catch (e: Exception) {
                val errorMsg = "দুঃখিত, একটু সমস্যা হয়েছে। আবার চেষ্টা করুন।"
                addChatMessage(errorMsg, isUser = false)
                _uiState.update { it.copy(isThinking = false, lastResponse = errorMsg) }
            }
        }
    }

    private suspend fun processIntent(intent: IntentResult, rawInput: String): BrainResponse {
        if (intent.confidence > 0.7f) {
            val localResult = localBrain.handle(intent)
            if (localResult != null) {
                return BrainResponse(localResult, "LOCAL")
            }
        }

        val cachedResult = cacheBrain.get(rawInput)
        if (cachedResult != null) {
            cacheHitCount++
            return BrainResponse(cachedResult, "CACHE")
        }

        val cloudResult = cloudBrain.query(rawInput, intent)
        cacheBrain.put(rawInput, cloudResult)
        return BrainResponse(cloudResult, "CLOUD")
    }

    private fun addChatMessage(text: String, isUser: Boolean, brainLayer: String = "LOCAL") {
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + ChatMessage(
                    text = text,
                    isUser = isUser,
                    brainLayer = brainLayer
                )
            )
        }
    }

    fun setListening(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun updateOnlineStatus(isOnline: Boolean) {
        _uiState.update { it.copy(isOnline = isOnline) }
    }

    fun clearChat() {
        _uiState.update { it.copy(chatMessages = emptyList()) }
    }

    fun confirmAction() {
        _uiState.update { it.copy(isConfirmationNeeded = false) }
    }

    fun cancelAction() {
        _uiState.update { it.copy(isConfirmationNeeded = false, confirmationMessage = "") }
    }
}

data class BrainResponse(val text: String, val layer: String)
