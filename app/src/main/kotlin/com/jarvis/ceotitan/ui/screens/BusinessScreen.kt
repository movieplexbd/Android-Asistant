package com.jarvis.ceotitan.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jarvis.ceotitan.database.dao.BusinessNoteDao
import com.jarvis.ceotitan.database.entities.BusinessNoteEntity
import com.jarvis.ceotitan.ui.components.JarvisTopBar
import com.jarvis.ceotitan.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BusinessViewModel @Inject constructor(
    private val businessNoteDao: BusinessNoteDao
) : ViewModel() {
    val notes: StateFlow<List<BusinessNoteEntity>> = businessNoteDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(title: String, content: String, category: String = "fan-zon") {
        viewModelScope.launch {
            businessNoteDao.insert(BusinessNoteEntity(title = title, content = content, category = category))
        }
    }

    fun deleteNote(note: BusinessNoteEntity) {
        viewModelScope.launch { businessNoteDao.delete(note) }
    }
}

@Composable
fun BusinessScreen(
    navController: NavController,
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), JarvisBackground)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JarvisBlue)
                }
                Text("Business CEO Mode", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisGold.copy(0.1f)),
                border = BorderStroke(1.dp, JarvisGold.copy(0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BusinessCenter, contentDescription = null, tint = JarvisGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("FAN-ZON Business Hub", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = JarvisGold)
                        Text("আপনার ব্যবসার সব নোট একসাথে", fontSize = 12.sp, color = JarvisGold.copy(0.8f))
                    }
                }
            }

            val quickCommands = listOf(
                "FAN-ZON আইডিয়া সেভ করো",
                "সাপ্লায়ার নোট যোগ করো",
                "কাস্টমার ফলোআপ রিমাইন্ড করো",
                "লোগো ট্রেন্ড খোঁজো",
                "বিক্রয় নোট লিখো"
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("দ্রুত কমান্ড:", fontSize = 13.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickCommands.size) { i ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(quickCommands[i], fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = JarvisGold.copy(0.1f), labelColor = JarvisGold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (notes.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, tint = JarvisGold.copy(0.3f), modifier = Modifier.size(80.dp))
                        Text("কোনো নোট নেই", fontSize = 16.sp, color = Color(0xFF6B7280))
                        Text("+বাটনে চাপুন বা ভয়েস দিন", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes.size) { i ->
                        BusinessNoteCard(note = notes[i], onDelete = { viewModel.deleteNote(notes[i]) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = JarvisGold,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Note")
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("নতুন ব্যবসা নোট", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = titleInput, onValueChange = { titleInput = it }, label = { Text("শিরোনাম") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = contentInput, onValueChange = { contentInput = it }, label = { Text("বিবরণ") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            viewModel.addNote(titleInput, contentInput)
                            titleInput = ""; contentInput = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisGold)
                ) { Text("সেভ করো") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun BusinessNoteCard(note: BusinessNoteEntity, onDelete: () -> Unit) {
    val timeFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, JarvisGold.copy(0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Note, contentDescription = null, tint = JarvisGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(note.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JarvisError.copy(0.6f), modifier = Modifier.size(16.dp))
                }
            }
            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(note.content, fontSize = 13.sp, color = Color(0xFF6B7280))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(timeFormat.format(Date(note.timestamp)), fontSize = 10.sp, color = Color(0xFF9CA3AF))
        }
    }
}
