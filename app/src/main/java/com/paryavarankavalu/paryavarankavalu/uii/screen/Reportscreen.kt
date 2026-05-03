package com.paryavarankavalu.paryavarankavalu.uii.screen

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.service.AiService
import com.paryavarankavalu.paryavarankavalu.ui.theme.*
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel
import com.paryavarankavalu.paryavarankavalu.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import kotlin.coroutines.resume
import java.nio.ByteBuffer
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    navController: NavController, 
    viewModel: MainViewModel = viewModel(),
    reportId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    
    var step by remember { mutableIntStateOf(1) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var wasteType by remember { mutableStateOf(if (reportId == null) "Analyzing..." else "Verifying Cleanup...") }
    var priority by remember { mutableStateOf("Low") }
    val isUploading by viewModel.isLoading.collectAsState()
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var detectedRegion by remember { mutableStateOf("Unknown Area") }

    val isCleanupMode = reportId != null
    val aiService = remember { AiService(BuildConfig.GEMINI_API_KEY) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                val bitmap = uriToBitmap(context, it)
                capturedBitmap = bitmap
                step = 2
                analyzeImage(aiService, bitmap, { 
                    wasteType = it 
                    priority = aiService.determinePriority(it)
                }, scope, isCleanupMode)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    if (userLocation == null) {
                        userLocation = Pair(it.latitude, it.longitude)
                        scope.launch { detectedRegion = getAreaName(context, it.latitude, it.longitude) }
                    }
                }
            }
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token).addOnSuccessListener { location ->
                location?.let { 
                    userLocation = Pair(it.latitude, it.longitude)
                    scope.launch { detectedRegion = getAreaName(context, it.latitude, it.longitude) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCleanupMode) "Verify Cleanup" else "Field Evidence", fontWeight = FontWeight.Bold, color = Forest900) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (step) {
                1 -> CameraCaptureView(
                    onPhotoCaptured = { bitmap ->
                        capturedBitmap = bitmap
                        step = 2
                        analyzeImage(aiService, bitmap, { 
                            wasteType = it 
                            priority = aiService.determinePriority(it)
                        }, scope, isCleanupMode)
                    },
                    onGalleryClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    permissionGranted = cameraPermissionState.status.isGranted,
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
                2 -> AnalysisPreview(
                    bitmap = capturedBitmap,
                    category = wasteType,
                    isUploading = isUploading,
                    locationReady = userLocation != null,
                    regionName = detectedRegion,
                    isCleanupMode = isCleanupMode,
                    onRetake = { step = 1; wasteType = if (isCleanupMode) "Verifying Cleanup..." else "Analyzing..."; priority = "Low" },
                    onCategoryChange = { newType -> 
                        wasteType = newType 
                        priority = aiService.determinePriority(newType)
                    },
                    onSubmit = {
                        if (isCleanupMode) {
                            viewModel.completeCleanupWithImage(
                                reportId = reportId!!,
                                bitmap = capturedBitmap!!,
                                onSuccess = { step = 3 },
                                onError = { msg -> Toast.makeText(context, "Action failed: $msg", Toast.LENGTH_SHORT).show() }
                            )
                        } else {
                            val report = Report(
                                id = UUID.randomUUID().toString(),
                                wasteType = wasteType,
                                priority = priority,
                                latitude = userLocation?.first ?: 0.0,
                                longitude = userLocation?.second ?: 0.0,
                                region = detectedRegion,
                                status = "Reported"
                            )
                            viewModel.submitReportWithImage(
                                report = report,
                                bitmap = capturedBitmap!!,
                                onSuccess = { step = 3 },
                                onError = { msg -> Toast.makeText(context, "Action failed: $msg", Toast.LENGTH_SHORT).show() }
                            )
                        }
                    }
                )
                3 -> SuccessView(isCleanupMode = isCleanupMode, onFinish = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun CameraCaptureView(
    onPhotoCaptured: (Bitmap) -> Unit,
    onGalleryClick: () -> Unit,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    if (!permissionGranted) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(64.dp), tint = GreenPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Camera access required", fontWeight = FontWeight.Bold)
            Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                Text("Grant Permission")
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        } catch (e: Exception) { Log.e("Camera", "Binding failed", e) }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onGalleryClick, modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                    Icon(Icons.Default.Add, contentDescription = "Gallery", tint = Color.White)
                }

                Surface(
                    modifier = Modifier.size(80.dp).clickable {
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = image.toBitmap()
                                    onPhotoCaptured(bitmap)
                                    image.close()
                                }
                                override fun onError(exception: ImageCaptureException) { Log.e("Camera", "Capture failed", exception) }
                            }
                        )
                    },
                    shape = CircleShape,
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(4.dp, GreenPrimary)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape).border(2.dp, Color.LightGray, CircleShape))
                    }
                }
                Box(modifier = Modifier.size(56.dp))
            }
        }
    }
}

@Composable
fun AnalysisPreview(
    bitmap: Bitmap?,
    category: String,
    isUploading: Boolean,
    locationReady: Boolean,
    regionName: String,
    isCleanupMode: Boolean,
    onRetake: () -> Unit,
    onCategoryChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (isCleanupMode) listOf("Site Cleaned", "Needs More Cleaning") else listOf("Plastic Waste", "Bio Waste", "Electronic Waste", "Hazardous Waste", "General Waste")

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp))) {
            bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Surface(
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
                color = if (category == "Needs More Cleaning") Color.Red else GreenPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (category == "Needs More Cleaning") Icons.Default.Add else Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isCleanupMode) (if (category == "Site Cleaned") "CLEANUP VERIFIED" else "CLEANUP FAILED") else "AI CATEGORIZED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Sage50)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box {
                    Column {
                        Text(if (isCleanupMode) "Status" else "Detected Category", color = Sage400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.clickable { expanded = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(category.uppercase(), color = Forest900, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change", tint = Forest900)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onCategoryChange(option); expanded = false }) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (locationReady) Icons.Default.LocationOn else Icons.Default.Add, contentDescription = null, tint = if (locationReady) GreenPrimary else Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (locationReady) regionName else "Fetching coordinates...", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)) {
                Text("RETAKE", color = Forest900, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onSubmit,
                modifier = Modifier.weight(1.5f).height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isCleanupMode && category == "Needs More Cleaning") Color.Gray else GreenPrimary),
                enabled = !isUploading && locationReady && (category != "Analyzing..." && category != "Verifying Cleanup...") && (!isCleanupMode || category == "Site Cleaned")
            ) {
                if (isUploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text(if (isCleanupMode) "COMPLETE TASK" else "SUBMIT REPORT", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SuccessView(isCleanupMode: Boolean = false, onFinish: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(120.dp).background(GreenLight, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(64.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(if (isCleanupMode) "Mission Accomplished!" else "Impact Recorded!", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Forest900)
        Text(if (isCleanupMode) "You've successfully cleaned this hotspot. +50 Eco Karma added to your profile." else "Your report has been synced with the local authority. +10 Eco Karma earned.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest900)) {
            Text("BACK TO DASHBOARD", fontWeight = FontWeight.Bold)
        }
    }
}

// Helpers
private fun analyzeImage(aiService: AiService, bitmap: Bitmap, onResult: (String) -> Unit, scope: kotlinx.coroutines.CoroutineScope, isCleanup: Boolean = false) {
    scope.launch {
        val result = aiService.analyzeWaste(bitmap, isCleanup)
        onResult(result)
    }
}

private suspend fun uriToBitmap(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
    try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        
        var inSampleSize = 1
        val reqHeight = 1024
        val reqWidth = 1024
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } 
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer: ByteBuffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    
    // Scale down if huge
    return if (bitmap != null && (bitmap.width > 1200 || bitmap.height > 1200)) {
        val scale = 1200f / maxOf(bitmap.width, bitmap.height)
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else {
        bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}

private suspend fun getAreaName(context: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    val res = if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        address.subLocality ?: address.locality ?: "Unknown Area"
                    } else "Unknown Area"
                    continuation.resume(res)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.subLocality ?: address.locality ?: "Unknown Area"
            } else "Unknown Area"
        }
    } catch (e: Exception) { "Unknown Area" }
}
