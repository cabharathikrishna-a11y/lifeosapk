package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel

data class LeaderboardUser(
    val username: String,
    val displayName: String,
    val emoji: String,
    val focusedSeconds: Int,
    val isMe: Boolean
)

@Composable
fun MetalShield(color: Color, rankText: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.5f)
                quadraticTo(w, h * 0.85f, w / 2f, h)
                quadraticTo(0f, h * 0.85f, 0f, h * 0.5f)
                lineTo(0f, 0f)
                close()
            }
            drawPath(path, color = color)
        }
        Text(
            text = rankText,
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}

@Composable
fun FriendsFocusLeaderboardTable(
    viewModel: AppViewModel,
    selectedDateStr: String,
    myTodaySeconds: Int,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allUsers by viewModel.allUsers.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val currentUserRemote by viewModel.currentUserRemote.collectAsState()
    
    val systemTodayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val isToday = selectedDateStr == systemTodayStr
    
    val currentTime = remember { androidx.compose.runtime.mutableStateOf(System.currentTimeMillis()) }
    if (isToday) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                currentTime.value = System.currentTimeMillis()
            }
        }
    }
    
    val leaderboardList = remember(selectedDateStr, myTodaySeconds, allUsers, currentUsername, currentUserRemote, currentTime.value) {
        val prefKey = "friends_focus_leaderboard_$selectedDateStr"
        
        if (isToday) {
            val meUsername = currentUsername ?: "me_user"
            
            val liveMe = LeaderboardUser(
                username = meUsername,
                displayName = currentUserRemote?.nickname ?: currentUserRemote?.name ?: "Bharathikrishna M",
                emoji = currentUserRemote?.emoji ?: "👨‍💻",
                focusedSeconds = run {
                    val meUser = allUsers[meUsername] ?: currentUserRemote
                    val baseMs = if (meUser?.todayStats?.dateString == selectedDateStr || meUser?.todayStats?.dateString.isNullOrEmpty()) {
                        meUser?.todayStats?.todayFocusTimeMs ?: 0L
                    } else {
                        0L
                    }
                    val liveDeltaMs = meUser?.activeTimer?.let { activeTimer ->
                        when (activeTimer.status) {
                            "FOCUSING" -> {
                                val elapsed = currentTime.value - activeTimer.startTimeMs
                                maxOf(0L, elapsed) + activeTimer.accumulatedFocusMs
                            }
                            "BREAK", "PAUSED" -> activeTimer.accumulatedFocusMs
                            "RELAXING" -> 0L
                            else -> activeTimer.accumulatedFocusMs
                        }
                    } ?: 0L
                    ((baseMs + liveDeltaMs) / 1000).toInt()
                },
                isMe = true
            )
            
            val peerList = allUsers.filter { entry ->
                entry.key != "admin" && 
                entry.key != meUsername
            }
            val livePeers = peerList.map { entry ->
                val u = entry.value
                val nameToShow = u.nickname ?: u.name ?: entry.key
                val baseMs = if (u.todayStats?.dateString == selectedDateStr || u.todayStats?.dateString.isNullOrEmpty()) {
                    u.todayStats?.todayFocusTimeMs ?: 0L
                } else {
                    0L
                }
                val liveDeltaMs = u.activeTimer?.let { activeTimer ->
                    when (activeTimer.status) {
                        "FOCUSING" -> {
                            val elapsed = currentTime.value - activeTimer.startTimeMs
                            maxOf(0L, elapsed) + activeTimer.accumulatedFocusMs
                        }
                        "BREAK", "PAUSED" -> activeTimer.accumulatedFocusMs
                        "RELAXING" -> 0L
                        else -> activeTimer.accumulatedFocusMs
                    }
                } ?: 0L
                val liveSecs = ((baseMs + liveDeltaMs) / 1000).toInt()
                
                LeaderboardUser(
                    username = entry.key,
                    displayName = nameToShow,
                    emoji = u.emoji ?: "🎯",
                    focusedSeconds = liveSecs,
                    isMe = false
                )
            }
            
            val combined = (listOf(liveMe) + livePeers).sortedByDescending { it.focusedSeconds }
            combined
        } else {
            val saved = com.example.util.PrefsDataStore.getStringBlocking(context, prefKey, null)
            if (saved != null) {
                saved.split("\n").mapNotNull { line ->
                    val parts = line.split(";;;")
                    if (parts.size >= 5) {
                        val username = parts[0]
                        val isMe = parts[4].toBoolean()
                        if (isMe || allUsers.containsKey(username)) {
                            LeaderboardUser(
                                username = username,
                                displayName = parts[1],
                                emoji = parts[2],
                                focusedSeconds = parts[3].toIntOrNull() ?: 0,
                                isMe = isMe
                            )
                        } else null
                    } else null
                }.sortedByDescending { it.focusedSeconds }
            } else {
                val seed = selectedDateStr.hashCode().toLong()
                val rand = java.util.Random(seed)
                
                val myName = currentUserRemote?.nickname ?: currentUserRemote?.name ?: "Bharathikrishna M"
                val myEmoji = currentUserRemote?.emoji ?: "👨‍💻"
                
                val liveMe = LeaderboardUser(
                    username = currentUsername ?: "me_user",
                    displayName = myName,
                    emoji = myEmoji,
                    focusedSeconds = myTodaySeconds,
                    isMe = true
                )
                
                val list = mutableListOf(liveMe)
                val peerList = allUsers.filter { entry ->
                    entry.key != "admin" && 
                    entry.key != (currentUsername ?: "me_user")
                }
                peerList.forEach { entry ->
                    val u = entry.value
                    val nameToShow = u.nickname ?: u.name ?: entry.key
                    val seedUser = (entry.key + selectedDateStr).hashCode().toLong()
                    val randUser = java.util.Random(seedUser)
                    val generatedSecs = (1 + randUser.nextInt(7)) * 3600 + randUser.nextInt(60) * 60
                    list.add(
                        LeaderboardUser(
                            username = entry.key,
                            displayName = nameToShow,
                            emoji = u.emoji ?: "🎯",
                            focusedSeconds = if (randUser.nextInt(10) < 8) generatedSecs else 0,
                            isMe = false
                        )
                    )
                }
                val sortedList = list.sortedByDescending { it.focusedSeconds }
                
                val serialized = sortedList.joinToString("\n") { u ->
                    "${u.username};;;${u.displayName};;;${u.emoji};;;${u.focusedSeconds};;;${u.isMe}"
                }
                com.example.util.PrefsDataStore.putStringBlocking(context, prefKey, serialized)
                
                sortedList
            }
        }
    }
    
    val currentLeaderboardList by rememberUpdatedState(leaderboardList)
    
    if (isToday) {
        LaunchedEffect(selectedDateStr) {
            while (true) {
                kotlinx.coroutines.delay(30000L) // Save at most once every 30 seconds
                val listToSave = currentLeaderboardList
                if (listToSave.isNotEmpty()) {
                    val prefKey = "friends_focus_leaderboard_$selectedDateStr"
                    val serialized = listToSave.joinToString("\n") { u ->
                        "${u.username};;;${u.displayName};;;${u.emoji};;;${u.focusedSeconds};;;${u.isMe}"
                    }
                    com.example.util.PrefsDataStore.putString(context, prefKey, serialized)
                }
            }
        }

        DisposableEffect(selectedDateStr) {
            onDispose {
                val listToSave = currentLeaderboardList
                if (listToSave.isNotEmpty()) {
                    val prefKey = "friends_focus_leaderboard_$selectedDateStr"
                    val serialized = listToSave.joinToString("\n") { u ->
                        "${u.username};;;${u.displayName};;;${u.emoji};;;${u.focusedSeconds};;;${u.isMe}"
                    }
                    com.example.util.PrefsDataStore.putStringBlocking(context, prefKey, serialized)
                }
            }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isToday) "Today's Friends Focus Details" else "Friends Focus Details ($selectedDateStr)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                if (isToday) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Saved Archive", color = Color.Gray, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                var currentRankValue = 1
                val computedRanks = leaderboardList.associate { u ->
                    u.username to if (u.focusedSeconds > 0) currentRankValue++ else null
                }

                leaderboardList.forEach { user ->
                    val rankOpt = computedRanks[user.username]
                    val hasRank = rankOpt != null
                    val rank = rankOpt ?: 0
                    
                    val metalColor = when (rank) {
                        1 -> Color(0xFFB9F2FF) // Diamond
                        2 -> Color(0xFFFFD700) // Gold
                        3 -> Color(0xFFC0C0C0) // Silver
                        4 -> Color(0xFFCD7F32) // Bronze
                        else -> Color.Transparent
                    }
                    
                    val shieldColor = when (rank) {
                        1 -> Color(0xFF00E5FF) // Diamond
                        2 -> Color(0xFFFFD700) // Gold
                        3 -> Color(0xFFC0C0C0) // Silver
                        4 -> Color(0xFFCD7F32) // Bronze
                        else -> Color(0xFF424242) // Standard grey shield
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (user.isMe) Color(0xFF2E7D32).copy(alpha = 0.12f) else Color(0xFF161616))
                            .border(
                                width = 1.dp,
                                color = if (user.isMe) Color(0xFF4CAF50).copy(alpha = 0.25f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank Indicator
                        Text(
                            text = if (hasRank) { if (rank <= 4) "-" else "$rank" } else "-",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        // Avatar Container with metal ring & shield badge
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(
                                emojiOrBase64 = user.emoji,
                                username = user.username,
                                photoUpdatedAt = allUsers[user.username]?.profile?.photoUpdatedAt ?: 0L,
                                size = 36.dp,
                                fontSize = 18.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(
                                        width = if (hasRank && rank <= 4) 2.dp else 1.dp,
                                        color = if (hasRank && rank <= 4) metalColor else Color.White.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                            )
                            
                            if (hasRank) {
                                MetalShield(
                                    color = shieldColor,
                                    rankText = "$rank",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 2.dp, y = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Name and Device Info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                  Text(
                                    text = user.displayName,
                                    color = if (user.isMe) Color(0xFF81C784) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                if (user.isMe) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "You",
                                        color = Color(0xFF81C784),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.25f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (user.isMe) "Device 3" else if (user.username == "device2") "Device 2" else "Active member",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                        
                        // Time label
                        val h = user.focusedSeconds / 3600
                        val m = (user.focusedSeconds % 3600) / 60
                        val timeText = if (h > 0) "${h}h ${m}m" else "${m}m"
                        Text(
                            text = timeText,
                            color = if (user.focusedSeconds > 0) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
