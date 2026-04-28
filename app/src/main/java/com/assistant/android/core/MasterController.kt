package com.assistant.android.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MASTER CONTROLLER — central state machine for the assistant.
 *
 * Live telemetry exposed:
 *  - state          : high-level FSM state
 *  - audioLevel     : 0..1 mic loudness for waveform bars
 *  - partial        : live STT partial transcript (what mic is hearing right now)
 *  - lastCommand    : final recognized command (what user said)
 *  - understood     : parsed intent + reply (what JARVIS understood)
 *  - actionInfo     : human readable action being executed
 *  - lastReply      : last spoken reply
 *  - lastError      : (short, detail) for the debug console
 *  - logs           : rolling activity log (last 30 lines)
 *  - stats          : counters
 */
object MasterController {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _partial = MutableStateFlow<String>("")
    val partial: StateFlow<String> = _partial

    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand

    private val _understood = MutableStateFlow<String?>(null)
    val understood: StateFlow<String?> = _understood

    private val _actionInfo = MutableStateFlow<String?>(null)
    val actionInfo: StateFlow<String?> = _actionInfo

    private val _lastReply = MutableStateFlow<String?>(null)
    val lastReply: StateFlow<String?> = _lastReply

    private val _lastError = MutableStateFlow<ErrorInfo?>(null)
    val lastError: StateFlow<ErrorInfo?> = _lastError

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    data class Stats(
        val commandsHandled: Int = 0,
        val routinesRun: Int = 0,
        val translationsDone: Int = 0,
        val errors: Int = 0
    )

    data class ErrorInfo(
        val short: String,
        val detail: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun transitionTo(newState: State) {
        Log.d(TAG, "${_state.value} -> $newState")
        _state.value = newState
        recordLog("State → $newState")
        if (newState == State.ERROR) {
            _stats.value = _stats.value.copy(errors = _stats.value.errors + 1)
        }
    }

    fun setAudioLevel(level: Float) {
        _audioLevel.value = level.coerceIn(0f, 1f)
    }

    fun setPartial(text: String) { _partial.value = text }

    fun recordCommand(command: String) {
        _lastCommand.value = command
        _partial.value = ""
        _stats.value = _stats.value.copy(commandsHandled = _stats.value.commandsHandled + 1)
        recordLog("Heard: \"$command\"")
    }

    fun recordUnderstood(intent: String, reply: String) {
        val txt = "intent=$intent  → \"$reply\""
        _understood.value = txt
        recordLog("Understood: $txt")
    }

    fun recordAction(description: String) {
        _actionInfo.value = description
        recordLog("Doing: $description")
    }

    fun recordReply(reply: String) {
        _lastReply.value = reply
        recordLog("Speaking: \"$reply\"")
    }

    fun recordError(short: String, detail: String) {
        _lastError.value = ErrorInfo(short, detail)
        _stats.value = _stats.value.copy(errors = _stats.value.errors + 1)
        recordLog("ERROR: $short")
        Log.e(TAG, "ERROR: $short\n$detail")
    }

    fun clearError() { _lastError.value = null }

    fun recordRoutine() { _stats.value = _stats.value.copy(routinesRun = _stats.value.routinesRun + 1) }
    fun recordTranslation() { _stats.value = _stats.value.copy(translationsDone = _stats.value.translationsDone + 1) }

    fun recordLog(msg: String) {
        val stamped = "[${timeFmt.format(Date())}] $msg"
        val cur = _logs.value.toMutableList()
        cur.add(0, stamped)
        if (cur.size > 30) cur.subList(30, cur.size).clear()
        _logs.value = cur
        Log.d(TAG, stamped)
    }

    fun reset() {
        _state.value = State.IDLE
        _lastCommand.value = null
        _lastReply.value = null
        _partial.value = ""
        _understood.value = null
        _actionInfo.value = null
        _lastError.value = null
    }

    private const val TAG = "MasterController"
}
