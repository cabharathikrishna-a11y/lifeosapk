package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusRecord
import com.example.ui.theme.WaterBlue

@Composable
fun FocusSummaryCard(
    focusRecords: List<FocusRecord>,
    todayStr: String,
    totalFocusMinutes: Int,
    liveAddedMinutes: Int = 0,
    liveAddedSeconds: Int = liveAddedMinutes * 60,
    activeTimer: com.example.api.ActiveTimer? = null,
    todayStats: com.example.api.TodayStats? = null,
    statsDashboard: com.example.api.StatsDashboard? = null
) {
    val currentTime = remember { androidx.compose.runtime.mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeTimer) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            currentTime.value = System.currentTimeMillis()
        }
    }

    val todayRecords = remember(focusRecords, todayStr) {
        focusRecords.filter { it.dateString == todayStr || it.dateString.isEmpty() }
    }
    
    val completedTodaySecs = remember(focusRecords, todayStr) {
        focusRecords.sumOf { com.example.util.FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
    }
    val todaySecs = remember(completedTodaySecs, liveAddedSeconds, activeTimer, todayStats, todayStr, currentTime.value) {
        val systemTodayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val isTargetDateToday = (todayStr == systemTodayStr)
        if (isTargetDateToday && (todayStats != null || activeTimer != null)) {
            val baseMs = if (todayStats?.dateString == todayStr || todayStats?.dateString.isNullOrEmpty()) {
                todayStats?.todayFocusTimeMs ?: 0L
            } else {
                0L
            }
            val liveDeltaMs = if (activeTimer != null) {
                when (activeTimer.status) {
                    "FOCUSING" -> {
                        val elapsed = currentTime.value - activeTimer.startTimeMs
                        maxOf(0L, elapsed) + activeTimer.accumulatedFocusMs
                    }
                    "BREAK", "PAUSED" -> activeTimer.accumulatedFocusMs
                    "RELAXING" -> 0L
                    else -> activeTimer.accumulatedFocusMs
                }
            } else 0L
            ((baseMs + liveDeltaMs) / 1000).toInt()
        } else {
            completedTodaySecs + liveAddedSeconds
        }
    }

    val completedTotalSecs = remember(focusRecords) {
        focusRecords.sumOf { it.durationSeconds }
    }
    val totalSecs = remember(completedTotalSecs, liveAddedSeconds, statsDashboard) {
        val baseSecs = if (statsDashboard != null && statsDashboard.allTimeMs > 0) {
            (statsDashboard.allTimeMs / 1000).toInt()
        } else {
            completedTotalSecs
        }
        baseSecs + liveAddedSeconds
    }
    
    var activeGroupTab by remember { mutableStateOf(1) } // 0 = By Task, 1 = By Tag

    val todayGrouped = remember(todayRecords, activeGroupTab) {
        if (activeGroupTab == 0) {
            todayRecords.groupBy { it.taskTitle.trim() }
        } else {
            todayRecords.groupBy { it.tag.trim() }
        }
    }
    
    val allGrouped = remember(focusRecords, activeGroupTab) {
        if (activeGroupTab == 0) {
            focusRecords.groupBy { it.taskTitle.trim() }
        } else {
            focusRecords.groupBy { it.tag.trim() }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Focus Summary Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Focus Activity Summary",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "Analytics",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Today Summary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                        .border(BorderStroke(0.5.dp, Color(0xFF262626)), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(text = "TODAY'S WORK", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatFocusedSeconds(todaySecs),
                        color = WaterBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${todayRecords.size} tags/sessions",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                // All-Time Summary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF161616), RoundedCornerShape(8.dp))
                        .border(BorderStroke(0.5.dp, Color(0xFF262626)), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(text = "TOTAL FOCUS COST", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatFocusedSeconds(totalSecs),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${focusRecords.size} sessions",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Sub-tabs to choose Group By Task vs Group By Tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { activeGroupTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeGroupTab == 0) Color(0xFF222222) else Color.Transparent,
                        contentColor = if (activeGroupTab == 0) Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("By Task", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { activeGroupTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeGroupTab == 1) Color(0xFF222222) else Color.Transparent,
                        contentColor = if (activeGroupTab == 1) Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("By Tag", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tagged Tasks Breakdown Header
            Text(
                text = if (activeGroupTab == 0) "Today's Productivity By Task" else "Today's Productivity By Tag",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // List of unique keys tagged today with counts
            val tasksTaggedToday = remember(todayGrouped, activeGroupTab) {
                todayGrouped.keys.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { 
                    if (it.isEmpty()) {
                        if (activeGroupTab == 0) "General Focus" else "Untagged"
                    } else it 
                })
            }

            if (tasksTaggedToday.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (activeGroupTab == 0) {
                            "No tasks tagged today yet. Start tagging tasks in focus sessions!"
                        } else {
                            "No tags assigned to sessions today. Try tagging sessions with categories!"
                        },
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val totalTodayMins = tasksTaggedToday.sumOf { raw -> (todayGrouped[raw] ?: emptyList()).sumOf { it.durationMinutes } }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasksTaggedToday.forEach { taskTitleRaw ->
                        val taskTitle = if (taskTitleRaw.isEmpty()) {
                            if (activeGroupTab == 0) "General Focus" else "Untagged"
                        } else taskTitleRaw
                        val todaySessions = todayGrouped[taskTitleRaw] ?: emptyList()
                        val todayCount = todaySessions.size
                        val todayTaskMins = todaySessions.sumOf { it.durationMinutes }

                        val allSessions = allGrouped[taskTitleRaw] ?: emptyList()
                        val allCount = allSessions.size
                        val allTaskMins = allSessions.sumOf { it.durationMinutes }

                        val pct = if (totalTodayMins > 0) todayTaskMins.toFloat() / totalTodayMins else 0f
                        val pctText = "${(pct * 100).toInt()}%"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141414), RoundedCornerShape(6.dp))
                                .border(BorderStroke(0.5.dp, Color(0xFF2C2C2C)), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(WaterBlue.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tag,
                                    contentDescription = "Task Tag",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = taskTitle,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = pctText,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = if (activeGroupTab == 0) "Today Tagged: $todayCount count" else "Today Focused: $todayCount times",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Duration: ${formatFocusedMinutes(todayTaskMins)}",
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = pct,
                                    color = WaterBlue,
                                    trackColor = Color(0xFF222222),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = if (activeGroupTab == 0) "All-Time Tagged: $allCount count" else "All-Time Focused: $allCount times",
                                        color = Color.DarkGray,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "All-Time Focus: ${formatFocusedMinutes(allTaskMins)}",
                                        color = Color.LightGray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFocusedMinutes(minutes: Int): String {
    val hrs = minutes / 60
    val mins = minutes % 60
    return when {
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h"
        else -> "${mins}m"
    }
}

private fun formatFocusedSeconds(totalSeconds: Int): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m ${secs}s"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
