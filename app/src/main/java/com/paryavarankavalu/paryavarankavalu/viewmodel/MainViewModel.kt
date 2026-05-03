package com.paryavarankavalu.paryavarankavalu.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.paryavarankavalu.paryavarankavalu.repository.AppDatabase
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.paryavarankavalu.paryavarankavalu.model.Report
import com.paryavarankavalu.paryavarankavalu.model.UserProfile
import com.paryavarankavalu.paryavarankavalu.repository.AppRepository
import com.paryavarankavalu.paryavarankavalu.service.LocationUtils
import com.paryavarankavalu.paryavarankavalu.service.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.SharedPreferences
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.withTimeout

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = AppRepository(database.reportDao())
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

    private val _isAuthChecked = MutableStateFlow(false)
    val isAuthChecked: StateFlow<Boolean> = _isAuthChecked

    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode

    private var prefs: SharedPreferences? = null
    
    private var reportsListener: ListenerRegistration? = null
    private var profileListener: ListenerRegistration? = null
    private var leaderboardListener: ListenerRegistration? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            setupUserListeners(user.uid)
        } else {
            clearUserListeners()
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        reportsListener = repository.observeReports()
        leaderboardListener = repository.observeLeaderboard { users -> _leaderboard.value = users }
        
        viewModelScope.launch {
            repository.getLocalReports().collect { _reports.value = it }
        }

        viewModelScope.launch {
            delay(3000)
            if (!_isReady.value) _isReady.value = true
        }
    }

    private fun setupUserListeners(uid: String) {
        profileListener?.remove()
        profileListener = repository.observeUserProfile(uid) { profile ->
            _userProfile.value = profile
            _isReady.value = true
        }
        fetchAndSaveFcmToken()
    }

    private fun clearUserListeners() {
        profileListener?.remove()
        _userProfile.value = null
        _isReady.value = true
    }

    fun initTheme(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val themeVal = prefs?.getInt("dark_mode", 0) ?: 0
            _isDarkMode.value = when(themeVal) { 1 -> false; 2 -> true; else -> null }
        }
    }

    fun setDarkMode(enabled: Boolean?) {
        _isDarkMode.value = enabled
        prefs?.edit()?.putInt("dark_mode", when(enabled) { false -> 1; true -> 2; null -> 0 })?.apply()
    }

    fun startLocationTracking(context: Context) {
        if (_isTracking.value) return
        fusedLocationClient = fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    val newLatLng = LatLng(it.latitude, it.longitude)
                    _userLocation.value = newLatLng
                    val nearest = LocationUtils.getNearestZone(it.latitude, it.longitude)
                    _nearestZone.value = nearest
                    if (_activeRegion.value == "All India") _activeRegion.value = nearest.name
                }
            }
        }
        try {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
            _isTracking.value = true
        } catch (e: SecurityException) {
            _isTracking.value = false
        }
    }

    fun stopLocationTracking() {
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        _isTracking.value = false
    }

    fun getOneTimeLocation(context: Context) {
        fusedLocationClient = fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient?.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)?.addOnSuccessListener { location ->
                location?.let {
                    _userLocation.value = LatLng(it.latitude, it.longitude)
                    _nearestZone.value = LocationUtils.getNearestZone(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {}
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        stopLocationTracking()
        reportsListener?.remove()
        profileListener?.remove()
        leaderboardListener?.remove()
    }

    fun setActiveRegion(region: String) { _activeRegion.value = region }
    fun setSelectedReportId(id: String?) { _selectedReportId.value = id }

    fun submitReportWithImage(report: Report, bitmap: Bitmap, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            try {
                Log.d(TAG, "Starting report submission flow...")
                val url = repository.uploadImage(bitmap)
                repository.submitReport(report.copy(photoUrl = url))
                Log.d(TAG, "Report submitted successfully.")
                onSuccess()
            } catch (e: Throwable) {
                Log.e(TAG, "Report submission failed", e)
                onError(e.message ?: "Submission failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fix for "Complete Task" hang:
     * 1. Added explicit Throwable catching (handles all errors/timeouts).
     * 2. Added logging to track progress.
     * 3. Prevents double-clicks.
     */
    fun completeCleanupWithImage(reportId: String, bitmap: Bitmap, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            try {
                Log.d(TAG, "Starting cleanup completion flow for: $reportId")
                
                // Uploading image with a local timeout check
                val url = withTimeout(40000L) {
                    repository.uploadImage(bitmap)
                }
                Log.d(TAG, "Proof image ready (URL or Base64). Finalizing task...")
                
                // Final document sync
                withTimeout(20000L) {
                    repository.completeCleanup(reportId, url)
                }
                
                Log.d(TAG, "Cleanup completed successfully.")
                onSuccess()
            } catch (e: Throwable) {
                Log.e(TAG, "Complete Task process failed", e)
                val msg = if (e is kotlinx.coroutines.TimeoutCancellationException) 
                    "Network timed out. Please try with a better connection."
                    else e.message ?: "Cleanup failed"
                onError(msg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createUserProfile(uid: String, email: String, displayName: String) {
        viewModelScope.launch { repository.createUserProfile(uid, email, displayName) }
    }

    fun bookCleanup(reportId: String) = viewModelScope.launch { repository.bookCleanup(reportId) }
    fun toggleLike(reportId: String) = viewModelScope.launch { repository.toggleLike(reportId) }
    fun updateRegion(region: String?) = viewModelScope.launch { repository.updateRegion(region) }
    fun updateRole(role: String) = viewModelScope.launch { repository.updateRole(role) }
    fun deleteReport(reportId: String) = viewModelScope.launch { repository.deleteReport(reportId) }
    fun updateNotificationSettings(enabled: Boolean, sound: String, vibration: Boolean) = viewModelScope.launch { repository.updateNotificationSettings(enabled, sound, vibration) }

    private fun fetchAndSaveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) viewModelScope.launch { repository.updateFcmToken(task.result) }
        }
    }
}
