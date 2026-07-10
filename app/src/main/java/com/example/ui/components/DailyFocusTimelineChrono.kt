package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusRecord
import com.example.ui.theme.WaterBlue

@Composable
fun DailyFocusTimelineChrono(
    focusRecords: List<FocusRecord>,
    selectedDateStr: String,
    modifier: Modifier = Modifier
) {
    val systemTodayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
    val todayRecords = remember(focusRecords, selectedDateStr) {
        focusRecords.filter { it.dateString == selectedDateStr || (it.dateString.isEmpty() && selectedDateStr == systemTodayStr) }
    }

    // Parse each record's start and end times into double hours (0.0 to 24.0)
    val parsedSessions: List<Triple<Double, Double, FocusRecord>> = remember(todayRecords) {
        todayRecords.mapNotNull { record ->
            var sFrac = parseTimeToHourFraction(record.startTime)
            var eFrac = parseTimeToHourFraction(record.endTime)

            if (sFrac == null || eFrac == null) {
                if (record.timestamp > 0L) {
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = record.timestamp
                    }
                    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val min = cal.get(java.util.Calendar.MINUTE)
                    sFrac = hour + min / 60.0
                    eFrac = sFrac + (record.durationMinutes / 60.0)
                    if (eFrac > 24.0) eFrac = 24.0
                }
            }

            if (sFrac != null && eFrac != null) {
                val start = kotlin.math.min(sFrac, eFrac)
                val end = kotlin.math.max(sFrac, eFrac)
                Triple<Double, Double, FocusRecord>(start, end, record)
            } else {
                null
            }
        }
    }

    // Calculate overlap fraction for each of the 24 hours
    val hourlyData = remember(parsedSessions) {
        DoubleArray(24) { hour ->
            var sumOverlap = 0.0
            parsedSessions.forEach { triple ->
                val start = triple.first
                val end = triple.second
                val overlapStart = kotlin.math.max(hour.toDouble(), start)
                val overlapEnd = kotlin.math.min((hour + 1).toDouble(), end)
                if (overlapEnd > overlapStart) {
                    sumOverlap += (overlapEnd - overlapStart)
                }
            }
            kotlin.math.min(1.0, sumOverlap)
        }
    }

    // State for interactive hour selection
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        border = BorderStroke(1.dp, Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Chrono Timeline",
                        tint = WaterBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Daily Focus Timeline",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "24h Analytical Grid",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // The Horizontal Chronograph Bar (divided into 24 pieces)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(4.dp))
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (hour in 0..23) {
                    val scale = hourlyData[hour]
                    val isSelected = selectedHour == hour

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    selectedHour = if (selectedHour == hour) null else hour
                                }
                            )
                            .background(
                                color = if (isSelected) Color(0xFF161616) else Color.Black
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) WaterBlue else Color.Transparent,
                                shape = RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (scale > 0.0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(scale.toFloat())
                                    .fillMaxHeight()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                WaterBlue.copy(alpha = 0.5f),
                                                WaterBlue.copy(alpha = 0.95f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Every hour timestamp kept below
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (hour in 0..23) {
                    val scale = hourlyData[hour]
                    val isSelected = selectedHour == hour
                    val hourStr = String.format("%02d", hour)

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = hourStr,
                            color = if (isSelected) WaterBlue else if (scale > 0.0) Color.LightGray else Color.Gray.copy(alpha = 0.8f),
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected || scale > 0.0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detail Panel explaining what occurred during selection
            val activeHour = selectedHour
            if (activeHour != null) {
                val fraction = hourlyData[activeHour]
                val mins = (fraction * 60).toInt()
                val hourRangeStr = String.format("%02d:00 - %02d:59", activeHour, activeHour)

                // Find matching focus session record (if any)
                val matchingRecord = parsedSessions.firstOrNull { triple ->
                    val start = triple.first
                    val end = triple.second
                    val overlapStart = kotlin.math.max(activeHour.toDouble(), start)
                    val overlapEnd = kotlin.math.min((activeHour + 1).toDouble(), end)
                    overlapEnd > overlapStart
                }?.third

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF151515))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (mins > 0) WaterBlue else Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Time slot: $hourRangeStr",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (mins > 0) {
                                "Focused $mins mins" + (matchingRecord?.let { " on: ${it.taskTitle}" } ?: "")
                            } else {
                                "No active focus session recorded in this hour"
                            },
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "💡 Tip: Tap any hour block above to audit precise focus details.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

fun parseTimeToHourFraction(timeStr: String): Double? {
    return try {
        val cleanStr = timeStr.trim().uppercase()
        val parts = cleanStr.split(" ")
        if (parts.size >= 2) {
            val hms = parts[0].split(":")
            var hour = hms[0].toIntOrNull() ?: 0
            val min = if (hms.size > 1) hms[1].toIntOrNull() ?: 0 else 0
            val ampm = parts[1]
            if (ampm == "PM" && hour < 12) {
                hour += 12
            } else if (ampm == "AM" && hour == 12) {
                hour = 0
            }
            hour + min / 60.0
        } else {
            // Fallback for 24 hour formats if any
            val hms = cleanStr.split(":")
            val hour = hms[0].toIntOrNull() ?: 0
            val min = if (hms.size > 1) hms[1].toIntOrNull() ?: 0 else 0
            hour + min / 60.0
        }
    } catch (e: Exception) {
        null
    }
}
