package com.jarvis.ceotitan.core.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val OFFLINE_PRIORITY = booleanPreferencesKey("offline_priority")
        val BANGLA_BOOST = booleanPreferencesKey("bangla_boost")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        val ALWAYS_LISTEN = booleanPreferencesKey("always_listen")
        val AUTO_SEND_CONFIRM = booleanPreferencesKey("auto_send_confirm")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val VOICE_PIN = stringPreferencesKey("voice_pin")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
    }

    suspend fun getGeminiApiKey(): String = dataStore.data.first()[Keys.GEMINI_API_KEY] ?: ""
    suspend fun getGroqApiKey(): String = dataStore.data.first()[Keys.GROQ_API_KEY] ?: ""
    suspend fun getOpenRouterApiKey(): String = dataStore.data.first()[Keys.OPENROUTER_API_KEY] ?: ""

    suspend fun setGeminiApiKey(key: String) = dataStore.edit { it[Keys.GEMINI_API_KEY] = key }
    suspend fun setGroqApiKey(key: String) = dataStore.edit { it[Keys.GROQ_API_KEY] = key }
    suspend fun setOpenRouterApiKey(key: String) = dataStore.edit { it[Keys.OPENROUTER_API_KEY] = key }

    suspend fun getOfflinePriority(): Boolean = dataStore.data.first()[Keys.OFFLINE_PRIORITY] ?: true
    suspend fun setOfflinePriority(value: Boolean) = dataStore.edit { it[Keys.OFFLINE_PRIORITY] = value }

    suspend fun getBanglaBoost(): Boolean = dataStore.data.first()[Keys.BANGLA_BOOST] ?: true
    suspend fun setBanglaBoost(value: Boolean) = dataStore.edit { it[Keys.BANGLA_BOOST] = value }

    suspend fun getWakeWordEnabled(): Boolean = dataStore.data.first()[Keys.WAKE_WORD_ENABLED] ?: true
    suspend fun setWakeWordEnabled(value: Boolean) = dataStore.edit { it[Keys.WAKE_WORD_ENABLED] = value }

    suspend fun getAlwaysListen(): Boolean = dataStore.data.first()[Keys.ALWAYS_LISTEN] ?: false
    suspend fun setAlwaysListen(value: Boolean) = dataStore.edit { it[Keys.ALWAYS_LISTEN] = value }

    suspend fun getAutoSendConfirm(): Boolean = dataStore.data.first()[Keys.AUTO_SEND_CONFIRM] ?: true
    suspend fun setAutoSendConfirm(value: Boolean) = dataStore.edit { it[Keys.AUTO_SEND_CONFIRM] = value }

    suspend fun getStartOnBoot(): Boolean = dataStore.data.first()[Keys.START_ON_BOOT] ?: false
    suspend fun setStartOnBoot(value: Boolean) = dataStore.edit { it[Keys.START_ON_BOOT] = value }

    suspend fun getBatterySaver(): Boolean = dataStore.data.first()[Keys.BATTERY_SAVER] ?: false
    suspend fun setBatterySaver(value: Boolean) = dataStore.edit { it[Keys.BATTERY_SAVER] = value }

    suspend fun getBubbleEnabled(): Boolean = dataStore.data.first()[Keys.BUBBLE_ENABLED] ?: true
    suspend fun setBubbleEnabled(value: Boolean) = dataStore.edit { it[Keys.BUBBLE_ENABLED] = value }

    suspend fun getDefaultProvider(): String = dataStore.data.first()[Keys.DEFAULT_PROVIDER] ?: "AUTO"
    suspend fun setDefaultProvider(value: String) = dataStore.edit { it[Keys.DEFAULT_PROVIDER] = value }

    suspend fun isSetupComplete(): Boolean = dataStore.data.first()[Keys.SETUP_COMPLETE] ?: false
    suspend fun setSetupComplete(value: Boolean) = dataStore.edit { it[Keys.SETUP_COMPLETE] = value }

    suspend fun getTtsSpeed(): Float = dataStore.data.first()[Keys.TTS_SPEED] ?: 1.0f
    suspend fun setTtsSpeed(value: Float) = dataStore.edit { it[Keys.TTS_SPEED] = value }

    suspend fun getTtsPitch(): Float = dataStore.data.first()[Keys.TTS_PITCH] ?: 1.0f
    suspend fun setTtsPitch(value: Float) = dataStore.edit { it[Keys.TTS_PITCH] = value }

    fun getSettingsFlow(): Flow<Map<Preferences.Key<*>, Any>> = dataStore.data.map { prefs ->
        prefs.asMap()
    }
}
