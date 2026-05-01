package com.paryavarankavalu.paryavarankavalu.uii.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.paryavarankavalu.paryavarankavalu.ui.theme.GreenPrimary
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val userProfile by viewModel.userProfile.collectAsState()
    
    // Local state for UI responsiveness, initialized from profile
    var pushEnabled by remember(userProfile) { mutableStateOf(userProfile?.pushNotificationsEnabled ?: true) }
    var soundOption by remember(userProfile) { mutableStateOf(userProfile?.notificationSound ?: "Default") }
    var vibrationEnabled by remember(userProfile) { mutableStateOf(userProfile?.vibrationEnabled ?: true) }

    val sounds = listOf("Default", "Nature", "Forest", "Bird Chirp")
    var soundExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GreenPrimary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Alert Preferences", fontWeight = FontWeight.Black, fontSize = 18.sp, color = GreenPrimary)
                        Text("Stay updated on environmental reports", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Toggle
            SettingsToggleItem(
                title = "Push Notifications",
                description = "Receive alerts for new waste reports nearby",
                checked = pushEnabled,
                onCheckedChange = { 
                    pushEnabled = it
                    viewModel.updateNotificationSettings(it, soundOption, vibrationEnabled)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Sound Dropdown
            Column {
                Text("Notification Sound", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { soundExpanded = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(soundOption, fontSize = 16.sp)
                            Text("▼", fontSize = 12.sp, color = GreenPrimary)
                        }
                    }
                    DropdownMenu(
                        expanded = soundExpanded,
                        onDismissRequest = { soundExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        sounds.forEach { sound ->
                            DropdownMenuItem(
                                text = { Text(sound) },
                                onClick = {
                                    soundOption = sound
                                    soundExpanded = false
                                    viewModel.updateNotificationSettings(pushEnabled, sound, vibrationEnabled)
                                }
                            )
                        }
                    }
                }
            }

            // Vibration Toggle
            SettingsToggleItem(
                title = "Vibration",
                description = "Vibrate device for incoming alerts",
                checked = vibrationEnabled,
                onCheckedChange = { 
                    vibrationEnabled = it
                    viewModel.updateNotificationSettings(pushEnabled, soundOption, it)
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                "System Settings",
                modifier = Modifier.fillMaxWidth().clickable { /* Intent to system settings */ },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp,
                color = GreenPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GreenPrimary
            )
        )
    }
}
