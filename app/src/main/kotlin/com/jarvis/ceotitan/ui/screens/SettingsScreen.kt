package com.jarvis.ceotitan.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.jarvis.ceotitan.core.utils.SettingsManager
import com.jarvis.ceotitan.ui.components.GlassCard
import com.jarvis.ceotitan.ui.components.JarvisTopBar
import com.jarvis.ceotitan.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val defaultProvider: String = "AUTO",
    val offlinePriority: Boolean = true,
    val banglaBoost: Boolean = true,
    val wakeWordEnabled: Boolean = true,
    val alwaysListen: Boolean = false,
    val autoSendConfirm: Boolean = true,
    val startOnBoot: Boolean = false,
    val batterySaver: Boolean = false,
    val bubbleEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    geminiApiKey = settingsManager.getGeminiApiKey(),
                    groqApiKey = settingsManager.getGroqApiKey(),
                    openRouterApiKey = settingsManager.getOpenRouterApiKey(),
                    offlinePriority = settingsManager.getOfflinePriority(),
                    banglaBoost = settingsManager.getBanglaBoost(),
                    wakeWordEnabled = settingsManager.getWakeWordEnabled(),
                    alwaysListen = settingsManager.getAlwaysListen(),
                    autoSendConfirm = settingsManager.getAutoSendConfirm(),
                    startOnBoot = settingsManager.getStartOnBoot(),
                    batterySaver = settingsManager.getBatterySaver(),
                    bubbleEnabled = settingsManager.getBubbleEnabled(),
                    defaultProvider = settingsManager.getDefaultProvider()
                )
            }
        }
    }

    fun saveGeminiKey(key: String) { viewModelScope.launch { settingsManager.setGeminiApiKey(key); _state.update { it.copy(geminiApiKey = key) } } }
    fun saveGroqKey(key: String) { viewModelScope.launch { settingsManager.setGroqApiKey(key); _state.update { it.copy(groqApiKey = key) } } }
    fun saveOpenRouterKey(key: String) { viewModelScope.launch { settingsManager.setOpenRouterApiKey(key); _state.update { it.copy(openRouterApiKey = key) } } }
    fun setOfflinePriority(v: Boolean) { viewModelScope.launch { settingsManager.setOfflinePriority(v); _state.update { it.copy(offlinePriority = v) } } }
    fun setBanglaBoost(v: Boolean) { viewModelScope.launch { settingsManager.setBanglaBoost(v); _state.update { it.copy(banglaBoost = v) } } }
    fun setWakeWordEnabled(v: Boolean) { viewModelScope.launch { settingsManager.setWakeWordEnabled(v); _state.update { it.copy(wakeWordEnabled = v) } } }
    fun setAlwaysListen(v: Boolean) { viewModelScope.launch { settingsManager.setAlwaysListen(v); _state.update { it.copy(alwaysListen = v) } } }
    fun setAutoSendConfirm(v: Boolean) { viewModelScope.launch { settingsManager.setAutoSendConfirm(v); _state.update { it.copy(autoSendConfirm = v) } } }
    fun setStartOnBoot(v: Boolean) { viewModelScope.launch { settingsManager.setStartOnBoot(v); _state.update { it.copy(startOnBoot = v) } } }
    fun setBatterySaver(v: Boolean) { viewModelScope.launch { settingsManager.setBatterySaver(v); _state.update { it.copy(batterySaver = v) } } }
    fun setBubbleEnabled(v: Boolean) { viewModelScope.launch { settingsManager.setBubbleEnabled(v); _state.update { it.copy(bubbleEnabled = v) } } }
}

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showGeminiKey by remember { mutableStateOf(false) }
    var showGroqKey by remember { mutableStateOf(false) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    var geminiInput by remember { mutableStateOf("") }
    var groqInput by remember { mutableStateOf("") }
    var openRouterInput by remember { mutableStateOf("") }

    LaunchedEffect(state.geminiApiKey) { if (geminiInput.isEmpty()) geminiInput = state.geminiApiKey }
    LaunchedEffect(state.groqApiKey) { if (groqInput.isEmpty()) groqInput = state.groqApiKey }
    LaunchedEffect(state.openRouterApiKey) { if (openRouterInput.isEmpty()) openRouterInput = state.openRouterApiKey }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), JarvisBackground)))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JarvisTopBar(title = "সেটিংস", onBack = { navController.popBackStack() })

            SectionTitle("API Keys")
            ApiKeyField("Gemini API Key", geminiInput, showGeminiKey, onValueChange = { geminiInput = it }, onToggleVisibility = { showGeminiKey = !showGeminiKey }, onSave = { viewModel.saveGeminiKey(geminiInput) })
            ApiKeyField("Groq API Key", groqInput, showGroqKey, onValueChange = { groqInput = it }, onToggleVisibility = { showGroqKey = !showGroqKey }, onSave = { viewModel.saveGroqKey(groqInput) })
            ApiKeyField("OpenRouter API Key", openRouterInput, showOpenRouterKey, onValueChange = { openRouterInput = it }, onToggleVisibility = { showOpenRouterKey = !showOpenRouterKey }, onSave = { viewModel.saveOpenRouterKey(openRouterInput) })

            SectionTitle("AI Settings")
            ToggleItem("Offline Priority (প্রথমে লোকাল)", Icons.Default.WifiOff, state.offlinePriority) { viewModel.setOfflinePriority(it) }
            ToggleItem("Bangla Boost (বাংলা মোড)", Icons.Default.Language, state.banglaBoost) { viewModel.setBanglaBoost(it) }

            SectionTitle("Voice Settings")
            ToggleItem("Wake Word (Hey Jarvis)", Icons.Default.RecordVoiceOver, state.wakeWordEnabled) { viewModel.setWakeWordEnabled(it) }
            ToggleItem("Always Listen (সবসময় শোনো)", Icons.Default.Hearing, state.alwaysListen) { viewModel.setAlwaysListen(it) }
            ToggleItem("Auto-Send Confirm (নিশ্চিত করার আগে জিজ্ঞেস)", Icons.Default.Send, state.autoSendConfirm) { viewModel.setAutoSendConfirm(it) }

            SectionTitle("App Behavior")
            ToggleItem("Start on Boot (চালু হলে শুরু)", Icons.Default.PowerSettingsNew, state.startOnBoot) { viewModel.setStartOnBoot(it) }
            ToggleItem("Battery Saver Mode", Icons.Default.BatteryAlert, state.batterySaver) { viewModel.setBatterySaver(it) }
            ToggleItem("Floating Bubble", Icons.Default.BubbleChart, state.bubbleEnabled) { viewModel.setBubbleEnabled(it) }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = JarvisBlue,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    showText: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, JarvisBlue.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 13.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    visualTransformation = if (showText) VisualTransformation.None else PasswordVisualTransformation(),
                    placeholder = { Text("API key লিখুন...", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JarvisBlue, unfocusedBorderColor = Color(0xFFE0E7FF)),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onToggleVisibility) {
                            Icon(if (showText) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Color(0xFF9CA3AF))
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue)) {
                    Text("Save", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ToggleItem(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, JarvisBlue.copy(0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = JarvisBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontSize = 14.sp, color = Color(0xFF1A1C2E), modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JarvisBlue))
        }
    }
}
