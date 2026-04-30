package com.paryavarankavalu.paryavarankavalu.uii.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun CommunityScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val reports by viewModel.reports.collectAsState()
    val leaderboard by viewModel.leaderboard.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val cleanedReports = reports.filter { it.status == "Cleaned" }.sortedByDescending { it.timestamp }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Eco Feed", "Leaderboard")

    Scaffold(
        containerColor = Sage50,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 48.dp)
            ) {
                Text(
                    text = "Community",
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = Forest900,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = GreenPrimary,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = GreenPrimary
                            )
                        }
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                Text(
                                    title, 
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                ) 
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> FeedSection(cleanedReports, userProfile?.uid ?: "", onLike = { id -> viewModel.toggleLike(id) })
                1 -> LeaderboardSection(leaderboard)
            }
        }
    }
}

@Composable
fun FeedSection(cleanedReports: List<Report>, currentUserId: String, onLike: (String) -> Unit) {
    if (cleanedReports.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cleanup stories yet. Be the first!", color = Sage400)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            items(cleanedReports) { report ->
                CleanupPost(report, currentUserId, onLike)
            }
        }
    }
}

@Composable
fun LeaderboardSection(users: List<UserProfile>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 100.dp)
    ) {
        item {
            Text(
                "Top Contributors",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = Forest900,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        itemsIndexed(users) { index, user ->
            LeaderboardItem(index + 1, user)
        }
    }
}

@Composable
fun LeaderboardItem(rank: Int, user: UserProfile) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rank.toString(),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = if (rank <= 3) GreenPrimary else Sage400,
                modifier = Modifier.width(32.dp)
            )
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Sage50, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    user.displayName.firstOrNull()?.toString() ?: "U",
                    fontWeight = FontWeight.Bold,
                    color = Forest900
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.Bold, color = Forest900, fontSize = 14.sp)
                Text("${user.role} • ${user.cleanupsCount} cleanups", fontSize = 11.sp, color = Sage400)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(user.ecoKarma.toString(), fontWeight = FontWeight.Black, color = Forest900)
            }
        }
    }
}

@Composable
fun CleanupPost(report: Report, currentUserId: String, onLike: (String) -> Unit) {
    val isLiked = report.likes.contains(currentUserId)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(GreenLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = report.cleanerName?.firstOrNull()?.toString() ?: "V",
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = report.cleanerName ?: "Eco Warrior",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Forest900
                    )
                    val date = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(report.timestamp))
                    Text(text = "Completed on $date", fontSize = 11.sp, color = Sage400)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Successfully cleaned a ${report.wasteType} hotspot in ${report.region.ifBlank { "the neighborhood" }}!",
                fontSize = 14.sp,
                color = Forest900,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BEFORE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Sage400, modifier = Modifier.padding(bottom = 4.dp))
                    val rawBeforeUrl = report.photoUrl ?: ""
                    val beforeModel = if (rawBeforeUrl.startsWith("data:image")) {
                        android.util.Base64.decode(rawBeforeUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                    } else rawBeforeUrl
                    AsyncImage(
                        model = beforeModel,
                        contentDescription = "Before",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AFTER", fontSize = 9.sp, fontWeight = FontWeight.Black, color = GreenPrimary, modifier = Modifier.padding(bottom = 4.dp))
                    val rawAfterUrl = report.cleanedPhotoUrl ?: ""
                    val afterModel = if (rawAfterUrl.startsWith("data:image")) {
                        android.util.Base64.decode(rawAfterUrl.substringAfter("base64,"), android.util.Base64.DEFAULT)
                    } else rawAfterUrl
                    AsyncImage(
                        model = afterModel,
                        contentDescription = "After",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { onLike(report.id) }) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                        contentDescription = "Like", 
                        tint = if (isLiked) Color.Red else Sage400
                    )
                }
                if (report.likes.isNotEmpty()) {
                    Text(
                        text = "${report.likes.size}",
                        fontSize = 12.sp,
                        color = Sage400,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Sage400)
                }
            }
        }
    }
}
