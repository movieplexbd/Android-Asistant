package com.jarvis.ceotitan.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jarvis.ceotitan.brain.MainBrainViewModel
import com.jarvis.ceotitan.ui.components.*
import com.jarvis.ceotitan.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ListeningScreen(
    navController: NavController,
    viewModel: MainBrainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    var transcribedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = results?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            transcribedText = text
            viewModel.processCommand(text)
        }
    }

    fun startSpeechRecognition() {
        if (!micPermission.status.isGranted) {
            micPermission.launchPermissionRequest()
            return
        }
        isListening = true
        viewModel.setListening(true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "বলুন...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            viewModel.setListening(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEEF2FF), JarvisBackground)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            JarvisTopBar(title = "JARVIS শুনছে", onBack = { navController.popBackStack() })

            Spacer(modifier = Modifier.height(40.dp))

            PulsingOrb(
                isListening = isListening || uiState.isListening,
                isThinking = uiState.isThinking,
                size = 150.dp,
                modifier = Modifier.clickable { startSpeechRecognition() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = when {
                    uiState.isThinking -> "ভাবছি..."
                    isListening -> "শুনছি... বলুন"
                    transcribedText.isNotEmpty() -> "\"$transcribedText\""
                    else -> "মাইক বাটনে চাপুন"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isListening) JarvisBlue else Color(0xFF1A1C2E),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = isListening) {
                WaveformAnimation(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    isActive = isListening,
                    color = JarvisBlue
                )
            }

            AnimatedVisibility(
                visible = uiState.lastResponse.isNotEmpty() && !uiState.isThinking,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(JarvisLightBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("J", color = JarvisBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("JARVIS", fontSize = 11.sp, color = JarvisBlue, fontWeight = FontWeight.SemiBold)
                            Text(uiState.lastResponse, fontSize = 15.sp, color = Color(0xFF1A1C2E))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            QuickCommandChips(onCommandSelected = { viewModel.processCommand(it) })

            Spacer(modifier = Modifier.height(24.dp))

            FloatingActionButton(
                onClick = { startSpeechRecognition() },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                containerColor = if (isListening) JarvisAccent else JarvisBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(10.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Microphone",
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuickCommandChips(onCommandSelected: (String) -> Unit) {
    val quickCmds = listOf(
        "টর্চ জ্বালাও", "ইউটিউব খোলো", "হোমে যাও", "ব্যাক", "ভলিউম বাড়াও", "ছবি তোলো"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("দ্রুত কমান্ড", fontSize = 12.sp, color = Color(0xFF6B7280))
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickCmds.size) { index ->
                FilterChip(
                    selected = false,
                    onClick = { onCommandSelected(quickCmds[index]) },
                    label = { Text(quickCmds[index], fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = JarvisLightBlue,
                        labelColor = JarvisBlue
                    )
                )
            }
        }
    }
}
