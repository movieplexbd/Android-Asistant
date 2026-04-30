package com.jarvis.ceotitan.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.jarvis.ceotitan.database.dao.InteractionDao
import com.jarvis.ceotitan.database.entities.InteractionEntity
import com.jarvis.ceotitan.memory.MemoryManager
import com.jarvis.ceotitan.ui.components.GlassCard
import com.jarvis.ceotitan.ui.components.JarvisTopBar
import com.jarvis.ceotitan.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryManager: MemoryManager,
    private val interactionDao: InteractionDao
) : ViewModel() {
    val interactions: StateFlow<List<InteractionEntity>> = interactionDao.getRecent(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() { viewModelScope.launch { memoryManager.deleteAllMemory() } }
}

@Composable
fun MemoryScreen(
    navController: NavController,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val interactions by viewModel.interactions.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), JarvisBackground)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JarvisBlue)
                }
                Text("Memory & Cache", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.clearAll() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = JarvisError)
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    MemoryStat("Total", "${interactions.size}", Icons.Default.Memory, JarvisBlue)
                    MemoryStat("Today", "${interactions.count { System.currentTimeMillis() - it.timestamp < 86400000L }}", Icons.Default.Today, JarvisSuccess)
                    MemoryStat("Cloud", "${interactions.count { it.brainLayer == "CLOUD" }}", Icons.Default.Cloud, JarvisAccent)
                    MemoryStat("Cache", "${interactions.count { it.brainLayer == "CACHE" }}", Icons.Default.Speed, JarvisGold)
                }
            }

            if (interactions.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = JarvisBlue.copy(0.3f), modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("কোনো মেমোরি নেই", fontSize = 16.sp, color = Color(0xFF6B7280))
                        Text("কমান্ড দিন, JARVIS মনে রাখবে", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(interactions) { item ->
                        InteractionCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = color.copy(0.7f))
    }
}

@Composable
private fun InteractionCard(interaction: InteractionEntity) {
    val timeFormat = SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault())
    val layerColor = when (interaction.brainLayer) {
        "CLOUD" -> JarvisAccent
        "CACHE" -> JarvisGold
        else -> JarvisSuccess
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, layerColor.copy(0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = JarvisBlue, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(interaction.command, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1C2E), modifier = Modifier.weight(1f))
                Box(modifier = Modifier.background(layerColor.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(interaction.brainLayer, fontSize = 9.sp, color = layerColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(interaction.response, fontSize = 12.sp, color = Color(0xFF6B7280))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(timeFormat.format(Date(interaction.timestamp)), fontSize = 10.sp, color = Color(0xFF9CA3AF))
        }
    }
}
