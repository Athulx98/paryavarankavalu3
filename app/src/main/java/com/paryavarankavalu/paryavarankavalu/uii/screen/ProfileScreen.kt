package com.paryavarankavalu.paryavarankavalu.uii.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.firebase.auth.FirebaseAuth
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.model.UserProfile
import com.paryavarankavalu.paryavarankavalu.ui.theme.*
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel

@Composable
fun ProfileScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val userProfile by viewModel.userProfile.collectAsState()
    val reports by viewModel.reports.collectAsState()
    
    // Filter reports to only show those made by the current user with memoization
    val myReports = remember(reports, userProfile) {
        reports.filter { it.reporterId == (userProfile?.uid ?: "") }
    }

    val levelInfo = getLevelInfo(userProfile?.ecoKarma ?: 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Sage50)
    ) {
        // Top Profile Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenPrimary)
                .padding(24.dp, top = 48.dp, bottom = 48.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            userProfile?.displayName?.firstOrNull()?.toString() ?: "A",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = userProfile?.displayName ?: "Alex Reginald",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "${userProfile?.role?.uppercase()} | ${levelInfo.tier.uppercase()}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-24).dp)
                .background(Sage50, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(horizontal = 24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(32.dp)) }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProfileStatSmall(Modifier.weight(1f), "Karma", "${userProfile?.ecoKarma ?: 0}", GreenPrimary)
                    ProfileStatSmall(Modifier.weight(1f), "Rank", levelInfo.rank, Color(0xFFF59E0B))
                }
            }

            // Streak Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(Color(0xFFFFF7ED), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF97316))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("${userProfile?.streak ?: 0} Day Streak", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Forest900)
                            Text("Keep active to grow your streak!", fontSize = 12.sp, color = Sage400)
                        }
                    }
                }
            }

            // Role Toggle Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Forest900,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (userProfile?.role == "Volunteer") "You are a Volunteer" else "Become a Volunteer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (userProfile?.role == "Volunteer") "Help clean reported hotspots" else "Take charge of cleaning your city",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = userProfile?.role == "Volunteer",
                            onCheckedChange = { isChecked ->
                                viewModel.updateRole(if (isChecked) "Volunteer" else "Citizen")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GreenPrimary,
                                checkedTrackColor = GreenLight,
                                uncheckedThumbColor = Sage400,
                                uncheckedTrackColor = Sage100
                            )
                        )
                    }
                }
            }

            item {
                Text(
                    text = "My Contribution",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Forest900,
                    modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                )
            }

            if (myReports.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            "You haven't reported any litter yet. Start making an impact!",
                            modifier = Modifier.padding(24.dp),
                            color = Sage400,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            items(myReports) { report ->
                Box(Modifier.padding(vertical = 8.dp)) {
                    ReportCard(
                        report = report,
                        userProfile = userProfile,
                        onBookClick = {},
                        onCardClick = { 
                            viewModel.setSelectedReportId(report.id)
                            navController.navigate("map") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                ProfileMenuItem("Settings", Icons.Default.Settings)
                ProfileMenuItem("Help & Feedback", Icons.Default.Info)
                ProfileMenuItem("Logout", Icons.AutoMirrored.Filled.ExitToApp, color = Color.Red) {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("auth") {
                        popUpTo(0)
                    }
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun ProfileStatSmall(modifier: Modifier, label: String, value: String, color: Color) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = Sage400, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Forest900)
        }
    }
}

@Composable
fun ProfileMenuItem(label: String, icon: ImageVector, color: Color = Forest900, onClick: () -> Unit = {}) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).background(Sage50, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Sage400, modifier = Modifier.size(16.dp))
        }
    }
}

data class LevelInfo(val tier: String, val rank: String)

fun getLevelInfo(points: Int): LevelInfo {
    return when {
        points < 100 -> LevelInfo("Seedling", "Rookie")
        points < 300 -> LevelInfo("Sapling", "Defender")
        points < 1000 -> LevelInfo("Tree", "Guardian")
        else -> LevelInfo("Forest", "Master")
    }
}
