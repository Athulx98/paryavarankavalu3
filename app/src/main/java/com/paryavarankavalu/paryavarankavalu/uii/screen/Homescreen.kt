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
import androidx.compose.runtime.*
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
import androidx.navigation.NavGraph.Companion.findStartDestination
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

    // Filter reports: Assigned to me vs Others (Nearby)
    val assignedReports = remember(reports, userProfile) {
        reports.filter { it.assignedTo == userProfile?.uid && it.status == "Assigned" }
            .sortedByDescending { it.timestamp }
    }
    
    val nearbyReports = remember(reports, assignedReports) {
        // Exclude reports already shown in the "Assigned" section to avoid duplication
        reports.filter { report -> 
            assignedReports.none { it.id == report.id } 
        }.sortedByDescending { it.timestamp }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
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
                        containerColor = SecondaryContainer,
                        contentColor = OnBackground
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Reports",
                        value = "${userProfile?.reportsCount ?: 0}",
                        icon = Icons.AutoMirrored.Filled.List,
                        containerColor = TertiaryContainer,
                        contentColor = OnBackground
                    )
                }
            }

            // Smart GeoZone Card
            item {
                GeoZoneCard(userProfile?.assignedRegion ?: "Detecting Zone...")
            }

            // My Assigned Work Section (Priority 1)
            if (assignedReports.isNotEmpty()) {
                item {
                    Text(
                        text = "My Assigned Work",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                
                items(assignedReports) { report ->
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        ReportCard(
                            report = report,
                            userProfile = userProfile,
                            onBookClick = { id -> viewModel.bookCleanup(id) },
                            onUploadProof = { id -> navController.navigate("report?reportId=$id") },
                            onCardClick = { 
                                viewModel.setSelectedReportId(report.id)
                                navController.navigate("map") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onDelete = { reportId -> viewModel.deleteReport(reportId) },
                            onViewDetails = { reportId ->
                                viewModel.setSelectedReportId(reportId)
                                navController.navigate("map")
                            }
                        )
                    }
                }
            }

            // Nearby Issues Section (Priority 2)
            item {
                Text(
                    text = "Nearby Issues",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            if (nearbyReports.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            "Everything looks clean around you! 🌿",
                            modifier = Modifier.padding(32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(nearbyReports) { report ->
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        ReportCard(
                            report = report,
                            userProfile = userProfile,
                            onBookClick = { id -> viewModel.bookCleanup(id) },
                            onUploadProof = { id -> navController.navigate("report?reportId=$id") },
                            onCardClick = { 
                                viewModel.setSelectedReportId(report.id)
                                navController.navigate("map") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onDelete = { reportId -> viewModel.deleteReport(reportId) },
                            onViewDetails = { reportId ->
                                viewModel.setSelectedReportId(reportId)
                                navController.navigate("map")
                            }
                        )
                    }
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp
    ) {
        val imgState = remember(report.photoUrl) { mutableStateOf<Any?>(null) }
        LaunchedEffect(report.photoUrl) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                imgState.value = if (report.photoUrl.startsWith("data:image")) {
                    android.util.Base64.decode(report.photoUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                } else report.photoUrl
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = imgState.value,
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
                color = GreenPrimary   // #006E2F
            )
            Text(
                "Cleanup in ${report.region.ifBlank { "Area" }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.paryavarankavalu.paryavarankavalu.R.drawable.app_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Hi, ${profile?.displayName?.split(" ")?.firstOrNull() ?: "Alex"}",
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-1).sp
            )
            Text(
                text = "Let's clean our world today.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Avatar pill — Stitch: 8dp rounded pill with surface-container-lowest
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
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
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = contentColor.copy(alpha = 0.7f))
            }
            Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = contentColor)
        }
    }
}

@Composable
fun GeoZoneCard(region: String) {
    // GeoZone: pure white card, Level 1 elevation, 24dp corners, 20dp margin
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),  // #EDF6EA
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GreenPrimary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "Your Zone",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(region, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
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
    onCardClick: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onViewDetails: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), // white
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
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                val rawUrl = if (report.status == "Cleaned") report.cleanedPhotoUrl ?: "" else report.photoUrl
                val imgState = remember(rawUrl) { mutableStateOf<Any?>(null) }
                LaunchedEffect(rawUrl) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        imgState.value = if (rawUrl.startsWith("data:image")) {
                            android.util.Base64.decode(rawUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                        } else rawUrl
                    }
                }
                AsyncImage(
                    model = imgState.value,
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = report.wasteType.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 1.sp
                        )
                        StatusBadge(report.status)
                    }
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp).padding(0.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("👁️", fontSize = 14.sp)
                                        Text("View Details", fontSize = 13.sp)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    onViewDetails(report.id)
                                }
                            )
                            if (report.status == "Cleaned") {
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🗑️", fontSize = 14.sp)
                                            Text(
                                                "Delete", 
                                                fontSize = 13.sp,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDelete(report.id)
                                    }
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = "Litter in ${report.region.ifBlank { "Unknown Area" }}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
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
                    Text(text = date, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    
                    if (userProfile?.role == "Volunteer") {
                        when (report.status) {
                            "Reported" -> {
                                Button(
                                    onClick = { onBookClick(report.id) },
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(999.dp),   // Stitch pill shape
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
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
                                        shape = RoundedCornerShape(999.dp),   // Stitch pill shape
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OnBackground)
                                    ) {
                                        Text("VERIFY CLEANUP", fontSize = 9.sp, fontWeight = FontWeight.Black)
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
