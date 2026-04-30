package com.jarvis.ceotitan.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jarvis.ceotitan.ui.components.*
import com.jarvis.ceotitan.ui.navigation.Screen
import com.jarvis.ceotitan.ui.theme.*
import com.jarvis.ceotitan.brain.MainBrainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainBrainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEEF2FF),
                        Color(0xFFF5F7FF),
                        JarvisBackground
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp)
        ) {
            HomeTopSection(
                uiState = uiState,
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            CentralOrbSection(
                isListening = uiState.isListening,
                isThinking = uiState.isThinking,
                lastResponse = uiState.lastResponse,
                onOrbClick = { navController.navigate(Screen.Listening.route) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            QuickActionsSection(navController = navController, viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            StatusCardsSection(uiState = uiState)

            Spacer(modifier = Modifier.height(16.dp))

            RecentCommandsSection(recentCommands = uiState.recentCommands)

            Spacer(modifier = Modifier.height(16.dp))

            SmartSuggestionsSection(suggestions = uiState.smartSuggestions, viewModel = viewModel)
        }

        FloatingMicButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            isListening = uiState.isListening,
            onClick = { navController.navigate(Screen.Listening.route) }
        )
    }
}

@Composable
private fun HomeTopSection(
    uiState: com.jarvis.ceotitan.brain.JarvisUiState,
    onSettingsClick: () -> Unit
) {
    val currentTime = remember {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "সুপ্রভাত"
        in 12..16 -> "শুভ অপরাহ্ন"
        in 17..20 -> "শুভ সন্ধ্যা"
        else -> "শুভ রাত্রি"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 52.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greeting,
                fontSize = 14.sp,
                color = JarvisBlue.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "JARVIS CEO TITAN",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1C2E)
            )
            Text(
                text = currentTime,
                fontSize = 13.sp,
                color = Color(0xFF6B7280)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                text = if (uiState.isOnline) "Online" else "Offline",
                isActive = uiState.isOnline
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = JarvisBlue
                )
            }
        }
    }
}

@Composable
private fun CentralOrbSection(
    isListening: Boolean,
    isThinking: Boolean,
    lastResponse: String,
    onOrbClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PulsingOrb(
                    isListening = isListening,
                    isThinking = isThinking,
                    size = 110.dp,
                    modifier = Modifier.clickable { onOrbClick() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = isListening,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    WaveformAnimation(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        isActive = isListening,
                        color = JarvisBlue
                    )
                }

                Text(
                    text = when {
                        isListening -> "শুনছি... বলুন"
                        isThinking -> "ভাবছি..."
                        lastResponse.isNotEmpty() -> lastResponse
                        else -> "ট্যাপ করুন অথবা বলুন \"Hey Jarvis\""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isListening) JarvisBlue else Color(0xFF4A4F6A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    navController: NavController,
    viewModel: MainBrainViewModel
) {
    val quickActions = listOf(
        QuickAction("Chat", Icons.Default.Chat, JarvisBlue) { navController.navigate(Screen.Chat.route) },
        QuickAction("Memory", Icons.Default.Psychology, Color(0xFF7C3AED)) { navController.navigate(Screen.Memory.route) },
        QuickAction("Automation", Icons.Default.AutoFixHigh, Color(0xFF059669)) { navController.navigate(Screen.Automation.route) },
        QuickAction("Business", Icons.Default.BusinessCenter, JarvisGold) { navController.navigate(Screen.Business.route) },
        QuickAction("Offline", Icons.Default.WifiOff, Color(0xFFDC2626)) { navController.navigate(Screen.OfflineCommands.route) },
        QuickAction("Permissions", Icons.Default.Security, Color(0xFF0891B2)) { navController.navigate(Screen.Permissions.route) }
    )

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "দ্রুত অ্যাকশন",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1C2E)
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(quickActions) { action ->
                QuickActionCard(action = action)
            }
        }
    }
}

data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionCard(action: QuickAction) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable { action.onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = action.color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, action.color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(action.icon, contentDescription = action.label, tint = action.color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(action.label, fontSize = 10.sp, color = action.color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatusCardsSection(uiState: com.jarvis.ceotitan.brain.JarvisUiState) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(
            modifier = Modifier.weight(1f),
            title = "Brain Layer",
            value = uiState.currentBrainLayer,
            icon = Icons.Default.Memory,
            color = JarvisBlue
        )
        StatusCard(
            modifier = Modifier.weight(1f),
            title = "Cache Hits",
            value = "${uiState.cacheHits}",
            icon = Icons.Default.Speed,
            color = JarvisSuccess
        )
        StatusCard(
            modifier = Modifier.weight(1f),
            title = "Commands",
            value = "${uiState.totalCommands}",
            icon = Icons.Default.Timeline,
            color = JarvisGold
        )
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, fontSize = 9.sp, color = color.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RecentCommandsSection(recentCommands: List<String>) {
    if (recentCommands.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("সাম্প্রতিক কমান্ড", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1C2E))
        Spacer(modifier = Modifier.height(12.dp))
        recentCommands.take(3).forEach { cmd ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisWhite),
                border = BorderStroke(1.dp, JarvisBlue.copy(alpha = 0.08f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = JarvisBlue.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(cmd, fontSize = 13.sp, color = Color(0xFF4A4F6A))
                }
            }
        }
    }
}

@Composable
private fun SmartSuggestionsSection(
    suggestions: List<String>,
    viewModel: MainBrainViewModel
) {
    if (suggestions.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("স্মার্ট সাজেশন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1C2E))
        Spacer(modifier = Modifier.height(12.dp))
        suggestions.take(3).forEach { suggestion ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.processCommand(suggestion) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisLightBlue)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = JarvisBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(suggestion, fontSize = 13.sp, color = JarvisBlue)
                }
            }
        }
    }
}

@Composable
private fun FloatingMicButton(
    modifier: Modifier = Modifier,
    isListening: Boolean,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(70.dp),
        shape = CircleShape,
        containerColor = if (isListening) JarvisAccent else JarvisBlue,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(8.dp)
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = "Mic",
            modifier = Modifier.size(30.dp)
        )
    }
}
