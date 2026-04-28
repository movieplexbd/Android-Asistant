package com.assistant.android.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * MASTER CONTROLLER — central state machine for the assistant.
 *
 * Every UI surface (MainActivity, OverlayService, ForegroundService) reads from this single
 * source of truth so the bubble, notification and main screen always agree on what NEXUS is doing.
 */
object MasterController {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand

    private val _lastReply = MutableStateFlow<String?>(null)
    val lastReply: StateFlow<String?> = _lastReply

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    data class Stats(
        val commandsHandled: Int = 0,
        val routinesRun: Int = 0,
        val translationsDone: Int = 0,
        val errors: Int = 0
    )

    fun transitionTo(newState: State) {
        Log.d(TAG, "${_state.value} -> $newState")
        _state.value = newState
        if (newState == State.ERROR) {
            _stats.value = _stats.value.copy(errors = _stats.value.errors + 1)
        }
    }

    fun recordCommand(command: String) {
        _lastCommand.value = command
        _stats.value = _stats.value.copy(commandsHandled = _stats.value.commandsHandled + 1)
    }

    fun recordReply(reply: String) { _lastReply.value = reply }
    fun recordRoutine() { _stats.value = _stats.value.copy(routinesRun = _stats.value.routinesRun + 1) }
    fun recordTranslation() { _stats.value = _stats.value.copy(translationsDone = _stats.value.translationsDone + 1) }

    fun reset() {
        _state.value = State.IDLE
        _lastCommand.value = null
        _lastReply.value = null
    }

    private const val TAG = "MasterController"
}
