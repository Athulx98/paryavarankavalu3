package com.paryavarankavalu.paryavarankavalu.uii.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.model.UserProfile
import com.paryavarankavalu.paryavarankavalu.ui.theme.*
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        } else {
            viewModel.startLocationTracking(context)
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            viewModel.startLocationTracking(context)
        }
    }
    val userProfile by viewModel.userProfile.collectAsState()
    val reports by viewModel.reports.collectAsState()

    // Filter reports for different sections
    val myTasks = reports.filter { it.cleanerId == userProfile?.uid && it.status == "Assigned" }
    val recentReports = reports.sortedByDescending { it.timestamp }

    Scaffold(
        containerColor = Sage50
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header Section
            item {
                HomeHeader(userProfile)
            }

            // Stats Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Eco Karma",
                        value = "${userProfile?.ecoKarma ?: 0}",
                        icon = Icons.Default.Star,
                        containerColor = GreenLight,
                        contentColor = Forest900
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Reports",
                        value = "${userProfile?.reportsCount ?: 0}",
                        icon = Icons.AutoMirrored.Filled.List,
                        containerColor = Color(0xFFFEE2E2), // RedLight
                        contentColor = Color(0xFF991B1B)
                    )
                }
            }

            // Your Tasks Section (Only for Volunteers with assigned tasks)
            if (userProfile?.role == "Volunteer" && myTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Active Tasks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Forest900,
                        modifier = Modifier.padding(24.dp, top = 32.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(myTasks) { report ->
                            TaskSmallCard(report) {
                                navController.navigate("report?reportId=${report.id}")
                            }
                        }
                    }
                }
            }

            // Smart GeoZone Card
            item {
                GeoZoneCard(userProfile?.assignedRegion ?: "Detecting Zone...")
            }

            // Recent Reports Section
            item {
                Text(
                    text = "Nearby Issues",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Forest900,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            if (recentReports.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                        color = Color.White,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            "Everything looks clean around you! 🌿",
                            modifier = Modifier.padding(32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = Sage400
                        )
                    }
                }
            }

            items(recentReports) { report ->
                Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    ReportCard(
                        report = report,
                        userProfile = userProfile,
                        onBookClick = { id -> viewModel.bookCleanup(id) },
                        onUploadProof = { id -> navController.navigate("report?reportId=$id") },
                        onCardClick = { 
                            viewModel.setSelectedReportId(report.id)
                            navController.navigate("map")
                        }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun TaskSmallCard(report: Report, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val imgModel = if (report.photoUrl.startsWith("data:image")) {
                android.util.Base64.decode(report.photoUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
            } else report.photoUrl
            AsyncImage(
                model = imgModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                report.wasteType.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = GreenPrimary
            )
            Text(
                "Cleanup in ${report.region.ifBlank { "Area" }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Forest900,
                maxLines = 1
            )
        }
    }
}

@Composable
fun HomeHeader(profile: UserProfile?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp, top = 32.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Hi, ${profile?.displayName?.split(" ")?.firstOrNull() ?: "Alex"}",
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = Forest900,
                letterSpacing = (-1).sp
            )
            Text(
                text = "Let's clean our world today.",
                fontSize = 14.sp,
                color = Sage400
            )
        }
        
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = profile?.displayName?.firstOrNull()?.toString() ?: "A",
                    fontWeight = FontWeight.Bold,
                    color = GreenPrimary,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(24.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor.copy(alpha = 0.6f))
            }
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = contentColor)
        }
    }
}

@Composable
fun GeoZoneCard(region: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        color = Color.White,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Sage100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GreenPrimary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Your Zone", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Sage400, letterSpacing = 1.sp)
                Text(region, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Forest900)
            }
        }
    }
}

@Composable
fun ReportCard(
    report: Report, 
    userProfile: UserProfile?, 
    onBookClick: (String) -> Unit,
    onUploadProof: (String) -> Unit = {},
    onCardClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(90.dp),
                shape = RoundedCornerShape(16.dp),
                color = Sage50
            ) {
                val rawUrl = if (report.status == "Cleaned") report.cleanedPhotoUrl ?: "" else report.photoUrl
                val imgModel = if (rawUrl.startsWith("data:image")) {
                    android.util.Base64.decode(rawUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                } else rawUrl
                AsyncImage(
                    model = imgModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = report.wasteType.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = Forest900,
                        letterSpacing = 1.sp
                    )
                    StatusBadge(report.status)
                }
                
                Text(
                    text = "Litter in ${report.region.ifBlank { "Unknown Area" }}",
                    fontSize = 14.sp,
                    color = Forest900,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(report.timestamp))
                    Text(text = date, fontSize = 10.sp, color = Sage400, fontWeight = FontWeight.Medium)
                    
                    if (userProfile?.role == "Volunteer") {
                        when (report.status) {
                            "Reported" -> {
                                Button(
                                    onClick = { onBookClick(report.id) },
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                                ) {
                                    Text("TAKE CHARGE", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            "Assigned" -> {
                                if (report.cleanerId == userProfile.uid) {
                                    Button(
                                        onClick = { onUploadProof(report.id) },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Forest900)
                                    ) {
                                        Text("UPLOAD PROOF", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "Reported" -> StatusReported
        "Assigned" -> StatusAssigned
        "Cleaned" -> StatusCleaned
        else -> Color.Gray
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(100.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
