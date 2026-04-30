package com.paryavarankavalu.paryavarankavalu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.paryavarankavalu.paryavarankavalu.ui.theme.ParyavaranKavaluTheme
import com.paryavarankavalu.paryavarankavalu.uii.screen.*
import com.paryavarankavalu.paryavarankavalu.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = FirebaseAuth.getInstance()
        val startDest = if (auth.currentUser != null) "home" else "auth"

        setContent {
            ParyavaranKavaluTheme {
                AppMain(startDest)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMain(startDest: String) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val viewModel: MainViewModel = viewModel()

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
            composable("auth") { AuthScreen(navController) }
            composable("home") { HomeScreen(navController) }
            composable(
                route = "report?reportId={reportId}",
                arguments = listOf(navArgument("reportId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val rId = backStackEntry.arguments?.getString("reportId")
                ReportScreen(navController, reportId = rId)
            }
            composable("map") { MapScreen(navController = navController, viewModel = viewModel) }
            composable("community") { CommunityScreen(navController) }
            composable("profile") { ProfileScreen(navController) }
        }
    }
}

@Composable
fun CustomBottomBar(onNavigate: (String) -> Unit, currentRoute: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTabItem("home", Icons.Default.Home, "Home", currentRoute == "home", onNavigate)
            BottomTabItem("map", Icons.Default.LocationOn, "Map", currentRoute == "map", onNavigate)
            
            // Central Floating Action Button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary)
                    .clickable { onNavigate("report") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Report", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            BottomTabItem("community", Icons.Default.Star, "Feed", currentRoute == "community", onNavigate)
            BottomTabItem("profile", Icons.Default.Person, "Profile", currentRoute == "profile", onNavigate)
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
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigate(route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) GreenPrimary else Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = if (isSelected) GreenPrimary else Color.Gray.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
