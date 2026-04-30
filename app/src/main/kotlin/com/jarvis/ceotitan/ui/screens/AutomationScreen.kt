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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jarvis.ceotitan.brain.MainBrainViewModel
import com.jarvis.ceotitan.ui.components.GlassCard
import com.jarvis.ceotitan.ui.components.JarvisTopBar
import com.jarvis.ceotitan.ui.components.StatusChip
import com.jarvis.ceotitan.ui.theme.*
import com.jarvis.ceotitan.automation.accessibility.JarvisAccessibilityService

@Composable
fun AutomationScreen(
    navController: NavController,
    viewModel: MainBrainViewModel = hiltViewModel()
) {
    val accessibilityEnabled = remember { JarvisAccessibilityService.isEnabled() }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), JarvisBackground)))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JarvisTopBar(title = "Automation Center", onBack = { navController.popBackStack() })

            if (!accessibilityEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = JarvisGold.copy(0.1f)),
                    border = BorderStroke(1.dp, JarvisGold.copy(0.3f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = JarvisGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("সম্পূর্ণ automation এর জন্য Accessibility Service চালু করুন।", fontSize = 13.sp, color = JarvisGold)
                    }
                }
            }

            SectionTitle("WhatsApp Automation")
            AutomationCommandGrid(
                commands = listOf(
                    AutoCmd("WhatsApp Open", "ওয়াটসঅ্যাপ খোলো", Icons.Default.Message, JarvisSuccess),
                    AutoCmd("Send Message", "রহিমকে হ্যালো পাঠাও", Icons.Default.Send, JarvisBlue),
                    AutoCmd("Read Chats", "নতুন মেসেজ পড়ো", Icons.Default.MarkEmailRead, JarvisAccent),
                    AutoCmd("Last Chat", "শেষ চ্যাট খোলো", Icons.Default.Chat, Color(0xFF7C3AED))
                ),
                onCommandClick = { viewModel.processCommand(it.voiceCmd) }
            )

            SectionTitle("YouTube Automation")
            AutomationCommandGrid(
                commands = listOf(
                    AutoCmd("YouTube Open", "ইউটিউব খোলো", Icons.Default.PlayCircle, JarvisError),
                    AutoCmd("Search Video", "ইউটিউবে সার্চ করো", Icons.Default.Search, JarvisBlue),
                    AutoCmd("Scroll Feed", "নিচে নামাও", Icons.Default.SwipeDown, JarvisSuccess),
                    AutoCmd("First Video", "প্রথম ভিডিও চালাও", Icons.Default.PlayArrow, JarvisGold)
                ),
                onCommandClick = { viewModel.processCommand(it.voiceCmd) }
            )

            SectionTitle("Screen Control")
            AutomationCommandGrid(
                commands = listOf(
                    AutoCmd("Go Home", "হোমে যাও", Icons.Default.Home, JarvisBlue),
                    AutoCmd("Go Back", "ব্যাক যাও", Icons.Default.ArrowBack, Color(0xFF6B7280)),
                    AutoCmd("Recent Apps", "সাম্প্রতিক অ্যাপ", Icons.Default.GridView, JarvisAccent),
                    AutoCmd("Scroll Down", "নিচে স্ক্রোল করো", Icons.Default.KeyboardArrowDown, JarvisSuccess),
                    AutoCmd("Scroll Up", "উপরে স্ক্রোল করো", Icons.Default.KeyboardArrowUp, JarvisGold),
                    AutoCmd("Take Screenshot", "স্ক্রিনশট নাও", Icons.Default.Screenshot, Color(0xFF7C3AED))
                ),
                onCommandClick = { viewModel.processCommand(it.voiceCmd) }
            )

            SectionTitle("Phone Control")
            AutomationCommandGrid(
                commands = listOf(
                    AutoCmd("Flashlight ON", "টর্চ জ্বালাও", Icons.Default.FlashOn, JarvisGold),
                    AutoCmd("Flashlight OFF", "টর্চ বন্ধ করো", Icons.Default.FlashOff, Color(0xFF6B7280)),
                    AutoCmd("Volume UP", "ভলিউম বাড়াও", Icons.Default.VolumeUp, JarvisBlue),
                    AutoCmd("Volume DOWN", "ভলিউম কমাও", Icons.Default.VolumeDown, JarvisBlue),
                    AutoCmd("Silent Mode", "সাইলেন্ট করো", Icons.Default.VolumeOff, JarvisError),
                    AutoCmd("Vibrate", "ভাইব্রেট মোড", Icons.Default.Vibration, JarvisAccent)
                ),
                onCommandClick = { viewModel.processCommand(it.voiceCmd) }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

data class AutoCmd(val label: String, val voiceCmd: String, val icon: ImageVector, val color: Color)

@Composable
private fun AutomationCommandGrid(
    commands: List<AutoCmd>,
    onCommandClick: (AutoCmd) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        commands.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cmd ->
                    AutoCmdCard(cmd = cmd, onClick = { onCommandClick(cmd) }, modifier = Modifier.weight(1f))
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AutoCmdCard(cmd: AutoCmd, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.aspectRatio(1f).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cmd.color.copy(0.08f)),
        border = BorderStroke(1.dp, cmd.color.copy(0.2f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(cmd.icon, contentDescription = cmd.label, tint = cmd.color, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(cmd.label, fontSize = 11.sp, color = cmd.color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
