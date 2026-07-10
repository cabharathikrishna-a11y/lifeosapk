package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.WaterBlue
import com.example.util.SleepTimeHelper

@Composable
fun CalendarOptimizationOnboardingView(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var wakeHour by remember { mutableStateOf(7) }
    var wakeMin by remember { mutableStateOf(0) }

    var sleepHour by remember { mutableStateOf(22) }
    var sleepMin by remember { mutableStateOf(0) }

    val formattedWake = String.format("%02d:%02d", wakeHour, wakeMin)
    val formattedSleep = String.format("%02d:%02d", sleepHour, sleepMin)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Premium visual header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(WaterBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Calendar Optimization",
                    tint = WaterBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CALENDAR & NOTIFICATION",
                color = WaterBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Time-Window Optimisation",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Establish your active daily boundaries. Life OS uses these parameters to filter notifications and optimize schedule layouts.",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Explanation Card of the Two Core Optimizations
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WHAT THIS CONFIGURATION DOES:",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Feature 1",
                            tint = WaterBlue,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "1. Calendar Range Optimization",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hides dormant earlier and later hours in your Day View. Hitting 'Show' reveals extended blocks when required.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Feature 2",
                            tint = WaterBlue,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "2. Smart Sleep Silent Mode",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Blocks pings, system alarms, friend focus alerts, and water reminders during your Sleep Time window automatically.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Wake-Up Time Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WAKE-UP TIME START (DAY VIEW START)",
                        color = WaterBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stepper Widget
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                wakeHour = if (wakeHour == 0) 23 else wakeHour - 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Minus Hour", tint = Color.White)
                        }

                        Text(
                            text = formattedWake,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        IconButton(
                            onClick = {
                                wakeHour = if (wakeHour == 23) 0 else wakeHour + 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Plus Hour", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        listOf(5, 6, 7, 8).forEach { hr ->
                            val active = wakeHour == hr && wakeMin == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) WaterBlue else Color(0xFF141416))
                                    .border(0.5.dp, if (active) WaterBlue else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        wakeHour = hr
                                        wakeMin = 0
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:00", hr),
                                    color = if (active) Color.Black else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sleep Time Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SLEEP TIME START (SILENT MODE DEPLOYMENT)",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stepper Widget
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                sleepHour = if (sleepHour == 0) 23 else sleepHour - 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Minus Hour", tint = Color.White)
                        }

                        Text(
                            text = formattedSleep,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        IconButton(
                            onClick = {
                                sleepHour = if (sleepHour == 23) 0 else sleepHour + 1
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E22))
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Plus Hour", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        listOf(21, 22, 23, 0).forEach { hr ->
                            val active = sleepHour == hr && sleepMin == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) Color(0xFFFF8A80) else Color(0xFF141416))
                                    .border(0.5.dp, if (active) Color(0xFFFF8A80) else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        sleepHour = hr
                                        sleepMin = 0
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:00", hr),
                                    color = if (active) Color.Black else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save and Proceed Button
            Button(
                onClick = {
                    SleepTimeHelper.setWakeUpTime(context, formattedWake)
                    SleepTimeHelper.setSleepTime(context, formattedSleep)
                    viewModel.navigateTo(viewModel.getDefaultScreen())
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = "Apply Configurations")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apply Optimizations & Enter",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
