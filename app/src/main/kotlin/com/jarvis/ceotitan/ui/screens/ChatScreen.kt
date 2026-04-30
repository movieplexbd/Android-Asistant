package com.jarvis.ceotitan.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jarvis.ceotitan.brain.ChatMessage
import com.jarvis.ceotitan.brain.MainBrainViewModel
import com.jarvis.ceotitan.ui.components.GlassCard
import com.jarvis.ceotitan.ui.components.JarvisTopBar
import com.jarvis.ceotitan.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: MainBrainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = results?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            inputText = text
        }
    }

    LaunchedEffect(uiState.chatMessages.size) {
        if (uiState.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatMessages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), JarvisBackground)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JarvisBlue)
                }
                Text("AI Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = JarvisError)
                }
            }

            if (uiState.chatMessages.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = JarvisBlue.copy(0.3f), modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("কথা বলুন বা টাইপ করুন", fontSize = 18.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium)
                        Text("Bangla, English, বা Banglish-এ", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.chatMessages) { message ->
                        ChatBubble(message = message)
                    }
                    if (uiState.isThinking) {
                        item { ThinkingBubble() }
                    }
                }
            }

            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.processCommand(inputText)
                        inputText = ""
                        focusManager.clearFocus()
                    }
                },
                onMicClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "বলুন...")
                    }
                    try { speechLauncher.launch(intent) } catch (e: Exception) {}
                }
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val time = timeFormat.format(Date(message.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(JarvisLightBlue),
                contentAlignment = Alignment.Center
            ) {
                Text("J", color = JarvisBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (message.isUser) 16.dp else 4.dp,
                            topEnd = if (message.isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp, bottomEnd = 16.dp
                        )
                    )
                    .background(if (message.isUser) JarvisBlue else Color.White)
                    .border(
                        width = if (message.isUser) 0.dp else 1.dp,
                        color = if (message.isUser) Color.Transparent else JarvisBlue.copy(0.1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.text,
                    color = if (message.isUser) Color.White else Color(0xFF1A1C2E),
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = 10.sp, color = Color(0xFF9CA3AF))
                if (!message.isUser) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(
                                when (message.brainLayer) {
                                    "CACHE" -> JarvisGold.copy(0.15f)
                                    "CLOUD" -> JarvisAccent.copy(0.15f)
                                    else -> JarvisSuccess.copy(0.15f)
                                }
                            ).padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            message.brainLayer,
                            fontSize = 9.sp,
                            color = when (message.brainLayer) {
                                "CACHE" -> JarvisGold
                                "CLOUD" -> JarvisAccent
                                else -> JarvisSuccess
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(JarvisLightBlue), contentAlignment = Alignment.Center) {
            Text("J", color = JarvisBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, JarvisBlue.copy(0.1f), RoundedCornerShape(16.dp)).padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    val infiniteTransition = rememberInfiniteTransition(label = "dot$i")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(400, delayMillis = i * 133),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ), label = "alpha$i"
                    )
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(JarvisBlue.copy(alpha)))
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Bangla, English বা Banglish লিখুন...", fontSize = 14.sp) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisBlue,
                unfocusedBorderColor = Color(0xFFE0E7FF)
            ),
            maxLines = 4
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onMicClick, modifier = Modifier.size(48.dp)) {
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(JarvisLightBlue), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Mic, contentDescription = "Mic", tint = JarvisBlue)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(48.dp),
            enabled = inputText.isNotBlank()
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(if (inputText.isNotBlank()) JarvisBlue else Color(0xFFE0E7FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = if (inputText.isNotBlank()) Color.White else Color(0xFF9CA3AF))
            }
        }
    }
}
