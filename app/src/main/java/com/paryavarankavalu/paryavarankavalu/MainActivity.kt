package com.paryavarankavalu.paryavarankavalu

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.paryavarankavalu.paryavarankavalu.ui.theme.GreenPrimary
import com.paryavarankavalu.paryavarankavalu.ui.theme.GreenPrimaryContainer
import com.paryavarankavalu.paryavarankavalu.ui.theme.OnGreenPrimaryContainer
import com.paryavarankavalu.paryavarankavalu.ui.theme.Background
import com.paryavarankavalu.paryavarankavalu.ui.theme.SurfaceContainerLowest
import com.paryavarankavalu.paryavarankavalu.ui.theme.OnSurface
import com.paryavarankavalu.paryavarankavalu.ui.theme.OnSurfaceVariant
import com.paryavarankavalu.paryavarankavalu.ui.theme.Outline
import com.paryavarankavalu.paryavarankavalu.ui.theme.ParyavaranKavaluTheme
import com.paryavarankavalu.paryavarankavalu.uii.screen.*
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

        val auth = FirebaseAuth.getInstance()
        val startDest = if (auth.currentUser != null) "home" else "auth"

        setContent {
            var showSplash by remember { mutableStateOf(true) }
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val context = LocalContext.current

            // Initialize background tasks and tracking without blocking onCreate
            LaunchedEffect(Unit) {
                viewModel.initTheme(context)
                viewModel.startLocationTracking(context)
            }
            
            val useDarkTheme = isDarkMode ?: isSystemInDarkTheme()
            
            ParyavaranKavaluTheme(darkTheme = useDarkTheme) {
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(400),
                    label = "SplashTransition"
                ) { isSplashing ->
                    if (isSplashing) {
                        SplashScreen(onTimeout = { showSplash = false })
                    } else {
                        AppMain(startDest, viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMain(startDest: String, viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Hiding logic
    val shouldShowNavbar = currentDestination?.route?.let { route ->
        !route.startsWith("report") && route != "auth"
    } ?: true

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = shouldShowNavbar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                CustomBottomBar(
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    currentRoute = currentDestination?.route?.substringBefore("?") ?: "home"
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(padding)
        ) {
            composable("auth") { AuthScreen(navController, viewModel) }
            composable("home") { HomeScreen(navController, viewModel) }
            composable(
                route = "report?reportId={reportId}",
                arguments = listOf(navArgument("reportId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val rId = backStackEntry.arguments?.getString("reportId")
                ReportScreen(navController, viewModel, reportId = rId)
            }
            composable("map") { MapScreen(navController, viewModel) }
            composable("community") { CommunityScreen(navController, viewModel) }
            composable("profile") { ProfileScreen(navController, viewModel) }
            composable("notification_settings") { NotificationSettingsScreen(navController, viewModel) }
        }
    }
}

@Composable
fun CustomBottomBar(onNavigate: (String) -> Unit, currentRoute: String) {
    // Stitch: Level 1 card surface — pure white, 24dp radius, soft ambient shadow
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTabItem("home",      Icons.Default.Home,       "Home",    currentRoute == "home",      onNavigate)
            BottomTabItem("map",       Icons.Default.LocationOn, "Map",     currentRoute == "map",       onNavigate)

            // Stitch FAB — pill-shaped, primary-container color, Level 2 shadow
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onNavigate("report") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Report Issue",
                    tint  = OnGreenPrimaryContainer,     // #004B1E
                    modifier = Modifier.size(28.dp)
                )
            }

            BottomTabItem("community", Icons.Default.Star,       "Feed",    currentRoute == "community", onNavigate)
            BottomTabItem("profile",   Icons.Default.Person,     "Profile", currentRoute == "profile",   onNavigate)
        }
    }
}

@Composable
fun RowScope.BottomTabItem(
    route: String,
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onNavigate: (String) -> Unit
) {
    // Stitch: active item gets pill-shaped primary-tinted background pill
    val bgColor = if (isSelected) GreenPrimaryContainer.copy(alpha = 0.15f) else Color.Transparent
    val fgColor = if (isSelected) GreenPrimary else OnSurfaceVariant

    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .clickable { onNavigate(route) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint     = fgColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color    = fgColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style    = MaterialTheme.typography.labelSmall
        )
    }
}
