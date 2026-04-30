package com.paryavarankavalu.paryavarankavalu.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.model.UserProfile
import com.paryavarankavalu.paryavarankavalu.repository.AppRepository
import com.paryavarankavalu.paryavarankavalu.service.LocationUtils
import com.paryavarankavalu.paryavarankavalu.service.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainViewModel : ViewModel() {
    private val repository = AppRepository()
    private val auth = FirebaseAuth.getInstance()
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _leaderboard = MutableStateFlow<List<UserProfile>>(emptyList())
    val leaderboard: StateFlow<List<UserProfile>> = _leaderboard

    private val _activeRegion = MutableStateFlow("All India")
    val activeRegion: StateFlow<String> = _activeRegion

    private val _selectedReportId = MutableStateFlow<String?>(null)
    val selectedReportId: StateFlow<String?> = _selectedReportId

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation

    private val _nearestZone = MutableStateFlow<Zone?>(null)
    val nearestZone: StateFlow<Zone?> = _nearestZone

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        repository.observeReports { newReports ->
            _reports.value = newReports
        }

        auth.currentUser?.uid?.let { uid ->
            repository.observeUserProfile(uid) { profile ->
                _userProfile.value = profile
                checkReadyStatus()
            }
        } ?: run {
            // Not logged in, auth state is "retrieved" (null)
            checkReadyStatus()
        }
        
        repository.observeLeaderboard { users ->
            _leaderboard.value = users
        }

        // Safety Timeout: Ensure splash screen dismisses even if data retrieval is slow
        viewModelScope.launch {
            delay(3500) // Reduced to 3.5 seconds for better UX
            if (!_isReady.value) {
                Log.w("MainViewModel", "Splash timeout reached. Forcing ready state.")
                _isReady.value = true
            }
        }
    }

    fun startLocationTracking(context: Context) {
        if (_isTracking.value) return
        
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    val newLatLng = LatLng(it.latitude, it.longitude)
                    _userLocation.value = newLatLng
                    
                    val nearest = LocationUtils.getNearestZone(it.latitude, it.longitude)
                    _nearestZone.value = nearest
                    
                    if (_activeRegion.value == "All India") {
                        _activeRegion.value = nearest.name
                    }
                    checkReadyStatus()
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            _isTracking.value = true
        } catch (unlikely: SecurityException) {
            Log.e("MainViewModel", "Lost location permission. $unlikely")
            _isTracking.value = false
            // If permission is denied, don't hang the splash screen
            checkReadyStatus()
        }
    }

    fun stopLocationTracking() {
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        _isTracking.value = false
    }

    fun getOneTimeLocation(context: Context) {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }
        try {
            fusedLocationClient?.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                ?.addOnSuccessListener { location ->
                    location?.let {
                        _userLocation.value = LatLng(it.latitude, it.longitude)
                        _nearestZone.value = LocationUtils.getNearestZone(it.latitude, it.longitude)
                    }
                }
        } catch (unlikely: SecurityException) {
            Log.e("MainViewModel", "Lost location permission. $unlikely")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationTracking()
    }

    fun setActiveRegion(region: String) {
        _activeRegion.value = region
    }

    fun setSelectedReportId(id: String?) {
        _selectedReportId.value = id
    }

    suspend fun uploadImage(bitmap: Bitmap): String {
        return repository.uploadImage(bitmap)
    }

    suspend fun submitReport(report: Report) {
        repository.submitReport(report)
    }

    fun createUserProfile(uid: String, email: String, displayName: String) {
        viewModelScope.launch { 
            try {
                repository.createUserProfile(uid, email, displayName)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error creating user profile", e)
            }
        }
    }

    fun bookCleanup(reportId: String) {
        viewModelScope.launch { 
            try {
                repository.bookCleanup(reportId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error booking cleanup", e)
            }
        }
    }

    fun toggleLike(reportId: String) {
        viewModelScope.launch { 
            try {
                repository.toggleLike(reportId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling like", e)
            }
        }
    }

    fun updateRegion(region: String?) {
        viewModelScope.launch { 
            try {
                repository.updateRegion(region)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating region", e)
            }
        }
    }

    fun updateRole(role: String) {
        viewModelScope.launch { 
            try {
                repository.updateRole(role)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error updating role", e)
            }
        }
    }

    suspend fun completeCleanup(reportId: String, photoUrl: String) {
        repository.completeCleanup(reportId, photoUrl)
    }

    private fun checkReadyStatus() {
        val authReady = auth.currentUser == null || _userProfile.value != null
        val locationReady = _userLocation.value != null
        if (authReady && locationReady) {
            _isReady.value = true
        }
    }
}
