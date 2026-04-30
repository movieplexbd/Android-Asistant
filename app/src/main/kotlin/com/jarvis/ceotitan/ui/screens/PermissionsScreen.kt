package com.jarvis.ceotitan.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.jarvis.ceotitan.automation.accessibility.JarvisAccessibilityService
import com.jarvis.ceotitan.ui.components.JarvisTopBar
import com.jarvis.ceotitan.ui.theme.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(navController: NavController) {
    val context = LocalContext.current

    val criticalPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )

    val accessibilityEnabled = remember { mutableStateOf(JarvisAccessibilityService.isEnabled()) }

    LaunchedEffect(Unit) {
        accessibilityEnabled.value = JarvisAccessibilityService.isEnabled()
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEEF2FF), JarvisBackground)))) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JarvisTopBar(title = "Permissions", onBack = { navController.popBackStack() })

            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisBlue.copy(0.1f)),
                border = BorderStroke(1.dp, JarvisBlue.copy(0.2f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = JarvisBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("JARVIS সবচেয়ে ভালো কাজ করার জন্য নিচের পারমিশনগুলো প্রয়োজন।", fontSize = 13.sp, color = JarvisBlue)
                }
            }

            SectionTitle("Critical Permissions")

            PermissionItem(
                title = "Microphone",
                description = "ভয়েস কমান্ড শোনার জন্য",
                icon = Icons.Default.Mic,
                isGranted = criticalPermissions.permissions.any { it.permission == Manifest.permission.RECORD_AUDIO && it.status.isGranted },
                onRequest = { criticalPermissions.launchMultiplePermissionRequest() }
            )
            PermissionItem(
                title = "Camera",
                description = "ছবি তোলা ও OCR এর জন্য",
                icon = Icons.Default.Camera,
                isGranted = criticalPermissions.permissions.any { it.permission == Manifest.permission.CAMERA && it.status.isGranted },
                onRequest = { criticalPermissions.launchMultiplePermissionRequest() }
            )
            PermissionItem(
                title = "Contacts",
                description = "কল ও মেসেজের জন্য",
                icon = Icons.Default.Contacts,
                isGranted = criticalPermissions.permissions.any { it.permission == Manifest.permission.READ_CONTACTS && it.status.isGranted },
                onRequest = { criticalPermissions.launchMultiplePermissionRequest() }
            )
            PermissionItem(
                title = "Phone",
                description = "সরাসরি কল করার জন্য",
                icon = Icons.Default.Phone,
                isGranted = criticalPermissions.permissions.any { it.permission == Manifest.permission.CALL_PHONE && it.status.isGranted },
                onRequest = { criticalPermissions.launchMultiplePermissionRequest() }
            )
            PermissionItem(
                title = "Notifications",
                description = "JARVIS নোটিফিকেশন দেখানোর জন্য",
                icon = Icons.Default.Notifications,
                isGranted = criticalPermissions.permissions.any { it.permission == Manifest.permission.POST_NOTIFICATIONS && it.status.isGranted },
                onRequest = { criticalPermissions.launchMultiplePermissionRequest() }
            )

            SectionTitle("Special Permissions (Manual)")

            SpecialPermissionItem(
                title = "Accessibility Service",
                description = "WhatsApp, YouTube automation ও screen control এর জন্য",
                icon = Icons.Default.Accessibility,
                isGranted = accessibilityEnabled.value,
                onOpen = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )
            SpecialPermissionItem(
                title = "Notification Listener",
                description = "নোটিফিকেশন পড়ার জন্য",
                icon = Icons.Default.NotificationsActive,
                isGranted = false,
                onOpen = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )
            SpecialPermissionItem(
                title = "Battery Optimization",
                description = "সবসময় চলার জন্য battery restriction বন্ধ রাখুন",
                icon = Icons.Default.Battery5Bar,
                isGranted = false,
                onOpen = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    }
                }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (isGranted) JarvisSuccess.copy(0.2f) else JarvisError.copy(0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isGranted) JarvisSuccess else JarvisBlue, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1C2E))
                Text(description, fontSize = 12.sp, color = Color(0xFF6B7280))
            }
            if (isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisSuccess, modifier = Modifier.size(24.dp))
            } else {
                TextButton(onClick = onRequest) { Text("Allow", color = JarvisBlue, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun SpecialPermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (isGranted) JarvisSuccess.copy(0.2f) else JarvisGold.copy(0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isGranted) JarvisSuccess else JarvisGold, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1C2E))
                Text(description, fontSize = 12.sp, color = Color(0xFF6B7280))
            }
            if (isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = JarvisSuccess, modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = onOpen, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = JarvisGold)) {
                    Text("Open", fontSize = 12.sp)
                }
            }
        }
    }
}
