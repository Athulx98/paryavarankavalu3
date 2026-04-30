package com.paryavarankavalu.paryavarankavalu.uii.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.util.LocationHelper
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel
import com.paryavarankavalu.paryavarankavalu.ui.theme.GreenPrimary
import com.paryavarankavalu.paryavarankavalu.ui.theme.OnGreenPrimary
import com.paryavarankavalu.paryavarankavalu.ui.theme.SecondaryContainer
import com.paryavarankavalu.paryavarankavalu.ui.theme.SurfaceContainerLowest
import com.paryavarankavalu.paryavarankavalu.ui.theme.OnBackground
import com.paryavarankavalu.paryavarankavalu.ui.theme.OnSurfaceVariant
import com.paryavarankavalu.paryavarankavalu.service.LocationUtils
import com.paryavarankavalu.paryavarankavalu.service.Zone
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel()
) {
    val reports by viewModel.reports.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val nearestZone by viewModel.nearestZone.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedReportId by viewModel.selectedReportId.collectAsState()
    
    var selectedReport by remember { mutableStateOf<Report?>(null) }
    var showHeatmap by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf("All") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showZoneSummary by remember { mutableStateOf(false) }
    var selectedZone by remember { mutableStateOf<Zone?>(null) }
    var isSatelliteMode by remember { mutableStateOf(false) }
    val alertedReportIds = remember { mutableSetOf<String>() }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val auth = FirebaseAuth.getInstance()
    
    var isMapLoaded by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(11.2588, 75.7804), 12f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            viewModel.startLocationTracking(context)
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    "Location permission denied. Enable in Settings.",
                    actionLabel = "SETTINGS"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (fine == PackageManager.PERMISSION_GRANTED) {
            viewModel.startLocationTracking(context)
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    var hasCenteredOnUser by remember { mutableStateOf(false) }

    LaunchedEffect(userLocation, isMapLoaded) {
        if (!hasCenteredOnUser && userLocation != null && isMapLoaded && selectedReport == null && selectedZone == null && selectedReportId == null) {
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(userLocation!!, 14f)
                )
                hasCenteredOnUser = true
            } catch (e: Exception) {
                // Ignore if animation fails
            }
        }
    }

    LaunchedEffect(selectedReportId, reports, isMapLoaded) {
        if (selectedReportId != null && reports.isNotEmpty()) {
            val report = reports.find { it.id == selectedReportId }
            if (report != null) {
                selectedReport = report
                if (isMapLoaded) {
                    try {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(report.latitude, report.longitude), 16f)
                        )
                        viewModel.setSelectedReportId(null)
                    } catch (e: Exception) {}
                }
            }
        }
    }

    // Proximity alert system
    LaunchedEffect(userLocation, reports) {
        if (userLocation == null) return@LaunchedEffect
        reports.forEach { report ->
            if (report.status != "Reported") return@forEach
            if (report.id in alertedReportIds) return@forEach
            
            val distKm = LocationUtils.getDistanceInKm(
                userLocation!!.latitude, userLocation!!.longitude,
                report.latitude, report.longitude
            )
            
            if (distKm <= 0.2) {
                val dist = LocationHelper.formatDistance(distKm)
                alertedReportIds.add(report.id)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "⚠️ Waste blackspot $dist away!",
                        actionLabel = "VIEW",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        selectedReport = report
                    }
                }
            }
        }
    }

    LaunchedEffect(error) {
        error?.let { err ->
            val result = snackbarHostState.showSnackbar(
                message = err,
                actionLabel = "RETRY",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Retry action would go here
            }
        }
    }

    val filteredReports = remember(reports, filterStatus) {
        reports.filter { report ->
            when (filterStatus) {
                "All" -> true
                "My Reports Only" -> report.reporterId == auth.currentUser?.uid
                else -> {
                    // If filterStatus matches a zone name, show only reports in that zone
                    val matchedZone = LocationUtils.SMART_ZONES.find { it.name == filterStatus }
                    if (matchedZone != null) {
                        LocationUtils.getDistanceInKm(report.latitude, report.longitude, matchedZone.latitude, matchedZone.longitude) <= 2.0
                    } else {
                        // It's a status
                        report.status == filterStatus.replace(Regex(".*Only|[^A-Za-z]"), "").trim() || report.status == filterStatus
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false,
                mapType = if (isSatelliteMode) MapType.SATELLITE else MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false
            ),
            onMapLoaded = { isMapLoaded = true }
        ) {
            filteredReports.forEach { report ->
                val markerColor = when(report.status) {
                    "Reported" -> BitmapDescriptorFactory.HUE_RED
                    "Assigned" -> BitmapDescriptorFactory.HUE_ORANGE
                    "Cleaned"  -> BitmapDescriptorFactory.HUE_GREEN
                    else       -> BitmapDescriptorFactory.HUE_AZURE
                }
                Marker(
                    state = MarkerState(LatLng(report.latitude, report.longitude)),
                    icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                    title = report.wasteType,
                    snippet = "Status: ${report.status}",
                    onClick = { 
                        selectedReport = report
                        true 
                    }
                )
            }

            userLocation?.let {
                Circle(
                    center = it,
                    radius = 30.0,
                    fillColor = Color(0x220000FF),
                    strokeColor = Color(0xFF2563EB),
                    strokeWidth = 3f
                )
            }

            if (showHeatmap) {
                LocationUtils.SMART_ZONES.forEach { zone ->
                    val count = reports.count { r ->
                        LocationUtils.getDistanceInKm(
                            r.latitude, r.longitude,
                            zone.latitude, zone.longitude
                        ) <= 2.0
                    }
                    Circle(
                        center = LatLng(zone.latitude, zone.longitude),
                        radius = 2000.0,
                        fillColor = when {
                            count >= 11 -> Color(0x88EF4444)
                            count >= 6  -> Color(0x88F97316)
                            count >= 3  -> Color(0x88EAB308)
                            else        -> Color(0x8822C55E)
                        },
                        strokeColor = Color.Transparent,
                        clickable = true,
                        onClick = {
                            selectedZone = zone
                            showZoneSummary = true
                        }
                    )
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Top Search + Filter Bar — Stitch: white card, 24dp pill, 20dp margin, Level 1 shadow
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp) // Stitch: 20dp margin, 24dp top
                .fillMaxWidth(),
            shape = RoundedCornerShape(999.dp), // Fully pill-shaped
            shadowElevation = 4.dp,
            color = SurfaceContainerLowest.copy(alpha = 0.95f) // Subtle glassmorphism
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nearestZone?.name ?: "Paryavaran Kavalu",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = OnBackground
                    )
                    Text(
                        text = "Viewing ${filterStatus} hotspots",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                }
                IconButton(onClick = { showHeatmap = !showHeatmap }) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = "Heatmap",
                        tint = if (showHeatmap) Color(0xFFEF4444) else OnSurfaceVariant
                    )
                }
                Box(modifier = Modifier.size(1.dp, 24.dp).background(OnSurfaceVariant.copy(alpha = 0.1f)))
                IconButton(onClick = { isSatelliteMode = !isSatelliteMode }) {
                    Icon(
                        if (isSatelliteMode) Icons.Default.Map else Icons.Default.Public,
                        contentDescription = "Toggle Map Type",
                        tint = if (isSatelliteMode) GreenPrimary else OnSurfaceVariant
                    )
                }
                Box(modifier = Modifier.size(1.dp, 24.dp).background(OnSurfaceVariant.copy(alpha = 0.1f)))
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(Icons.Default.Tune, contentDescription = "Filter", tint = GreenPrimary)
                }
            }
        }

        // FAB Column — Stitch: Level 2 shadow, circular
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FloatingActionButton(
                onClick = { viewModel.getOneTimeLocation(context) },
                containerColor = SurfaceContainerLowest,
                contentColor = GreenPrimary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
            }
            FloatingActionButton(
                onClick = {
                    if (selectedReport == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Tap a report marker first")
                        }
                    } else {
                        val selected = selectedReport!!
                        val uri = Uri.parse("google.navigation:q=${selected.latitude},${selected.longitude}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            val fallbackUri = Uri.parse("geo:${selected.latitude},${selected.longitude}?q=${selected.latitude},${selected.longitude}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
                        }
                    }
                },
                containerColor = GreenPrimary,   // #006E2F
                contentColor = OnGreenPrimary     // #FFFFFF
            ) {
                Icon(Icons.Default.Navigation, contentDescription = "Navigate")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )

        // Filter Bottom Sheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false }
            ) {
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                    Text(
                        "Filter Reports",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = OnBackground,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    val filters = listOf(
                        "All",
                        "Reported",
                        "Assigned",
                        "Cleaned",
                        "My Reports Only"
                    )
                    val displayFilters = listOf(
                        "All Reports",
                        "🔴 Reported Only",
                        "🟠 Assigned Only",
                        "🟢 Cleaned Only",
                        "👤 My Reports Only"
                    )
                    
                    filters.forEachIndexed { index, filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    filterStatus = filter
                                    showFilterSheet = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = filterStatus == filter,
                                onClick = {
                                    filterStatus = filter
                                    showFilterSheet = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(displayFilters[index])
                        }
                    }
                }
            }
        }

        // Report Detail Bottom Sheet
        if (selectedReport != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedReport = null }
            ) {
                ReportDetailBottomSheet(
                    report = selectedReport!!,
                    userLocation = userLocation,
                    role = userProfile?.role ?: "Citizen",
                    uid = auth.currentUser?.uid ?: "",
                    onNavigate = { lat, lng ->
                        val uri = Uri.parse("google.navigation:q=$lat,$lng")
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            // Fallback to generic map view
                            val fallbackUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
                        }
                    },
                    onTakeCharge = { id -> viewModel.bookCleanup(id) },
                    onCompleteCleanup = { id, url -> scope.launch { viewModel.completeCleanup(id, url) } }
                )
            }
        }

        // Zone Summary Bottom Sheet
        if (showZoneSummary && selectedZone != null) {
            ModalBottomSheet(
                onDismissRequest = { showZoneSummary = false }
            ) {
                ZoneSummaryBottomSheetContent(
                    zone = selectedZone!!,
                    reports = reports,
                    userLocation = userLocation,
                    onViewReports = {
                        filterStatus = selectedZone!!.name
                        scope.launch {
                            try {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(selectedZone!!.latitude, selectedZone!!.longitude), 14f
                                    )
                                )
                            } catch (e: Exception) {}
                        }
                        showZoneSummary = false
                    }
                )
            }
        }
    }
}

@Composable
fun ReportDetailBottomSheet(
    report: Report,
    userLocation: LatLng?,
    role: String,
    uid: String,
    onNavigate: (Double, Double) -> Unit,
    onTakeCharge: (String) -> Unit,
    onCompleteCleanup: (String, String) -> Unit
) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("Fetching address...") }
    var isUploading by remember { mutableStateOf(false) }
    val scope2 = rememberCoroutineScope()
    val viewModelForUpload: MainViewModel = viewModel()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            isUploading = true
            scope2.launch {
                try {
                    val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    } ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    val url = viewModelForUpload.uploadImage(bitmap)
                    onCompleteCleanup(report.id, url)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context, "Upload failed: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    LaunchedEffect(report) {
        address = LocationHelper.getAddressFromLatLng(context, report.latitude, report.longitude)
    }

    val distance = if (userLocation != null) {
        val distKm = LocationUtils.getDistanceInKm(userLocation.latitude, userLocation.longitude, report.latitude, report.longitude)
        "${LocationHelper.formatDistance(distKm)} away (${LocationHelper.estimateWalkingTime(distKm)})"
    } else {
        "Distance unknown"
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        // Stitch: 24dp rounded image container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            val rawUrl = (if (report.status == "Cleaned") report.cleanedPhotoUrl else report.photoUrl) ?: ""
            var imgModel by remember(rawUrl) { mutableStateOf<Any?>(null) }
            LaunchedEffect(rawUrl) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    imgModel = if (rawUrl.startsWith("data:image")) {
                        android.util.Base64.decode(rawUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                    } else rawUrl
                }
            }
            AsyncImage(
                model = imgModel,
                contentDescription = "Report image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Status Tag
            Surface(
                color = GreenPrimary.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
            ) {
                Text(
                    text = "AI VERIFIED",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = report.wasteType,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = OnBackground
        )
        
        Text(
            text = "📍 $address",
            fontSize = 14.sp,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Surface(
                color = when (report.status) {
                    "Reported" -> Color(0xFFFFDAD6)
                    "Assigned" -> Color(0xFFFEF3C7)
                    "Cleaned"  -> SecondaryContainer
                    else       -> Color.LightGray
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = report.status.uppercase(),
                    color = when (report.status) {
                        "Reported" -> Color(0xFFBA1A1A)
                        "Assigned" -> Color(0xFF92400E)
                        "Cleaned"  -> GreenPrimary
                        else       -> Color.DarkGray
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "📏 $distance", fontSize = 12.sp, color = OnSurfaceVariant, fontWeight = FontWeight.Bold)
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp), color = OnSurfaceVariant.copy(alpha = 0.1f))

        Text("Evidence Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = OnBackground)
        Text(
            "Dominant ${report.wasteType} identified. High risk for local ecosystem drainage.",
            fontSize = 13.sp,
            color = OnSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onNavigate(report.latitude, report.longitude) },
                modifier = Modifier.weight(1.5f).height(56.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GET DIRECTIONS", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Eco Alert: ${report.wasteType} at $address")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Alert"))
                },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f).height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OnSurfaceVariant.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.IosShare, contentDescription = null, tint = OnBackground, modifier = Modifier.size(18.dp))
            }
        }

        if (report.status == "Reported" && role == "Volunteer") {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onTakeCharge(report.id) },
                colors = ButtonDefaults.buttonColors(containerColor = OnBackground),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("TAKE CHARGE", fontWeight = FontWeight.Black)
            }
        } else if (report.status == "Assigned" && report.cleanerId == uid) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !isUploading,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("UPLOAD PROOF & COMPLETE", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun ZoneSummaryBottomSheetContent(zone: Zone, reports: List<Report>, userLocation: LatLng?, onViewReports: () -> Unit) {
    val zoneReports = reports.filter {
        LocationUtils.getDistanceInKm(it.latitude, it.longitude, zone.latitude, zone.longitude) <= 2.0
    }
    
    val reported = zoneReports.count { it.status == "Reported" }
    val assigned = zoneReports.count { it.status == "Assigned" }
    val cleaned = zoneReports.count { it.status == "Cleaned" }
    val total = zoneReports.size
    
    val score = if (total > 0) (cleaned.toFloat() / total) * 100 else 0f
    
    val distStr = if (userLocation != null) {
        val d = LocationUtils.getDistanceInKm(userLocation.latitude, userLocation.longitude, zone.latitude, zone.longitude)
        "${LocationHelper.formatDistance(d)} away"
    } else ""

    Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp).fillMaxWidth()) {
        Text(zone.name, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = OnBackground)
        if (distStr.isNotEmpty()) {
            Text(distStr, color = OnSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Reports: Total $total | 🔴 $reported | 🟠 $assigned | 🟢 $cleaned", fontWeight = FontWeight.Medium, color = OnBackground)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Cleanliness Score: ${score.toInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnBackground)
        // Stitch: thick 12dp pill progress bar, secondary-container track, primary indicator
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = GreenPrimary,
            trackColor = SecondaryContainer   // #B1F2BE
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onViewReports,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            Text("View Zone Reports")
        }
    }
}
