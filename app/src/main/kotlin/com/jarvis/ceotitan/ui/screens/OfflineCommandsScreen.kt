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
import com.jarvis.ceotitan.ui.components.JarvisTopBar
import com.jarvis.ceotitan.ui.theme.*

data class OfflineCmd(val label: String, val bangla: String, val icon: ImageVector, val color: Color, val category: String)

@Composable
fun OfflineCommandsScreen(
    navController: NavController,
    viewModel: MainBrainViewModel = hiltViewModel()
) {
    val allCommands = listOf(
        OfflineCmd("Flashlight ON", "টর্চ জ্বালাও", Icons.Default.FlashOn, JarvisGold, "phone"),
        OfflineCmd("Flashlight OFF", "টর্চ বন্ধ", Icons.Default.FlashOff, Color(0xFF6B7280), "phone"),
        OfflineCmd("Volume UP", "ভলিউম বাড়াও", Icons.Default.VolumeUp, JarvisBlue, "phone"),
        OfflineCmd("Volume DOWN", "ভলিউম কমাও", Icons.Default.VolumeDown, JarvisBlue, "phone"),
        OfflineCmd("Silent Mode", "সাইলেন্ট", Icons.Default.VolumeOff, JarvisError, "phone"),
        OfflineCmd("Vibrate", "ভাইব্রেট", Icons.Default.Vibration, JarvisAccent, "phone"),
        OfflineCmd("Go HOME", "হোমে যাও", Icons.Default.Home, JarvisSuccess, "navigate"),
        OfflineCmd("Go BACK", "ব্যাক", Icons.Default.ArrowBack, Color(0xFF6B7280), "navigate"),
        OfflineCmd("Recent Apps", "সাম্প্রতিক", Icons.Default.GridView, JarvisAccent, "navigate"),
        OfflineCmd("Scroll DOWN", "নিচে নামাও", Icons.Default.KeyboardArrowDown, JarvisBlue, "navigate"),
        OfflineCmd("Scroll UP", "উপরে যাও", Icons.Default.KeyboardArrowUp, JarvisBlue, "navigate"),
        OfflineCmd("Open Camera", "ক্যামেরা খোলো", Icons.Default.Camera, Color(0xFF7C3AED), "apps"),
        OfflineCmd("Take Photo", "ছবি তোলো", Icons.Default.PhotoCamera, Color(0xFF7C3AED), "apps"),
        OfflineCmd("Open YouTube", "ইউটিউব খোলো", Icons.Default.PlayCircle, JarvisError, "apps"),
        OfflineCmd("Open WhatsApp", "WhatsApp খোলো", Icons.Default.Message, JarvisSuccess, "apps"),
        OfflineCmd("Open Maps", "Maps খোলো", Icons.Default.Map, JarvisBlue, "apps"),
        OfflineCmd("Set Alarm", "অ্যালার্ম দাও", Icons.Default.Alarm, JarvisGold, "utility"),
        OfflineCmd("Set Timer", "টাইমার দাও", Icons.Default.Timer, JarvisGold, "utility"),
        OfflineCmd("Time & Date", "সময় ও তারিখ", Icons.Default.AccessTime, JarvisAccent, "utility"),
        OfflineCmd("Open WiFi", "WiFi সেটিংস", Icons.Default.Wifi, JarvisBlue, "settings"),
        OfflineCmd("Open Bluetooth", "ব্লুটুথ সেটিংস", Icons.Default.Bluetooth, JarvisBlue, "settings"),
        OfflineCmd("Brightness", "ব্রাইটনেস", Icons.Default.Brightness5, JarvisGold, "settings"),
        OfflineCmd("Lock Screen", "স্ক্রিন লক", Icons.Default.Lock, JarvisError, "settings")
    )

    val categories = listOf("phone" to "ফোন কন্ট্রোল", "navigate" to "নেভিগেশন", "apps" to "অ্যাপ খোলো", "utility" to "ইউটিলিটি", "settings" to "সেটিংস")

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), JarvisBackground)))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JarvisTopBar(title = "Offline Commands", onBack = { navController.popBackStack() })

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisSuccess.copy(0.1f)),
                border = BorderStroke(1.dp, JarvisSuccess.copy(0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = JarvisSuccess, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("এই কমান্ডগুলো ইন্টারনেট ছাড়াই কাজ করে", fontSize = 13.sp, color = JarvisSuccess, fontWeight = FontWeight.Medium)
                }
            }

            categories.forEach { (catKey, catName) ->
                val catCmds = allCommands.filter { it.category == catKey }
                if (catCmds.isNotEmpty()) {
                    Text(catName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = JarvisBlue, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    catCmds.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { cmd ->
                                OfflineCmdCard(cmd = cmd, onClick = { viewModel.processCommand(cmd.bangla) }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun OfflineCmdCard(cmd: OfflineCmd, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(80.dp).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cmd.color.copy(0.07f)),
        border = BorderStroke(1.dp, cmd.color.copy(0.2f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(cmd.icon, contentDescription = cmd.label, tint = cmd.color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(cmd.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cmd.color, maxLines = 1)
                Text(cmd.bangla, fontSize = 11.sp, color = cmd.color.copy(0.7f), maxLines = 1)
            }
        }
    }
}
