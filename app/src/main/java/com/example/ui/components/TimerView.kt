package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Task
import com.example.ui.AppViewModel
import com.example.ui.FocusRecord
import com.example.ui.theme.PremiumEffects.bouncyClick
import com.example.ui.theme.PremiumEffects.glassmorphicCard
import com.example.ui.theme.WaterBlue
import com.example.util.FocusTimerManager
import kotlinx.coroutines.delay



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        )
    }
    var isOverlayPermissionDismissed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
                // Calculate and sync focused time again on resume
                com.example.util.FocusTimerManager.forceRecalculateAndSync(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        // Calculate and sync focused time again on enter/tab switch
        com.example.util.FocusTimerManager.forceRecalculateAndSync(context)
    }

    // Navigation & Modal States
    val showHistoryScreen by viewModel.showHistoryScreen.collectAsStateWithLifecycle()
    var showFriendsFocusDetails by remember { mutableStateOf(false) }
    var selectedDateStr by remember { 
        mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) 
    }
    var showCalendarDialog by remember { mutableStateOf(false) }
    val showTaskSelectionDialog by viewModel.showTaskSelectionDialog.collectAsStateWithLifecycle()

    // Configuration and Dynamic States
    val focusTimerDurationMins by viewModel.focusTimerDurationMins.collectAsStateWithLifecycle()
    val isImmersive by viewModel.isTimerImmersive.collectAsStateWithLifecycle()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val selectedTask by viewModel.attachedTask.collectAsStateWithLifecycle()
    val sessionStartTimestamp by viewModel.sessionStartTimestamp.collectAsStateWithLifecycle()
    val focusRecords by viewModel.focusRecords.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()
    val pendingFocusReview by viewModel.pendingFocusReview.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val isTimerSyncInProgress by viewModel.isTimerSyncInProgress.collectAsStateWithLifecycle()
    val lastButtonClicked by viewModel.lastButtonClicked.collectAsStateWithLifecycle()

    // Milestone & Dialog States
    val focusRankPopup by viewModel.focusRankPopup.collectAsStateWithLifecycle()

    val liveGrandTotalSeconds by com.example.util.FocusTimerManager.liveGrandTotalSeconds.collectAsStateWithLifecycle()
    val globalTodaySeconds = liveGrandTotalSeconds

    // Display Custom Date Picker Mini Calendar Dialog
    if (showCalendarDialog) {
        MiniCalendarDialog(
            currentSelectedDateStr = selectedDateStr,
            onDateSelected = { date -> selectedDateStr = date },
            onDismissRequest = { showCalendarDialog = false }
        )
    }

    // Display Achievement rank achievements modal dialog
    focusRankPopup?.let { popupData ->
        FocusRankMilestoneDialog(
            viewModel = viewModel,
            popupData = popupData,
            onDismiss = { viewModel.dismissFocusRankPopup() }
        )
    }

    // Display Task Selection Dialog
    if (showTaskSelectionDialog) {
        TaskSelectionDialog(
            viewModel = viewModel,
            tasks = tasks,
            isTabFocusTimerSelected = viewModel.isTabFocusTimerSelected.value,
            sessionStartTimestamp = sessionStartTimestamp,
            onDismiss = { viewModel.setShowTaskSelectionDialog(false) }
        )
    }

    val showTagSelectionDialog by viewModel.showTagSelectionDialog.collectAsStateWithLifecycle()
    if (showTagSelectionDialog) {
        TagSelectionDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowTagSelectionDialog(false) }
        )
    }

    // Display Friends Focus details list modal
    if (showFriendsFocusDetails) {
        FriendsFocusDetailsDialog(
            viewModel = viewModel,
            onDismiss = { showFriendsFocusDetails = false }
        )
    }

    // Centralized session timer confirm & auto-save controller
    TimerConfirmDialogController(
        viewModel = viewModel,
        focusTimerDurationMins = focusTimerDurationMins,
        selectedTask = selectedTask,
        sessionStartTimestamp = sessionStartTimestamp,
        onSessionStartTimestampChange = { viewModel.setSessionStartTimestamp(it) }
    )

    // Sync timer display seconds remaining with duration modification from Settings
    LaunchedEffect(focusTimerDurationMins) {
        if (!isTimerActive && isFocusPhase) {
            viewModel.setTimerDuration(focusTimerDurationMins)
        }
    }

    if (isImmersive) {
        TimerImmersiveContent(
            viewModel = viewModel,
            focusTimerDurationMins = focusTimerDurationMins,
            onShowFriendsDetails = { showFriendsFocusDetails = true }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(if (isTablet) 16.dp else 4.dp)
        ) {
            // Header Top Bar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (showHistoryScreen) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.setShowHistoryScreen(false) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Timer",
                            tint = WaterBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Back to Timer",
                            color = WaterBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { showCalendarDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF151515))
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select focus date",
                            tint = WaterBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendsFocusPill(
                            viewModel = viewModel,
                            onClick = { showFriendsFocusDetails = true }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isBellSilent by viewModel.isBellSilentModeEnabled.collectAsStateWithLifecycle()
                        IconButton(
                            onClick = { viewModel.setBellSilentModeEnabled(!isBellSilent) },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isBellSilent) Color(0xFFE53935) else Color(0xFF151515))
                                .size(32.dp)
                                .testTag("bell_silent_button")
                        ) {
                            Icon(
                                imageVector = if (isBellSilent) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = "Bell Silent Mode Toggle",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setTimerImmersive(true) },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF151515))
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Enter Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setShowHistoryScreen(true) },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF151515))
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Focus History Overview",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Draw system alert window drawing permission banner
            if (!hasOverlayPermission && !isOverlayPermissionDismissed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("overlay_permission_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Permission Info",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay Widget Enabled",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Allow drawing over other apps to see a floating timer on the screen when minimized.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { isOverlayPermissionDismissed = true },
                            modifier = Modifier.size(28.dp).testTag("dismiss_overlay_permission")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "No I won't",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = showHistoryScreen,
                transitionSpec = {
                    slideInHorizontally { width -> if (targetState) width else -width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> if (targetState) -width else width } + fadeOut()
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "history_transition"
            ) { targetHistory ->
                if (targetHistory) {
                    TimerHistoryView(
                        viewModel = viewModel,
                        selectedDateStr = selectedDateStr
                    )
                } else {
                    TimerLiveControlContent(
                        viewModel = viewModel,
                        isTablet = isTablet,
                        isImmersive = false,
                        isAntiBurnCenteredByTap = true,
                        globalTodaySeconds = globalTodaySeconds,
                        focusTimerDurationMins = focusTimerDurationMins
                    )
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: TimerView_Calendar.kt
// ==========================================================



@Composable
fun MiniCalendarDialog(
    currentSelectedDateStr: String,
    onDateSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sdfInput = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val calendar = remember {
        val cal = java.util.Calendar.getInstance()
        try {
            val d = sdfInput.parse(currentSelectedDateStr)
            if (d != null) cal.time = d
        } catch (_: Exception) {}
        cal
    }

    var currentYear by remember { mutableStateOf(calendar.get(java.util.Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(calendar.get(java.util.Calendar.MONTH)) } // 0-11

    // Calculate days grid
    val daysInMonth = remember(currentYear, currentMonth) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, currentYear)
        cal.set(java.util.Calendar.MONTH, currentMonth)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday...
        val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        Pair(firstDayOfWeek, maxDays)
    }

    val (firstDayOfWeek, maxDays) = daysInMonth
    val monthName = remember(currentMonth) {
        val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        monthNames[currentMonth]
    }

    val WaterBlue = Color(0xFF38BDF8)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header of Calendar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear -= 1
                            } else {
                                currentMonth -= 1
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous Month",
                            tint = WaterBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "$monthName $currentYear",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear += 1
                            } else {
                                currentMonth += 1
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next Month",
                            tint = WaterBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Days of week header labels
                val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekdays.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Days grid
                val totalSlots = 42
                val cols = 7
                val rows = totalSlots / cols

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (c in 0 until cols) {
                                val slotIndex = r * cols + c
                                val dayNumber = slotIndex - (firstDayOfWeek - 2)
                                val isValidDay = dayNumber in 1..maxDays

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isValidDay) {
                                        val dayStr = String.format("%04d-%02d-%02d", currentYear, currentMonth + 1, dayNumber)
                                        val isSelected = dayStr == currentSelectedDateStr

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize(0.85f)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) WaterBlue else Color.Transparent
                                                )
                                                .clickable {
                                                    onDateSelected(dayStr)
                                                    onDismissRequest()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNumber.toString(),
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val todayStrLocal = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            onDateSelected(todayStrLocal)
                            onDismissRequest()
                        }
                    ) {
                        Text("Reset to Today", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Close", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: TimerView_ConfirmDialog.kt
// ==========================================================



@Composable
fun TimerConfirmDialogController(
    viewModel: AppViewModel,
    focusTimerDurationMins: Int,
    selectedTask: Task?,
    sessionStartTimestamp: Long?,
    onSessionStartTimestampChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val showElapsedTimeDialog by viewModel.showElapsedTimeDialog.collectAsStateWithLifecycle()
    val editHoursInput by viewModel.editHoursInput.collectAsStateWithLifecycle()
    val editMinutesInput by viewModel.editMinutesInput.collectAsStateWithLifecycle()
    val editSecondsInput by viewModel.editSecondsInput.collectAsStateWithLifecycle()
    val stopSessionType by viewModel.stopSessionType.collectAsStateWithLifecycle()
    val stoppedElapsedSeconds by viewModel.stoppedElapsedSeconds.collectAsStateWithLifecycle()
    val focusNotesInput by viewModel.focusNotesInput.collectAsStateWithLifecycle()
    val pendingFocusReview by viewModel.pendingFocusReview.collectAsStateWithLifecycle()

    var originalAutoSavedSeconds by remember { mutableStateOf(0) }
    var originalAutoSavedMinutes by remember { mutableStateOf(0) }
    var originalAutoSavedTask by remember { mutableStateOf<Task?>(null) }
    var isAutoSavedSessionActive by remember { mutableStateOf(false) }
    var autoSavedRecordId by remember { mutableStateOf<String?>(null) }

    // Centralized pending focus review effect
    LaunchedEffect(pendingFocusReview) {
        val review = pendingFocusReview
        if (review != null && !showElapsedTimeDialog) {
            val rSeconds = review.durationSeconds
            viewModel.setEditHoursInput(rSeconds / 3600)
            viewModel.setEditMinutesInput((rSeconds % 3600) / 60)
            viewModel.setEditSecondsInput(rSeconds % 60)
            viewModel.setStopSessionType("timer") 
            viewModel.setStoppedElapsedSeconds(rSeconds)
            
            onSessionStartTimestampChange(com.example.util.StableTime.currentTimeMillis() - (rSeconds * 1000L))
            
            isAutoSavedSessionActive = true
            autoSavedRecordId = review.id
            originalAutoSavedSeconds = rSeconds
            originalAutoSavedMinutes = maxOf(1, (rSeconds + 30) / 60)
            originalAutoSavedTask = selectedTask

            viewModel.setShowElapsedTimeDialog(true)
            viewModel.setTimerImmersive(false)
            viewModel.clearPendingFocusReview()
        }
    }

    // Centralized auto-save effect
    LaunchedEffect(showElapsedTimeDialog, stoppedElapsedSeconds) {
        if (showElapsedTimeDialog) {
            val totalSeconds = stoppedElapsedSeconds
            if (totalSeconds > 0) {
                val finalMinutes = if (totalSeconds > 0) maxOf(1, (totalSeconds + 30) / 60) else 0
                viewModel.addFocusMinutes(finalMinutes)
                
                if (stopSessionType == "timer" && finalMinutes >= focusTimerDurationMins) {
                    viewModel.incrementTodayPomos()
                }

                val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
                val startStr = sessionStartTimestamp?.let { formatter.format(java.util.Date(it)) }
                    ?: formatter.format(java.util.Date(System.currentTimeMillis() - totalSeconds * 1000L))
                val endStr = formatter.format(java.util.Date())
                val taskName = selectedTask?.title ?: "Focus Session"

                viewModel.addFocusRecord(startStr, endStr, taskName, finalMinutes, focusNotesInput.trim(), totalSeconds, tag = viewModel.attachedTag.value)

                if (selectedTask != null) {
                    val updated = selectedTask.copy(actualMinutes = selectedTask.actualMinutes + finalMinutes)
                    viewModel.updateTask(updated)
                    viewModel.attachTaskToTimer(updated)
                }
            }

            com.example.util.FocusTimerManager.recordSessionCompleteOrReset(isSaving = true)

            if (stopSessionType == "timer") {
                viewModel.resetTimer(saveSession = false)
            } else {
                viewModel.resetStopwatch(saveSession = false)
            }
            viewModel.clearPendingFocusReview()
            onSessionStartTimestampChange(null)
            viewModel.setShowElapsedTimeDialog(false)
            viewModel.setFocusNotesInput("")
            viewModel.setTimerImmersive(false)

            isAutoSavedSessionActive = false
            autoSavedRecordId = null
            originalAutoSavedSeconds = 0
            originalAutoSavedMinutes = 0
            originalAutoSavedTask = null
        }
    }

    if (showElapsedTimeDialog && false) {
        fun discardElapsedTimeSession() {
            if (isAutoSavedSessionActive) {
                val recordId = autoSavedRecordId
                if (recordId != null) {
                    val records = viewModel.focusRecords.value
                    val originalRecord = records.find { it.id == recordId }
                    if (originalRecord != null) {
                        val durationMinutes = originalRecord.durationMinutes
                        
                        // 1. Subtract the focus minutes
                        viewModel.addFocusMinutes(-durationMinutes)
                        
                        // 2. Subtract task minutes if any task was attached
                        val task = originalAutoSavedTask
                        if (task != null) {
                            val updated = task.copy(actualMinutes = maxOf(0, task.actualMinutes - durationMinutes))
                            viewModel.updateTask(updated)
                            viewModel.attachTaskToTimer(updated)
                        }
                        
                        // 3. Revert Pomodoro count if applicable
                        if (stopSessionType == "timer" && durationMinutes >= focusTimerDurationMins) {
                            viewModel.decrementTodayPomos()
                        }
                        
                        // 4. Delete the record by ID
                        viewModel.deleteFocusRecordById(recordId)
                    }
                }
            }

            com.example.util.FocusTimerManager.recordSessionCompleteOrReset(isSaving = false)

            if (stopSessionType == "timer") {
                viewModel.resetTimer(saveSession = false)
            } else {
                viewModel.resetStopwatch(saveSession = false)
            }
            viewModel.clearPendingFocusReview()
            onSessionStartTimestampChange(null)
            viewModel.setShowElapsedTimeDialog(false)
            viewModel.setFocusNotesInput("")
            viewModel.setTimerImmersive(false)

            isAutoSavedSessionActive = false
            autoSavedRecordId = null
            originalAutoSavedSeconds = 0
            originalAutoSavedMinutes = 0
            originalAutoSavedTask = null
        }

        fun saveAndCloseElapsedTimeSession() {
            val totalSeconds = editHoursInput * 3600 + editMinutesInput * 60 + editSecondsInput
            val finalMinutes = if (totalSeconds > 0) maxOf(1, (totalSeconds + 30) / 60) else 0

            if (isAutoSavedSessionActive) {
                val recordId = autoSavedRecordId
                if (recordId != null) {
                    val records = viewModel.focusRecords.value
                    val originalRecord = records.find { it.id == recordId }
                    if (originalRecord != null) {
                        val updatedRecord = originalRecord.copy(
                            durationMinutes = finalMinutes,
                            durationSeconds = totalSeconds,
                            notes = focusNotesInput.trim()
                        )
                        viewModel.updateFocusRecordById(recordId, updatedRecord)
                        
                        val diffMinutes = finalMinutes - originalAutoSavedMinutes
                        if (diffMinutes != 0) {
                            viewModel.addFocusMinutes(diffMinutes)
                        }
                        
                        val task = originalAutoSavedTask
                        if (task != null) {
                            val updated = task.copy(actualMinutes = task.actualMinutes + diffMinutes)
                            viewModel.updateTask(updated)
                            viewModel.attachTaskToTimer(updated)
                        }
                        
                        if (stopSessionType == "timer") {
                            val wasPomo = originalAutoSavedMinutes >= focusTimerDurationMins
                            val isPomo = finalMinutes >= focusTimerDurationMins
                            if (!wasPomo && isPomo) {
                                viewModel.incrementTodayPomos()
                            }
                        }
                    }
                }
            } else {
                if (totalSeconds > 0) {
                    viewModel.addFocusMinutes(finalMinutes)
                    if (stopSessionType == "timer" && finalMinutes >= focusTimerDurationMins) {
                        viewModel.incrementTodayPomos()
                    }
                    val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
                    val startStr = sessionStartTimestamp?.let { formatter.format(java.util.Date(it)) }
                        ?: formatter.format(java.util.Date(System.currentTimeMillis() - totalSeconds * 1000L))
                    val endStr = formatter.format(java.util.Date())
                    val taskName = selectedTask?.title ?: "Focus Session"

                    viewModel.addFocusRecord(startStr, endStr, taskName, finalMinutes, focusNotesInput.trim(), totalSeconds, tag = viewModel.attachedTag.value)

                    if (selectedTask != null) {
                        val updated = selectedTask.copy(actualMinutes = selectedTask.actualMinutes + finalMinutes)
                        viewModel.updateTask(updated)
                        viewModel.attachTaskToTimer(updated)
                    }
                }
            }

            // Preserve start time and pause ranges before wiping out current session tracking
            com.example.util.FocusTimerManager.recordSessionCompleteOrReset(isSaving = true)

            if (stopSessionType == "timer") {
                viewModel.resetTimer(saveSession = false)
            } else {
                viewModel.resetStopwatch(saveSession = false)
            }
            viewModel.clearPendingFocusReview()
            onSessionStartTimestampChange(null)
            viewModel.setShowElapsedTimeDialog(false)
            viewModel.setFocusNotesInput("")
            viewModel.setTimerImmersive(false)

            isAutoSavedSessionActive = false
            autoSavedRecordId = null
            originalAutoSavedSeconds = 0
            originalAutoSavedMinutes = 0
            originalAutoSavedTask = null

            com.example.util.FocusTimerManager.setGlobalVerificationFocusedTimeSeconds(totalSeconds)
            com.example.util.FocusTimerManager.setGlobalVerificationRevisedTotalMinutes(com.example.util.FocusTimerManager.getTodayFocusMinutes())
            com.example.util.FocusTimerManager.setGlobalVerificationRevisedTotalSeconds(com.example.util.FocusTimerManager.getTodayFocusSeconds())
            if (com.example.util.FocusTimerManager.verifiedSessionStartMs.value == null) {
                com.example.util.FocusTimerManager.setVerifiedSessionStartMs(System.currentTimeMillis() - totalSeconds * 1000L)
            }
            com.example.util.FocusTimerManager.setShowGlobalVerificationDialog(true)
        }

        Dialog(onDismissRequest = { saveAndCloseElapsedTimeSession() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                border = BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Confirmation Needed",
                        tint = WaterBlue,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Confirm Focused Time",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val formattedDurationString = if (stoppedElapsedSeconds >= 3600) {
                        "${stoppedElapsedSeconds / 3600}h ${(stoppedElapsedSeconds % 3600) / 60}m ${stoppedElapsedSeconds % 60}s"
                    } else if (stoppedElapsedSeconds >= 60) {
                        "${stoppedElapsedSeconds / 60}m ${stoppedElapsedSeconds % 60}s"
                    } else {
                        "${stoppedElapsedSeconds}s"
                    }
                    Text(
                        text = "Total Session Focus: $formattedDurationString",
                        color = WaterBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Do you confirm you focused for this much time?",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Hours Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hours", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = { viewModel.setEditHoursInput(editHoursInput + 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                            }
                            Text(
                                text = "$editHoursInput",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { if (editHoursInput > 0) viewModel.setEditHoursInput(editHoursInput - 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Colon 1
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(50.dp))
                            Text(":", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Minutes Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minutes", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = { if (editMinutesInput < 59) viewModel.setEditMinutesInput(editMinutesInput + 1) else viewModel.setEditMinutesInput(0) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                            }
                            Text(
                                text = String.format("%02d", editMinutesInput),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { if (editMinutesInput > 0) viewModel.setEditMinutesInput(editMinutesInput - 1) else viewModel.setEditMinutesInput(59) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Colon 2
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(50.dp))
                            Text(":", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Seconds Column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Seconds", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = { if (editSecondsInput < 59) viewModel.setEditSecondsInput(editSecondsInput + 1) else viewModel.setEditSecondsInput(0) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                            }
                            Text(
                                text = String.format("%02d", editSecondsInput),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { if (editSecondsInput > 0) viewModel.setEditSecondsInput(editSecondsInput - 1) else viewModel.setEditSecondsInput(59) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = focusNotesInput,
                        onValueChange = { viewModel.setFocusNotesInput(it) },
                        label = { Text("What did you focus on?", fontSize = 10.sp) },
                        placeholder = { Text("List tasks, thoughts, or reflections here...", fontSize = 12.sp, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedBorderColor = WaterBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { discardElapsedTimeSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Discard", color = Color.White, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { saveAndCloseElapsedTimeSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Yes, Record", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: TimerView_Dialogs.kt
// ==========================================================



@Composable
fun TagSelectionDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val focusTags by viewModel.focusTags.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select Focus Tag",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose a tag category to classify your focused block",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (focusTags.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.DarkGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No custom tags created yet. You can add them in settings!",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(focusTags) { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .clickable {
                                            viewModel.attachTagToTimer(tag)
                                            onDismiss()
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tag, contentDescription = "Tag", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = tag,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.LightGray),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun TaskSelectionDialog(
    viewModel: AppViewModel,
    tasks: List<Task>,
    isTabFocusTimerSelected: Boolean,
    sessionStartTimestamp: Long?,
    onDismiss: () -> Unit
) {
    var taskSearchQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Choose Focus Target",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select an existing task to link focus times dynamically",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = taskSearchQuery,
                    onValueChange = { taskSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    placeholder = { Text("Search task lists...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = WaterBlue,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedContainerColor = Color(0xFF0F0F0F),
                        unfocusedContainerColor = Color(0xFF0F0F0F)
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val filteredTasks = remember(tasks, taskSearchQuery) {
                    tasks.filter {
                        !it.isCompleted &&
                        (it.title.contains(taskSearchQuery, ignoreCase = true) ||
                         it.description.contains(taskSearchQuery, ignoreCase = true))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (filteredTasks.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.DarkGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (taskSearchQuery.isEmpty()) "No active tasks in system" else "No matching tasks found",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredTasks) { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .clickable {
                                            viewModel.attachTaskToTimer(task)
                                            viewModel.setShowTaskSelectionDialog(false)
                                            if (sessionStartTimestamp == null) {
                                                viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                                                if (isTabFocusTimerSelected) {
                                                    viewModel.startTimer()
                                                } else {
                                                    viewModel.startStopwatch()
                                                }
                                            }
                                            viewModel.setTimerImmersive(true)
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${task.listCategory} • Prior: ${task.priority}",
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF102535))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Linked: ${task.actualMinutes}m",
                                                    color = WaterBlue,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Select", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.attachTaskToTimer(null)
                            viewModel.setShowTaskSelectionDialog(false)
                            if (sessionStartTimestamp == null) {
                                viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                                if (isTabFocusTimerSelected) {
                                    viewModel.startTimer()
                                } else {
                                    viewModel.startStopwatch()
                                }
                            }
                            viewModel.setTimerImmersive(true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("General Focus", fontSize = 12.sp, color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.setShowTaskSelectionDialog(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FocusRankMilestoneDialog(
    viewModel: AppViewModel,
    popupData: com.example.ui.FocusRankPopupData,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("rank_motivation_popup"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
            border = BorderStroke(1.2.dp, WaterBlue.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(WaterBlue.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rank Achievements",
                        tint = WaterBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Daily Focus Milestone",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Calculated comparing your effort yesterday",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F0F), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Yesterday Rank",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "#${popupData.yesterdayRank}",
                            color = WaterBlue,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "out of ${popupData.totalPeersCount} peers",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color(0xFF222222))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Yesterday Focus",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = popupData.yesterdayFocusedTimeStr,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = popupData.motivationalMessage,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WaterBlue.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .border(BorderStroke(0.5.dp, WaterBlue.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dismiss_rank_popup_btn")
                ) {
                    Text(
                        text = "Let's Do It!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: TimerView_FriendHistoryDetails.kt
// ==========================================================



@Composable
fun androidx.compose.foundation.layout.ColumnScope.FriendHistoryDetailsContent(
    viewModel: AppViewModel,
    peer: PeerFocusInfo,
    allUsers: Map<String, com.example.api.UserRemote>,
    selectedFilter: String,
    targetDates: List<String>,
    todayStr: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val WaterBlue = Color(0xFF38BDF8)

    val targetUser = allUsers[peer.username]
    val lastUpdated = targetUser?.lastUpdatedTimestamp ?: 0L
    val lastUpdatedDateStr = if (lastUpdated > 0) {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(lastUpdated))
    } else {
        ""
    }
    val isPeerStale = !peer.isMe && lastUpdatedDateStr.isNotEmpty() && lastUpdatedDateStr != todayStr

    val rawFriendRecords = if (isPeerStale) emptyList() else (targetUser?.todaysFocusRecords ?: emptyList())

    // Retrieve/generate records for all dates in target range
    val friendRecords = remember(peer.username, selectedFilter, rawFriendRecords, isPeerStale) {
        val recordsList = mutableListOf<FocusRecord>()
        val sdfTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())

        val allPeerRecords = if (peer.isMe) {
            emptyList()
        } else {
            FocusTimerManager.loadPeerFocusRecords(context, peer.username)
        }

        val detailDates = targetDates

        detailDates.forEach { dateStr ->
            val isTodayDate = dateStr == todayStr

            if (isTodayDate) {
                if (peer.isMe) {
                    recordsList.addAll(FocusTimerManager.focusRecords.value.filter { it.dateString == todayStr })
                    // Also add live active session if running
                    val isRunning = FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value || FocusTimerManager.accumulatedSessionTimeMs.value > 0L
                    if (isRunning) {
                        val activeSecs = if (FocusTimerManager.isTimerRunning.value) {
                            FocusTimerManager.cumulativeSessionFocusSeconds.value
                        } else {
                            FocusTimerManager.stopwatchSeconds.value
                        }
                        if (activeSecs > 0) {
                            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                            val durationMins = activeSecs / 60
                            val startTimeStr = formatter.format(java.util.Date(System.currentTimeMillis() - activeSecs * 1000L))
                            val endTimeStr = "Now"
                            recordsList.add(
                                FocusRecord(
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    taskTitle = FocusTimerManager.attachedTask.value?.title ?: "Active Session",
                                    durationMinutes = durationMins,
                                    dateString = todayStr,
                                    notes = "In Progress...",
                                    durationSeconds = activeSecs
                                )
                            )
                        }
                    }
                } else if (rawFriendRecords.isNotEmpty()) {
                    recordsList.addAll(rawFriendRecords)
                    // Also add live active session if they are focusing
                    if (targetUser != null && (targetUser.isFocusing == true || targetUser.focusStatus == "paused")) {
                        val lastResume = targetUser.lastResumeTimeMs
                        val startMs = if (lastResume != null) {
                            lastResume - (targetUser.accumulatedTimeMs ?: 0L)
                        } else {
                            System.currentTimeMillis() - (targetUser.accumulatedTimeMs ?: 0L)
                        }
                        val currentChunkMs = if (lastResume != null) {
                            System.currentTimeMillis() - lastResume
                        } else {
                            0L
                        }
                        val totalMs = (targetUser.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                        val activeSecs = (totalMs / 1000).toInt()
                        if (activeSecs > 0) {
                            val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                            val durationMins = activeSecs / 60
                            val startTimeStr = formatter.format(java.util.Date(startMs))
                            val endTimeStr = "Now"
                            recordsList.add(
                                FocusRecord(
                                    startTime = startTimeStr,
                                    endTime = endTimeStr,
                                    taskTitle = targetUser.currentTaskTitle ?: "Active Session",
                                    durationMinutes = durationMins,
                                    dateString = todayStr,
                                    notes = "In Progress...",
                                    durationSeconds = activeSecs,
                                    tag = targetUser.currentTag ?: ""
                                )
                            )
                        }
                    }
                } else {
                    val targetSeconds = if (targetUser != null && !isPeerStale) {
                        val isFocusing = targetUser.isFocusing == true
                        if (isFocusing && targetUser.lastResumeTimeMs != null) {
                            val currentChunkMs = System.currentTimeMillis() - targetUser.lastResumeTimeMs
                            val totalMs = (targetUser.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                            (totalMs / 1000).toInt()
                        } else {
                            ((targetUser.accumulatedTimeMs ?: 0L) / 1000).toInt()
                        }
                    } else 0
                    val targetMinutes = targetSeconds / 60
                    if (targetMinutes > 0) {
                        var remainingMins = targetMinutes
                        var sessionIndex = 1
                        val calendar = java.util.Calendar.getInstance()
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
                        calendar.set(java.util.Calendar.MINUTE, 0)

                        while (remainingMins > 0) {
                            val sessionMins = minOf(remainingMins, if (peer.username == "madhavan") 45 else 25)
                            if (sessionMins <= 0) break

                            val startStr = sdfTime.format(calendar.time)
                            calendar.add(java.util.Calendar.MINUTE, sessionMins)
                            val endStr = sdfTime.format(calendar.time)
                            calendar.add(java.util.Calendar.MINUTE, 10)

                            recordsList.add(
                                FocusRecord(
                                    startTime = startStr,
                                    endTime = endStr,
                                    taskTitle = when (peer.username) {
                                        "madhavan" -> listOf("Writing Compiler Backend", "Optimizing Garbage Collector", "Profiling CPU registers", "Debugging JVM hooks")[sessionIndex % 4]
                                        "shalini" -> listOf("Polishing Custom Canvas Theme", "Designing Flow Architecture", "Writing Screenshot Tests", "Optimizing Vector Drawables")[sessionIndex % 4]
                                        "subash" -> listOf("Project Management Alignment", "Reviewing Sprint Backlog", "Syncing with Product Stakeholders")[sessionIndex % 3]
                                        else -> "Productive Focus Session"
                                    },
                                    durationMinutes = sessionMins,
                                    dateString = dateStr,
                                    notes = "Excellent progress on task goals.",
                                    durationSeconds = sessionMins * 60
                                )
                            )
                            sessionIndex++
                            remainingMins -= sessionMins
                        }
                    }
                }
            } else {
                if (peer.isMe) {
                    recordsList.addAll(FocusTimerManager.focusRecords.value.filter { it.dateString == dateStr })
                } else {
                    val peerRecs = allPeerRecords.filter { it.dateString == dateStr }
                    if (peerRecs.isNotEmpty()) {
                        recordsList.addAll(peerRecs)
                    } else {
                        // Use the identical deterministic random logic to generate simulated seconds
                        val seed = (peer.username + dateStr).hashCode().toLong()
                        val rand = java.util.Random(seed)
                        val targetSeconds = when (peer.username) {
                            "madhavan" -> (6 + rand.nextInt(7)) * 3600 + rand.nextInt(60) * 60
                            "shalini" -> (3 + rand.nextInt(6)) * 3600 + rand.nextInt(60) * 60
                            "subash" -> if (rand.nextBoolean()) (1 + rand.nextInt(4)) * 3600 + rand.nextInt(60) * 60 else 0
                            else -> {
                                if (rand.nextInt(10) < 8) {
                                    (1 + rand.nextInt(7)) * 3600 + rand.nextInt(60) * 60
                                } else {
                                    0
                                }
                            }
                        }
                        val targetMinutes = targetSeconds / 60
                        if (targetMinutes > 0) {
                            var remainingMins = targetMinutes
                            var sessionIndex = 1
                            val calendar = java.util.Calendar.getInstance()
                            val dateParts = dateStr.split("-")
                            if (dateParts.size == 3) {
                                val y = dateParts[0].toIntOrNull() ?: 2026
                                val m = (dateParts[1].toIntOrNull() ?: 6) - 1
                                val d = dateParts[2].toIntOrNull() ?: 24
                                calendar.set(y, m, d, 9, 0, 0)
                            }

                            while (remainingMins > 0) {
                                val sessionMins = minOf(remainingMins, if (peer.username == "madhavan") 45 else 25)
                                if (sessionMins <= 0) break

                                val startStr = sdfTime.format(calendar.time)
                                calendar.add(java.util.Calendar.MINUTE, sessionMins)
                                val endStr = sdfTime.format(calendar.time)
                                calendar.add(java.util.Calendar.MINUTE, 10)

                                recordsList.add(
                                    FocusRecord(
                                        startTime = startStr,
                                        endTime = endStr,
                                        taskTitle = when (peer.username) {
                                            "madhavan" -> listOf("Writing Compiler Backend", "Optimizing Garbage Collector", "Profiling CPU registers", "Debugging JVM hooks")[sessionIndex % 4]
                                            "shalini" -> listOf("Polishing Custom Canvas Theme", "Designing Flow Architecture", "Writing Screenshot Tests", "Optimizing Vector Drawables")[sessionIndex % 4]
                                            "subash" -> listOf("Project Management Alignment", "Reviewing Sprint Backlog", "Syncing with Product Stakeholders")[sessionIndex % 3]
                                            else -> "Productive Focus Session"
                                        },
                                        durationMinutes = sessionMins,
                                        dateString = dateStr,
                                        notes = "Excellent progress on task goals.",
                                        durationSeconds = sessionMins * 60
                                    )
                                )
                                sessionIndex++
                                remainingMins -= sessionMins
                            }
                        }
                    }
                }
            }
        }
        recordsList.sortByDescending { it.startTime }
        recordsList
    }

    // Header with back arrows and close
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to friends list",
                tint = WaterBlue,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        UserAvatar(
            emojiOrBase64 = peer.emoji,
            size = 24.dp,
            fontSize = 20.sp,
            username = peer.username,
            photoUpdatedAt = targetUser?.profile?.photoUpdatedAt ?: 0L
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = peer.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(@${peer.username})",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            val friendStatusText = when (peer.focusStatus) {
                "focusing" -> "Live Focusing Now"
                "paused" -> "Paused"
                "break" -> "On a Break"
                else -> "Currently Idle"
            }
            val friendStatusColor = when (peer.focusStatus) {
                "focusing" -> Color(0xFF2E7D32)
                "paused" -> Color(0xFFFFA726)
                "break" -> Color(0xFF4CAF50)
                else -> Color.Gray
            }
            Text(
                text = friendStatusText,
                color = friendStatusColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close details",
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Chronological graph view (for today's timeline slice)
        DailyFocusTimelineChrono(focusRecords = friendRecords, selectedDateStr = todayStr)

        val friendUser = allUsers[peer.username]
        // Focus activities summary breakdown card
        FocusSummaryCard(
            focusRecords = friendRecords,
            todayStr = todayStr,
            totalFocusMinutes = friendRecords.sumOf { it.durationMinutes },
            liveAddedMinutes = 0,
            liveAddedSeconds = 0,
            activeTimer = friendUser?.activeTimer,
            todayStats = friendUser?.todayStats,
            statsDashboard = friendUser?.stats_dashboard
        )

        // Synced logs list format matching user's page
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            border = BorderStroke(1.dp, Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$selectedFilter Session Logs", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    Text("${friendRecords.size} sessions", color = Color.Gray, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (friendRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No focus records synced for this period",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        friendRecords.forEach { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E1E))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(WaterBlue)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(record.taskTitle, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    Text("${record.startTime} - ${record.endTime}", color = Color.Gray, fontSize = 9.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    LiveRecordDurationText(
                                        record = record,
                                        isFocusing = peer.isFocusing,
                                        isMe = peer.isMe,
                                        peerRemote = targetUser
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back", color = Color.White)
        }
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LiveRecordDurationText(
    record: FocusRecord,
    isFocusing: Boolean,
    isMe: Boolean,
    peerRemote: com.example.api.UserRemote?
) {
    if (record.endTime != "Now") {
        Text(
            text = formatRecordDuration(record.durationSeconds, record.durationMinutes),
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        return
    }

    var durationSeconds by remember(record) {
        mutableStateOf(record.durationSeconds)
    }

    LaunchedEffect(record, isFocusing, isMe, peerRemote) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            if (isMe) {
                val currentChunkMs = FocusTimerManager.getCurrentChunkMs()
                val totalMs = FocusTimerManager.accumulatedSessionTimeMs.value + currentChunkMs
                durationSeconds = (totalMs / 1000).toInt()
            } else if (peerRemote != null) {
                val currentChunkMs = if (peerRemote.lastResumeTimeMs != null) {
                    System.currentTimeMillis() - peerRemote.lastResumeTimeMs
                } else {
                    0L
                }
                val totalMs = (peerRemote.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                durationSeconds = (totalMs / 1000).toInt()
            }
        }
    }

    Text(
        text = formatRecordDuration(durationSeconds, durationSeconds / 60) + " (In Progress)",
        color = Color(0xFF38BDF8),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold
    )
}

// ==========================================================
// MERGED FROM: TimerView_FriendsFocus.kt
// ==========================================================



data class PeerFocusInfo(
    val username: String,
    val displayName: String,
    val emoji: String,
    val isFocusing: Boolean,
    val liveFocusedSeconds: Int,
    val currentTask: String?,
    val currentTag: String? = null,
    val isMe: Boolean = false,
    val focusStatus: String = "idle"
)

@Composable
fun FriendsFocusPill(
    viewModel: AppViewModel,
    onClick: () -> Unit
) {
    val currentMeUsername by viewModel.currentUsername.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val isTimerActive by viewModel.isTimerRunning.collectAsState()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsState()
    val isMeFocusing = isTimerActive || isStopwatchActive
    val meUser by viewModel.currentUserRemote.collectAsState()

    // Filter active users who are focusing and dynamically include me if focusing locally
    val focusingUsers = remember(allUsers, currentMeUsername, isMeFocusing, meUser) {
        val list = allUsers.filter {
            it.value.isFocusing == true && 
            it.key != "admin"
        }.toMutableMap()
        
        val myUsername = currentMeUsername
        if (isMeFocusing && !myUsername.isNullOrEmpty()) {
            val myRemoteUser = meUser ?: allUsers[myUsername] ?: com.example.api.UserRemote(emoji = "👨‍💻")
            list[myUsername] = myRemoteUser.copy(isFocusing = true)
        }
        list
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(width = 0.8.dp, color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("friends_focus_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (focusingUsers.isEmpty()) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "No one focusing",
                    tint = Color.LightGray.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "No one is focusing",
                    color = Color.LightGray.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    focusingUsers.forEach { (username, user) ->
                        UserAvatar(
                            emojiOrBase64 = user.emoji,
                            fontSize = 14.sp,
                            size = 20.dp,
                            username = username,
                            photoUpdatedAt = user.profile?.photoUpdatedAt ?: 0L
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendsFocusDetailsDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allUsers by viewModel.allUsers.collectAsState()
    val showElapsedTimeDialog by viewModel.showElapsedTimeDialog.collectAsState()

    val GoldRank = Color(0xFFFFD700)
    val SilverRank = Color(0xFFC0C0C0)
    val BronzeRank = Color(0xFFCD7F32)
    val WaterBlue = Color(0xFF38BDF8)

    var selectedFilter by remember { mutableStateOf("Today") }
    var filterExpanded by remember { mutableStateOf(false) }
    val filterOptions = listOf("Today", "Past 7 Days", "Past 30 Days", "All Time")

    val todayStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val days = when (selectedFilter) {
        "Today" -> 1
        "Past 7 Days" -> 7
        "Past 30 Days" -> 30
        "All Time" -> 365
        else -> 1
    }

    val targetDates = remember(selectedFilter, todayStr) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val dates = mutableListOf<String>()
        val cal = java.util.Calendar.getInstance()
        for (i in 0 until days) {
            dates.add(sdf.format(cal.time))
            cal.add(java.util.Calendar.DATE, -1)
        }
        dates
    }

    fun getSecondsForDate(
        username: String,
        dateStr: String,
        isMe: Boolean,
        peerRemote: com.example.api.UserRemote?,
        currentUnixTime: Long
    ): Int {
        if (dateStr == todayStr) {
            if (isMe) {
                val isLocalFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value && FocusTimerManager.pendingFocusReview.value == null
                if (isLocalFocusing) {
                    val completedTodaySecs = FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
                    val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0
                    val activeSessionOverlap = if (isLocalFocusing) {
                        val startMs = viewModel.sessionStartTimestamp.value
                        if (startMs != null) {
                            FocusTimerManager.getActiveSessionOverlapSeconds(startMs, todayStr)
                        } else {
                            val currentChunkMs = FocusTimerManager.getCurrentChunkMs()
                            val totalMs = FocusTimerManager.accumulatedSessionTimeMs.value + currentChunkMs
                            (totalMs / 1000).toInt()
                        }
                    } else 0
                    return completedTodaySecs + pendingSecs + activeSessionOverlap
                } else if (peerRemote != null) {
                    val isRemoteFocusing = peerRemote.isFocusing == true
                    val liveFocusedSeconds = if (isRemoteFocusing && peerRemote.lastResumeTimeMs != null) {
                        val currentChunkMs = (currentUnixTime * 1000) - peerRemote.lastResumeTimeMs!!
                        val totalMs = (peerRemote.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                        (totalMs / 1000).toInt()
                    } else {
                        ((peerRemote.accumulatedTimeMs ?: 0L) / 1000).toInt()
                    }
                    val completedTodaySecs = peerRemote.todaysFocusRecords?.sumOf { 
                        FocusTimerManager.getOverlapSecondsForDate(it, todayStr) 
                    } ?: 0
                    return liveFocusedSeconds + completedTodaySecs
                } else {
                    val completedTodaySecs = FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) }
                    return completedTodaySecs
                }
            } else {
                if (peerRemote != null) {
                    val lastUpdated = peerRemote.lastUpdatedTimestamp ?: 0L
                    val lastUpdatedDateStr = if (lastUpdated > 0) {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(lastUpdated))
                    } else {
                        ""
                    }
                    if (lastUpdatedDateStr.isNotEmpty() && lastUpdatedDateStr != todayStr) {
                        return 0
                    }

                    val isFocusing = peerRemote.isFocusing == true
                    
                    // 1. Calculate the Temporary Bank (Live Session)
                    val liveFocusedSeconds = if (isFocusing && peerRemote.lastResumeTimeMs != null) {
                        val currentChunkMs = (currentUnixTime * 1000) - peerRemote.lastResumeTimeMs!!
                        val totalMs = (peerRemote.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                        (totalMs / 1000).toInt()
                    } else {
                        ((peerRemote.accumulatedTimeMs ?: 0L) / 1000).toInt()
                    }
                    
                    // 2. Add the Permanent Vault (Completed Records)
                    val completedTodaySecs = peerRemote.todaysFocusRecords?.sumOf { 
                        FocusTimerManager.getOverlapSecondsForDate(it, todayStr) 
                    } ?: 0
                    
                    return liveFocusedSeconds + completedTodaySecs
                } else {
                    return 0
                }
            }
        } else {
            if (isMe) {
                return FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, dateStr) }
            } else {
                val peerRecords = FocusTimerManager.loadPeerFocusRecords(context, username)
                return peerRecords.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, dateStr) }
            }
        }
    }

    fun formatFocusedSecondsForFilter(seconds: Int, filter: String): String {
        if (filter == "Today") {
            return formatLiveSeconds(seconds)
        } else {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            return if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }
        }
    }

    val currentMeUsername = viewModel.currentUsername.collectAsState().value ?: "me_user"
    val myName = viewModel.currentUserRemote.collectAsState().value?.nickname
        ?: viewModel.currentUserRemote.collectAsState().value?.name
        ?: "Bharathikrishna M"
    val myEmoji = viewModel.currentUserRemote.collectAsState().value?.emoji ?: "👨‍💻"

    val participantInfos = remember(allUsers, selectedFilter, targetDates, currentMeUsername, myName, myEmoji, showElapsedTimeDialog) {
        val keys = mutableSetOf<String>()
        keys.add(currentMeUsername)
        allUsers.forEach { (username, user) ->
            if (username != "admin" && 
                username != currentMeUsername
            ) {
                keys.add(username)
            }
        }

        keys.map { username ->
            val isMe = username == currentMeUsername
            val peerRemote = allUsers[username]

            var totalFilterSeconds = 0
            val listDates = targetDates
            listDates.forEach { dateStr ->
                totalFilterSeconds += getSecondsForDate(
                    username = username,
                    dateStr = dateStr,
                    isMe = isMe,
                    peerRemote = peerRemote,
                    currentUnixTime = System.currentTimeMillis() / 1000
                )
            }

            val displayName = if (isMe) {
                myName
            } else if (peerRemote != null) {
                peerRemote.nickname ?: peerRemote.name ?: username
            } else {
                when (username) {
                    "madhavan" -> "Madhavan Sethuraman"
                    "shalini" -> "Shalini Krishnan"
                    "subash" -> "Subash E"
                    else -> username
                }
            }

            val emoji = if (isMe) {
                myEmoji
            } else if (peerRemote != null) {
                peerRemote.emoji ?: "🎯"
            } else {
                when (username) {
                    "madhavan" -> "👨‍💻"
                    "shalini" -> "👩‍💻"
                    "subash" -> "👨‍💼"
                    else -> "🎯"
                }
            }

            val peerLastUpdated = peerRemote?.lastUpdatedTimestamp ?: 0L
            val peerLastUpdatedDateStr = if (peerLastUpdated > 0) {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(peerLastUpdated))
            } else {
                ""
            }
            val isPeerStale = !isMe && peerLastUpdatedDateStr.isNotEmpty() && peerLastUpdatedDateStr != todayStr

            val isFocusing = if (isPeerStale) false else (peerRemote?.focusStatus == "focusing" || peerRemote?.isFocusing == true)

            val focusStatus = if (isPeerStale) "idle" else (peerRemote?.focusStatus ?: (if (peerRemote?.isFocusing == true) "focusing" else "idle"))

            val currentTask = if (isMe) {
                FocusTimerManager.attachedTask.value?.title
            } else {
                if (isPeerStale) null else peerRemote?.currentTaskTitle
            }

            val currentTag = if (isMe) {
                FocusTimerManager.attachedTag.value.takeIf { it.isNotEmpty() }
            } else {
                if (isPeerStale) null else peerRemote?.currentTag
            }

            PeerFocusInfo(
                username = username,
                displayName = displayName,
                emoji = emoji,
                isFocusing = isFocusing,
                liveFocusedSeconds = totalFilterSeconds,
                currentTask = currentTask,
                currentTag = currentTag,
                isMe = isMe,
                focusStatus = focusStatus
            )
        }.sortedByDescending { it.liveFocusedSeconds }
    }

    var selectedFriendForHistory by remember { mutableStateOf<PeerFocusInfo?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f).fillMaxHeight(0.95f)
                .padding(16.dp)
                .testTag("friends_focus_details_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                if (selectedFriendForHistory == null) {
                    // Title and Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Friends Focus Details",
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Friends Focus Details",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close details",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Period Selection Dropdown
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Period Range:",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .clickable { filterExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedFilter,
                                    color = WaterBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select range",
                                    tint = WaterBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = filterExpanded,
                                onDismissRequest = { filterExpanded = false },
                                modifier = Modifier
                                    .background(Color(0xFF141414))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            ) {
                                filterOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option,
                                                color = if (selectedFilter == option) WaterBlue else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (selectedFilter == option) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedFilter = option
                                            filterExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (participantInfos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No other users registered",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(participantInfos) { index, peer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (peer.isFocusing) WaterBlue.copy(alpha = 0.08f)
                                            else Color.White.copy(alpha = 0.03f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (peer.isFocusing) WaterBlue.copy(alpha = 0.25f) else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rank Number Badge
                                    Text(
                                        text = "#${index + 1}",
                                        color = when (index) {
                                            0 -> GoldRank
                                            1 -> SilverRank
                                            2 -> BronzeRank
                                            else -> Color.Gray
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp)
                                    )

                                    // Clickable Participant Row
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedFriendForHistory = peer },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Emoji / Photo
                                        UserAvatar(
                                            emojiOrBase64 = peer.emoji,
                                            size = 36.dp,
                                            fontSize = 18.sp,
                                            username = peer.username,
                                            photoUpdatedAt = allUsers[peer.username]?.profile?.photoUpdatedAt ?: 0L
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Name and task details
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (peer.isMe) "${peer.displayName} (You)" else peer.displayName,
                                                    color = if (peer.isMe) WaterBlue else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (!peer.isMe) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "@${peer.username}",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                }
                                                if (peer.focusStatus == "focusing") {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFF2E7D32))
                                                    )
                                                }
                                            }

                                            val subtitleText = when (peer.focusStatus) {
                                                "focusing" -> peer.currentTask?.let { "Focusing on: $it" } ?: "Focusing"
                                                "paused" -> peer.currentTask?.let { "Paused: $it" } ?: "Paused"
                                                "break" -> "On a Break"
                                                else -> "Idle"
                                            }

                                            val subtitleColor = when (peer.focusStatus) {
                                                "focusing" -> WaterBlue.copy(alpha = 0.8f)
                                                "paused" -> Color(0xFFFFA726).copy(alpha = 0.8f)
                                                "break" -> Color(0xFF66BB6A).copy(alpha = 0.8f)
                                                else -> Color.Gray
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = subtitleText,
                                                    color = subtitleColor,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                if (!peer.currentTag.isNullOrBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(WaterBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                            .border(0.5.dp, WaterBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = peer.currentTag,
                                                            color = WaterBlue,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Focus Time Summed Over Range
                                    val focusTimeColor = when (peer.focusStatus) {
                                        "focusing" -> WaterBlue
                                        "paused" -> Color(0xFFFFA726)
                                        "break" -> Color(0xFF66BB6A)
                                        else -> Color.LightGray
                                    }
                                    LiveDurationText(
                                        viewModel = viewModel,
                                        baseSeconds = peer.liveFocusedSeconds,
                                        isFocusing = peer.isFocusing,
                                        isMe = peer.isMe,
                                        peerRemote = allUsers[peer.username],
                                        filter = selectedFilter
                                    )

                                    if (!peer.isMe && !peer.isFocusing) {
                                        Spacer(modifier = Modifier.width(8.dp))

                                        val remainingCooldown = viewModel.getBellCooldownRemaining(peer.username)
                                        val isOnCooldown = remainingCooldown > 0

                                        IconButton(
                                            onClick = {
                                                viewModel.ringFriendBell(
                                                    targetUsername = peer.username,
                                                    onSuccess = {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Rang focus bell for ${peer.displayName}! 🔔",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    onError = { error ->
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            error,
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            },
                                            modifier = Modifier.size(24.dp),
                                            enabled = !isOnCooldown
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = "Remind friend to focus",
                                                tint = if (isOnCooldown) Color.Gray.copy(alpha = 0.5f) else WaterBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    FriendHistoryDetailsContent(
                        viewModel = viewModel,
                        peer = selectedFriendForHistory!!,
                        allUsers = allUsers,
                        selectedFilter = selectedFilter,
                        targetDates = targetDates,
                        todayStr = todayStr,
                        onBack = { selectedFriendForHistory = null },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun LiveDurationText(
    viewModel: AppViewModel,
    baseSeconds: Int,
    isFocusing: Boolean,
    isMe: Boolean,
    peerRemote: com.example.api.UserRemote?,
    filter: String
) {
    val systemTodayStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }

    var liveSeconds by remember(baseSeconds, isFocusing, isMe, peerRemote) {
        val initialSecs = if (isMe) {
            val isLocalFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value && FocusTimerManager.pendingFocusReview.value == null
            if (isLocalFocusing) {
                baseSeconds
            } else if (isFocusing && peerRemote != null) {
                val currentUnixTime = System.currentTimeMillis() / 1000
                val completedTodaySecs = peerRemote.todaysFocusRecords?.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
                if (peerRemote.lastResumeTimeMs != null) {
                    val currentChunkMs = (currentUnixTime * 1000) - peerRemote.lastResumeTimeMs!!
                    val totalMs = (peerRemote.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                    completedTodaySecs + (totalMs / 1000).toInt()
                } else {
                    completedTodaySecs + ((peerRemote.accumulatedTimeMs ?: 0L) / 1000).toInt()
                }
            } else {
                baseSeconds
            }
        } else if (isFocusing && peerRemote != null) {
            val currentUnixTime = System.currentTimeMillis() / 1000
            val completedTodaySecs = peerRemote.todaysFocusRecords?.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
            
            if (peerRemote.lastResumeTimeMs != null) {
                val currentChunkMs = (currentUnixTime * 1000) - peerRemote.lastResumeTimeMs!!
                val totalMs = (peerRemote.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                completedTodaySecs + (totalMs / 1000).toInt()
            } else {
                completedTodaySecs + ((peerRemote.accumulatedTimeMs ?: 0L) / 1000).toInt()
            }
        } else {
            baseSeconds
        }
        mutableStateOf(initialSecs)
    }

    LaunchedEffect(isFocusing, isMe, peerRemote) {
        if (isFocusing) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                val currentUnixTime = System.currentTimeMillis() / 1000
                if (isMe) {
                    val isLocalFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value && FocusTimerManager.pendingFocusReview.value == null
                    if (isLocalFocusing) {
                        val completedTodaySecs = FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) }
                        val pendingSecs = FocusTimerManager.pendingFocusReview.value?.let { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
                        val startMs = viewModel.sessionStartTimestamp.value
                        val activeSessionOverlap = if (startMs != null) {
                            FocusTimerManager.getActiveSessionOverlapSeconds(startMs, systemTodayStr)
                        } else {
                            val currentChunkMs = FocusTimerManager.getCurrentChunkMs()
                            val totalMs = FocusTimerManager.accumulatedSessionTimeMs.value + currentChunkMs
                            (totalMs / 1000).toInt()
                        }
                        liveSeconds = completedTodaySecs + pendingSecs + activeSessionOverlap
                    } else if (peerRemote != null) {
                        val completedTodaySecs = peerRemote.todaysFocusRecords?.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
                        if (peerRemote.lastResumeTimeMs != null) {
                            val currentChunkMs = (currentUnixTime * 1000) - peerRemote.lastResumeTimeMs!!
                            val totalMs = (peerRemote.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                            liveSeconds = completedTodaySecs + (totalMs / 1000).toInt()
                        } else {
                            liveSeconds = completedTodaySecs + ((peerRemote.accumulatedTimeMs ?: 0L) / 1000).toInt()
                        }
                    } else {
                        val completedTodaySecs = FocusTimerManager.focusRecords.value.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) }
                        liveSeconds = completedTodaySecs
                    }
                } else if (peerRemote != null) {
                    val completedTodaySecs = peerRemote.todaysFocusRecords?.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, systemTodayStr) } ?: 0
                    if (peerRemote.lastResumeTimeMs != null) {
                        val currentChunkMs = (currentUnixTime * 1000) - peerRemote.lastResumeTimeMs!!
                        val totalMs = (peerRemote.accumulatedTimeMs ?: 0L) + maxOf(0L, currentChunkMs)
                        liveSeconds = completedTodaySecs + (totalMs / 1000).toInt()
                    } else {
                        liveSeconds = completedTodaySecs + ((peerRemote.accumulatedTimeMs ?: 0L) / 1000).toInt()
                    }
                }
            }
        }
    }

    Text(
        text = if (filter == "Today") formatLiveSeconds(liveSeconds) else {
            val hours = liveSeconds / 3600
            val minutes = (liveSeconds % 3600) / 60
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        },
        color = if (isFocusing) Color(0xFF38BDF8) else Color.LightGray,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
}

fun formatLiveSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
    }
}

fun formatRecordDuration(durationSeconds: Int, durationMinutes: Int): String {
    val secs = if (durationSeconds > 0) durationSeconds else durationMinutes * 60
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) {
        String.format(java.util.Locale.getDefault(), "%dh %dm %ds", h, m, s)
    } else if (m > 0) {
        String.format(java.util.Locale.getDefault(), "%dm %ds", m, s)
    } else {
        String.format(java.util.Locale.getDefault(), "%ds", s)
    }
}

// ==========================================================
// MERGED FROM: TimerView_History.kt
// ==========================================================



@Composable
fun TimerHistoryView(
    viewModel: AppViewModel,
    selectedDateStr: String,
    modifier: Modifier = Modifier
) {
    // State for editing focus session logs
    var editingLogId by remember { mutableStateOf<String?>(null) }
    var showEditLogDialog by remember { mutableStateOf(false) }

    // State for manual focus entry requests
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var manualMinutesInput by remember { mutableStateOf("") }
    var manualReasonInput by remember { mutableStateOf("") }

    var editTaskTitle by remember { mutableStateOf("") }
    var editStartTime by remember { mutableStateOf("") }
    var editEndTime by remember { mutableStateOf("") }
    var editDurationMins by remember { mutableStateOf("") }
    var editDateString by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }
    var editTag by remember { mutableStateOf("") }

    // SEPARATE OVERVIEW AND FOCUS HISTORY PAGE
    var historySubTab by remember { mutableStateOf(0) } // 0 = Focus History, 1 = System Audit Logs
    val auditLogs by FocusTimerManager.systemLogs.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(currentUsername) {
        val username = currentUsername
        if (!username.isNullOrEmpty()) {
            val prefs = context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
            val lastSync = prefs.getLong("history_logs_last_sync_${username}", 0L)
            viewModel.syncHistoryLogs(username, lastSync) { newSyncTime ->
                prefs.edit().putLong("history_logs_last_sync_${username}", newSyncTime).apply()
            }
        }
    }

    val focusRecords by viewModel.focusRecords.collectAsStateWithLifecycle()
    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val pendingFocusReview by viewModel.pendingFocusReview.collectAsStateWithLifecycle()
    val totalFocusMinutes by viewModel.totalFocusMinutes.collectAsStateWithLifecycle()
    val sessionStartTimestamp by viewModel.sessionStartTimestamp.collectAsStateWithLifecycle()
    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()

    val WaterBlue = Color(0xFF38BDF8)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Subtab Selection Segmented Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F12), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { historySubTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (historySubTab == 0) Color(0xFF1E1E24) else Color.Transparent,
                    contentColor = if (historySubTab == 0) Color.White else Color.Gray
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Focus History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { historySubTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (historySubTab == 1) Color(0xFF1E1E24) else Color.Transparent,
                    contentColor = if (historySubTab == 1) Color.White else Color.Gray
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("System Audit Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (historySubTab == 1) {
            // Render System Audit Logs Terminal View
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D10)),
                border = BorderStroke(1.dp, Color(0xFF1A1A22)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Security & Audit Engine", fontWeight = FontWeight.Black, color = Color.White, fontSize = 13.sp)
                            Text("Verifying timer calculations, events, and cloud state saves", color = Color.Gray, fontSize = 9.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    viewModel.triggerManualAlignmentCheck()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24), contentColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Align Cloud", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.clearAuditLogs()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221111), contentColor = Color(0xFFFF5555)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Clear Logs", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1A22)))

                    if (auditLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No audit logs recorded yet.", color = Color.DarkGray, fontSize = 11.sp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            auditLogs.forEach { log ->
                                val catColor = when (log.category) {
                                    "BUTTON_PRESS" -> Color(0xFF00ACC1) // Cyan
                                    "FIREBASE_SYNC" -> Color(0xFFFB8C00) // Orange
                                    "STATE_RESTORE" -> Color(0xFF8E24AA) // Purple
                                    "ALARM" -> Color(0xFFE53935) // Red
                                    "SYSTEM" -> Color(0xFF43A047) // Green
                                    else -> Color.Gray
                                }
                                val timeStr = java.text.SimpleDateFormat("hh:mm:ss.SSS a", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF131317), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFF1F1F24), RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.event.uppercase(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = log.category,
                                            color = catColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .background(catColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                .border(1.dp, catColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = log.details,
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "TIMESTAMP: $timeStr",
                                        color = Color.DarkGray,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Daily Chronological Focus Timeline Grid
            DailyFocusTimelineChrono(focusRecords = focusRecords, selectedDateStr = selectedDateStr)

            val localLiveAddedSeconds = remember(isFocusPhase, cumulativeSessionFocusSeconds, stopwatchSeconds, pendingFocusReview) {
                val running = if (isFocusPhase && pendingFocusReview == null) {
                    (cumulativeSessionFocusSeconds + stopwatchSeconds)
                } else 0
                val pending = pendingFocusReview?.durationSeconds ?: ((pendingFocusReview?.durationMinutes ?: 0) * 60)
                running + pending
            }

            val meUser = viewModel.currentUserRemote.collectAsState().value
            // Focus Activity Summary Breakdown Card
            FocusSummaryCard(
                focusRecords = focusRecords,
                todayStr = selectedDateStr,
                totalFocusMinutes = totalFocusMinutes,
                liveAddedMinutes = localLiveAddedSeconds / 60,
                liveAddedSeconds = localLiveAddedSeconds,
                activeTimer = meUser?.activeTimer,
                todayStats = meUser?.todayStats,
                statsDashboard = meUser?.stats_dashboard
            )

            val completedSecs = remember(focusRecords, selectedDateStr) {
                focusRecords.sumOf { FocusTimerManager.getOverlapSecondsForDate(it, selectedDateStr) }
            }

            val pendingSecs = remember(pendingFocusReview, selectedDateStr) {
                pendingFocusReview?.let { FocusTimerManager.getOverlapSecondsForDate(it, selectedDateStr) } ?: 0
            }

            val myTodaySeconds = remember(completedSecs, pendingSecs, selectedDateStr, isFocusPhase, sessionStartTimestamp, pendingFocusReview, isTimerActive, isStopwatchActive) {
                val activeSecs = if (isFocusPhase && pendingFocusReview == null) {
                    if ((isTimerActive || isStopwatchActive) && sessionStartTimestamp != null) {
                        FocusTimerManager.getActiveSessionOverlapSeconds(sessionStartTimestamp!!, selectedDateStr)
                    } else {
                        cumulativeSessionFocusSeconds + stopwatchSeconds
                    }
                } else {
                    0
                }
                completedSecs + pendingSecs + activeSecs
            }

            // Friends Focus Details Table / Leaderboard
            FriendsFocusLeaderboardTable(
                viewModel = viewModel,
                selectedDateStr = selectedDateStr,
                myTodaySeconds = myTodaySeconds
            )

            // Session Logs
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text("Session Log history", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showManualEntryDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f), contentColor = WaterBlue),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("log_focus_manually_button")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Focus", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${focusRecords.size} sessions", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        focusRecords.forEachIndexed { index, record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF161616))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Bullet
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(WaterBlue)
                                )
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = record.taskTitle,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (record.tag.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(WaterBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .border(1.dp, WaterBlue.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            ) {
                                                Text(
                                                    text = record.tag,
                                                    color = WaterBlue,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (record.mode.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF222222), RoundedCornerShape(4.dp))
                                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                            ) {
                                                Text(
                                                    text = record.mode,
                                                    color = Color.LightGray,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    // Start - End timestamps
                                    val timeDisplay = if (record.startTime.isNotEmpty() && record.endTime.isNotEmpty()) {
                                        "${record.startTime} - ${record.endTime}"
                                    } else {
                                        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                        "Manual Log • " + sdf.format(java.util.Date(record.timestamp))
                                    }
                                    Text(
                                        text = timeDisplay,
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }

                                // Duration pill badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF222222))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${record.durationMinutes} min",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog to submit manual focus entry request
    if (showManualEntryDialog) {
        AlertDialog(
            onDismissRequest = { showManualEntryDialog = false },
            title = {
                Text(
                    "Submit Manual Focus Entry",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Submit a request to manually log focus time. Once approved, the time will be automatically synchronized.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    OutlinedTextField(
                        value = manualMinutesInput,
                        onValueChange = { manualMinutesInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Focus Minutes", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manual_minutes_input")
                    )
                    OutlinedTextField(
                        value = manualReasonInput,
                        onValueChange = { manualReasonInput = it },
                        label = { Text("Reason / Task Name", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manual_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = manualMinutesInput.toIntOrNull() ?: 0
                        if (mins > 0) {
                            val username = currentUsername ?: ""
                            
                            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            val todayRecords = com.example.util.FocusTimerManager.focusRecords.value.filter { it.dateString == todayStr }
                            val manualRecords = todayRecords.filter { it.startTime.isEmpty() || it.endTime.isEmpty() }
                            
                            if (manualRecords.size >= 3) {
                                android.widget.Toast.makeText(context, "⚠️ Limit Exceeded: Max 3 manual entries allowed per day.", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            
                            val manualMinsSum = manualRecords.sumOf { it.durationMinutes }
                            if (manualMinsSum + mins > 240) {
                                val remaining = maxOf(0, 240 - manualMinsSum)
                                android.widget.Toast.makeText(context, "⚠️ Limit Exceeded: Manual focus limit is 4 hours (240 mins) per day. You have logged $manualMinsSum mins. Remaining: $remaining mins.", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            viewModel.submitManualEntry(username, mins, manualReasonInput)
                            showManualEntryDialog = false
                            manualMinutesInput = ""
                            manualReasonInput = ""
                        } else {
                            android.widget.Toast.makeText(context, "Please enter valid minutes", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("manual_submit_button")
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualEntryDialog = false },
                    modifier = Modifier.testTag("manual_cancel_button")
                ) {
                    Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Dialog to Edit Focus Session Details
    if (showEditLogDialog && editingLogId != null) {
        AlertDialog(
            onDismissRequest = { showEditLogDialog = false },
            title = {
                Text(
                    "Edit Focus Session",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            containerColor = Color(0xFF161616),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Modify the details for this focus history session:", color = Color.Gray, fontSize = 11.sp)

                    Text("Task / Tag Title", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editTaskTitle,
                        onValueChange = { editTaskTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Time", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editStartTime,
                                onValueChange = { editStartTime = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("End Time", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editEndTime,
                                onValueChange = { editEndTime = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Duration (Mins)", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editDurationMins,
                                onValueChange = { editDurationMins = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Date (yyyy-MM-dd)", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editDateString,
                                onValueChange = { editDateString = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF0F0F0F),
                                    unfocusedContainerColor = Color(0xFF0F0F0F)
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Text("Tag / Category", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editTag,
                        onValueChange = { editTag = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text("Notes", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WaterBlue,
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minsParsed = editDurationMins.trim().toIntOrNull() ?: 0
                        val updated = FocusRecord(
                            startTime = editStartTime.trim(),
                            endTime = editEndTime.trim(),
                            taskTitle = editTaskTitle.trim(),
                            durationMinutes = minsParsed,
                            dateString = editDateString.trim(),
                            notes = editNotes.trim(),
                            durationSeconds = minsParsed * 60,
                            tag = editTag.trim(),
                            id = editingLogId!!
                        )
                        viewModel.updateFocusRecordById(editingLogId!!, updated)
                        showEditLogDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.deleteFocusRecordById(editingLogId!!)
                            showEditLogDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showEditLogDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222), contentColor = Color.LightGray)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

// ==========================================================
// MERGED FROM: TimerView_Immersive.kt
// ==========================================================



@Composable
fun TimerImmersiveContent(
    viewModel: AppViewModel,
    focusTimerDurationMins: Int,
    onShowFriendsDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVerticalPhone = configuration.screenWidthDp < 600

    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val timerSecondsRemaining by viewModel.timerSecondsLeft.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()
    val isTabFocusTimerSelected by viewModel.isTabFocusTimerSelected.collectAsStateWithLifecycle()
    val wasStartedFromStopwatch by viewModel.wasStartedFromStopwatch.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val selectedTask by viewModel.attachedTask.collectAsStateWithLifecycle()
    val timerDisplayMode by viewModel.timerDisplayMode.collectAsStateWithLifecycle()
    val isTimerSyncInProgress by viewModel.isTimerSyncInProgress.collectAsStateWithLifecycle()
    val lastButtonClicked by viewModel.lastButtonClicked.collectAsStateWithLifecycle()

    val motivationalQuoteEnabled by viewModel.focusMotivationalQuoteEnabled.collectAsStateWithLifecycle()
    val quoteIntervalMins by viewModel.focusMotivationalQuoteIntervalMins.collectAsStateWithLifecycle()
    val currentQuote by viewModel.currentQuote.collectAsStateWithLifecycle()

    var areControlsVisible by remember { mutableStateOf(true) }
    var isAntiBurnCenteredByTap by remember { mutableStateOf(false) }
    var interactionCounter by remember { mutableStateOf(0) }

    val minutesElapsedTotal = (System.currentTimeMillis() / 60000).toInt()
    val periodIndex = (minutesElapsedTotal / 5) % 4

    LaunchedEffect(viewModel, motivationalQuoteEnabled, quoteIntervalMins) {
        if (motivationalQuoteEnabled) {
            if (viewModel.currentQuote.value.isEmpty()) {
                viewModel.triggerNextMotivationalQuote()
            }
            while (true) {
                delay(quoteIntervalMins * 60 * 1000L)
                viewModel.triggerNextMotivationalQuote()
            }
        }
    }

    LaunchedEffect(periodIndex) {
        isAntiBurnCenteredByTap = false
    }

    LaunchedEffect(areControlsVisible, interactionCounter) {
        if (areControlsVisible) {
            delay(10000) // 10 seconds auto-hide
            areControlsVisible = false
        }
    }

    LaunchedEffect(isFocusPhase, isTimerActive, isStopwatchActive) {
        areControlsVisible = true
        interactionCounter++
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var isDrag = false
                    var dragDirection: String? = null
                    val touchSlop = viewConfiguration.touchSlop
                    var totalDragX = 0f
                    var totalDragY = 0f
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            break
                        }
                        
                        totalDragX = change.position.x - down.position.x
                        totalDragY = change.position.y - down.position.y
                        
                        if (!isDrag) {
                            if (kotlin.math.abs(totalDragX) > touchSlop || kotlin.math.abs(totalDragY) > touchSlop) {
                                isDrag = true
                                dragDirection = if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                                    "horizontal"
                                } else {
                                    "vertical"
                                }
                            }
                        }
                        
                        if (isDrag) {
                            change.consume()
                        }
                    }
                    
                    if (isDrag) {
                        if (dragDirection == "horizontal") {
                            if (kotlin.math.abs(totalDragX) > 80f) {
                                val currentMode = viewModel.timerDisplayMode.value
                                val nextMode = if (currentMode == "digital") "flip" else "digital"
                                viewModel.setTimerDisplayMode(nextMode)
                            }
                        } else if (dragDirection == "vertical") {
                            if (kotlin.math.abs(totalDragY) > 80f) {
                                viewModel.setTimerImmersive(false)
                            }
                        }
                    } else {
                        areControlsVisible = !areControlsVisible
                        isAntiBurnCenteredByTap = true
                        interactionCounter++
                    }
                }
            }
            .padding(24.dp)
    ) {
        // Upper block: Show the focusing people emoji bubble and potential quote below it
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.85f)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FriendsFocusPill(
                viewModel = viewModel,
                onClick = onShowFriendsDetails
            )

            if (motivationalQuoteEnabled && currentQuote.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Crossfade(
                    targetState = currentQuote,
                    animationSpec = androidx.compose.animation.core.tween(1500),
                    label = "quote_crossfade"
                ) { targetQuote ->
                    Text(
                        text = "\"$targetQuote\"",
                        color = Color(0xFFFFEB3B).copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // Close button fixed at top right corner only
        if (areControlsVisible) {
            IconButton(
                onClick = { viewModel.setTimerImmersive(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("exit_immersive_btn")
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Immersive", tint = Color.White)
            }
        }

        // Exactly Centered Timer Display
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Task Name (visible only when controls are visible)
            if (areControlsVisible) {
                val displayName = selectedTask?.title ?: "GENERAL FOCUS SPHERE"
                Text(
                    text = displayName.uppercase(),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            val currentSeconds = if (!isFocusPhase) {
                timerSecondsRemaining
            } else if (isTabFocusTimerSelected) {
                timerSecondsRemaining
            } else {
                stopwatchSeconds
            }

            val isBlinking = !isFocusPhase || (isTabFocusTimerSelected && !isFocusPhase)

            if (timerDisplayMode == "flip") {
                RenderFlipDigits(
                    viewModel = viewModel,
                    seconds = currentSeconds,
                    isImmersive = true,
                    isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                    isBlinking = isBlinking,
                    isVerticalPhone = isVerticalPhone
                )
                if (!isFocusPhase) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("now u r in a break", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            } else {
                if (!isFocusPhase) {
                    RenderDigitalDigits(
                        viewModel = viewModel,
                        seconds = timerSecondsRemaining,
                        isImmersive = true,
                        isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                        isBlinking = true,
                        isVerticalPhone = isVerticalPhone
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("now u r in a break", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                } else if (isTabFocusTimerSelected) {
                    RenderDigitalDigits(
                        viewModel = viewModel,
                        seconds = timerSecondsRemaining,
                        isImmersive = true,
                        isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                        isBlinking = !isFocusPhase,
                        isVerticalPhone = isVerticalPhone
                    )
                } else {
                    RenderDigitalDigits(
                        viewModel = viewModel,
                        seconds = stopwatchSeconds,
                        isImmersive = true,
                        isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                        isBlinking = false,
                        isVerticalPhone = isVerticalPhone
                    )
                }
            }

            // Swipe instruction indicator
            if (areControlsVisible) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(0.6f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (timerDisplayMode == "digital") Color.White else Color.Gray,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = "SWIPE TO SWITCH MODE",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (timerDisplayMode == "flip") Color.White else Color.Gray,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons row (visible only when controls are visible)
            if (areControlsVisible) {
                val selectedTag by viewModel.attachedTag.collectAsStateWithLifecycle()
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (isFocusPhase) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TagInterlinkSearchVBar(
                                selectedTag = selectedTag,
                                onClear = { viewModel.attachTagToTimer("") },
                                onClick = { viewModel.setShowTagSelectionDialog(true) },
                                modifier = Modifier.weight(1f)
                            )
                            TaskInterlinkSearchVBar(
                                selectedTask = selectedTask,
                                onClear = { viewModel.attachTaskToTimer(null) },
                                onClick = { viewModel.setShowTaskSelectionDialog(true) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFocusPhase) {
                            // Break Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isTabFocusTimerSelected) {
                                            viewModel.pauseTimer()
                                            viewModel.takeBreakFromPomodoro()
                                        } else {
                                            viewModel.pauseStopwatch()
                                            viewModel.takeBreakFromStopwatch()
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x5581C784),
                                        backgroundColor = Color(0x334CAF50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Break", color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Pause / Resume Button
                            val isActive = if (isTabFocusTimerSelected) isTimerActive else isStopwatchActive
                            val isThisSyncing = isTimerSyncInProgress && (
                                lastButtonClicked == "pause_timer" || lastButtonClicked == "start_timer" ||
                                lastButtonClicked == "start_stopwatch" || lastButtonClicked == "pause_stopwatch"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (!isTimerSyncInProgress) {
                                            if (isTabFocusTimerSelected) {
                                                if (isActive) viewModel.pauseTimer() else viewModel.startTimer()
                                            } else {
                                                if (isActive) viewModel.pauseStopwatch() else viewModel.startStopwatch()
                                            }
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = if (isActive) Color(0x33FFFFFF) else WaterBlue.copy(alpha = 0.5f),
                                        backgroundColor = if (isActive) Color(0x40222222) else WaterBlue.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isThisSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = if (isActive) Color.White else WaterBlue)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isActive) "Pausing..." else "Resuming...", color = if (isActive) Color.White else WaterBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    } else {
                                        Text(if (isActive) "Pause" else "Resume", color = if (isActive) Color.White else WaterBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                            
                            // End Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isTabFocusTimerSelected) {
                                            viewModel.pauseTimer()
                                            viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                                        } else {
                                            viewModel.pauseStopwatch()
                                            viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x15F9325D),
                                        backgroundColor = Color(0x40C62828)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("End", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else {
                            // BREAK PHASE
                            val isBreakActive = isTimerActive
                            val isThisSyncing = isTimerSyncInProgress && (lastButtonClicked == "pause_timer" || lastButtonClicked == "start_timer")
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (!isTimerSyncInProgress) {
                                            if (isBreakActive) {
                                                viewModel.pauseTimer()
                                            } else {
                                                viewModel.startTimer()
                                            }
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = if (isBreakActive) Color(0x33FFFFFF) else WaterBlue.copy(alpha = 0.5f),
                                        backgroundColor = if (isBreakActive) Color(0x40222222) else WaterBlue.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isThisSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = if (isBreakActive) Color.White else WaterBlue)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isBreakActive) "Pausing..." else "Resuming...", color = if (isBreakActive) Color.White else WaterBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Text(if (isBreakActive) "Pause" else "Resume", color = if (isBreakActive) Color.White else WaterBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            // Start Focus
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        viewModel.pauseTimer()
                                        if (isTabFocusTimerSelected) {
                                            if (wasStartedFromStopwatch) {
                                                viewModel.switchToFocusPhaseFromStopwatch()
                                                viewModel.startStopwatch()
                                            } else {
                                                viewModel.resetWorkPhaseTimer(focusTimerDurationMins)
                                                viewModel.startTimer()
                                            }
                                        } else {
                                            viewModel.switchToFocusPhase()
                                            viewModel.startStopwatch()
                                        }
                                        viewModel.setTimerImmersive(true)
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = WaterBlue.copy(alpha = 0.6f),
                                        backgroundColor = WaterBlue.copy(alpha = 0.35f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isTabFocusTimerSelected && !wasStartedFromStopwatch) "Start Pomo" else "Start Stopw", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // End Break
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isTabFocusTimerSelected) {
                                            viewModel.skipOrEndBreak()
                                        } else {
                                            viewModel.pauseTimer()
                                            viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x33C62828),
                                        backgroundColor = Color(0x22C62828)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("End", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenderFlipDigits(
    viewModel: AppViewModel,
    seconds: Int,
    isImmersive: Boolean,
    isAntiBurnCenteredByTap: Boolean,
    isBlinking: Boolean = false,
    isVerticalPhone: Boolean = false
) {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsStateWithLifecycle()
    val minutesElapsedTotal = (System.currentTimeMillis() / 60000).toInt()
    val periodIndex = (minutesElapsedTotal / 5) % 4

    val antiBurnOffset = if (antiBurnScreenEnabled && isImmersive && !isAntiBurnCenteredByTap) {
        when (periodIndex) {
            0 -> Modifier.offset(x = (-40).dp, y = (-30).dp)
            1 -> Modifier.offset(x = (40).dp, y = (30).dp)
            2 -> Modifier.offset(x = (30).dp, y = (-40).dp)
            else -> Modifier.offset(x = (-30).dp, y = (40).dp)
        }
    } else {
        Modifier
    }

    val blinkAlpha = if (isBlinking) {
        val infiniteTransition = rememberInfiniteTransition(label = "flip_blink")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flip_blinkAlpha"
        )
        alpha
    } else {
        1.0f
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    val cardSize = if (isPortrait) {
        if (h > 0) 130.dp else 180.dp
    } else {
        if (h > 0) 95.dp else 125.dp
    }

    val fontSize = if (isPortrait) {
        if (h > 0) 75.sp else 115.sp
    } else {
        if (h > 0) 50.sp else 75.sp
    }

    Box(
        modifier = antiBurnOffset
            .alpha(blinkAlpha)
            .testTag("timer_flip_display")
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isPortrait) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (h > 0) {
                    FlipCard(valueString = String.format(java.util.Locale.US, "%02d", h), cardSize = cardSize, fontSize = fontSize)
                }
                FlipCard(valueString = String.format(java.util.Locale.US, "%02d", m), cardSize = cardSize, fontSize = fontSize)
                FlipCard(valueString = String.format(java.util.Locale.US, "%02d", s), cardSize = cardSize, fontSize = fontSize)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (h > 0) {
                    FlipCard(valueString = String.format(java.util.Locale.US, "%02d", h), cardSize = cardSize, fontSize = fontSize)
                }
                FlipCard(valueString = String.format(java.util.Locale.US, "%02d", m), cardSize = cardSize, fontSize = fontSize)
                FlipCard(valueString = String.format(java.util.Locale.US, "%02d", s), cardSize = cardSize, fontSize = fontSize)
            }
        }
    }
}

@Composable
fun FlipCard(
    valueString: String,
    cardSize: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedContent(
        targetState = valueString,
        transitionSpec = {
            (androidx.compose.animation.slideInVertically { height -> -height } + androidx.compose.animation.fadeIn(animationSpec = tween(150)))
                .togetherWith(androidx.compose.animation.slideOutVertically { height -> height } + androidx.compose.animation.fadeOut(animationSpec = tween(150)))
        },
        label = "flip_card_anim"
    ) { animatedValue ->
        Box(
            modifier = modifier
                .size(cardSize)
                .background(Color.Black, shape = RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, Color(0xFF2E2E31)), shape = RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF202022),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(Color(0xFF0C0C0D))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF151517),
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        )
                )
            }
            
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-4).dp)
                        .size(width = 8.dp, height = 12.dp)
                        .background(Color.Black, shape = RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 4.dp)
                        .size(width = 8.dp, height = 12.dp)
                        .background(Color.Black, shape = RoundedCornerShape(4.dp))
                )
            }

            Text(
                text = animatedValue,
                color = Color(0xFFECECEC),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-2).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}



// ==========================================================
// MERGED FROM: TimerView_LiveControl.kt
// ==========================================================



@Composable
fun TimerLiveControlContent(
    viewModel: AppViewModel,
    isTablet: Boolean,
    isImmersive: Boolean,
    isAntiBurnCenteredByTap: Boolean,
    globalTodaySeconds: Int,
    focusTimerDurationMins: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val WaterBlue = Color(0xFF38BDF8)

    val isTimerActive by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val timerSecondsRemaining by viewModel.timerSecondsLeft.collectAsStateWithLifecycle()
    val isFocusPhase by viewModel.isFocusPhase.collectAsStateWithLifecycle()
    val cumulativeSessionFocusSeconds by viewModel.cumulativeSessionFocusSeconds.collectAsStateWithLifecycle()
    val isInBreakMode = !isFocusPhase

    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()
    val isStopwatchActive by viewModel.isStopwatchActive.collectAsStateWithLifecycle()
    val isTabFocusTimerSelected by viewModel.isTabFocusTimerSelected.collectAsStateWithLifecycle()
    val wasStartedFromStopwatch by viewModel.wasStartedFromStopwatch.collectAsStateWithLifecycle()

    val isStopwatchOnOrActive = isStopwatchActive || stopwatchSeconds > 0
    val isTimerOnOrActive = isTimerActive || (timerSecondsRemaining < focusTimerDurationMins * 60)

    val waterReminderEnabled by viewModel.waterReminderEnabled.collectAsStateWithLifecycle()
    var soundPlayingNotification by remember { mutableStateOf<String?>(null) }
    val selectedTask by viewModel.attachedTask.collectAsStateWithLifecycle()
    val sessionStartTimestamp by viewModel.sessionStartTimestamp.collectAsStateWithLifecycle()

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = if (isTablet) Color(0xFF101010) else Color.Black),
        border = if (isTablet) BorderStroke(1.dp, Color(0xFF222222)) else null,
        shape = if (isTablet) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTablet) 18.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Mode Toggles Focus vs Stopwatch
            if (!isTimerOnOrActive && !isStopwatchOnOrActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF151515))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(32.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isTabFocusTimerSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.setTabFocusTimerSelected(true) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Pomodoro Mode", color = if (isTabFocusTimerSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (!isTabFocusTimerSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.setTabFocusTimerSelected(false) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Stopwatch", color = if (!isTabFocusTimerSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Numeric display unified for both Timer and Stopwatch
            Box(
                modifier = Modifier.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isTabFocusTimerSelected || isInBreakMode) {
                        RenderDigitalDigits(
                            viewModel = viewModel,
                            seconds = timerSecondsRemaining,
                            isImmersive = isImmersive,
                            isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                            isBlinking = isInBreakMode
                        )
                        Text(
                            text = if (isTimerActive) {
                                if (isInBreakMode) "now u r in a break" else "KEEP FOCUSING"
                            } else {
                                if (isInBreakMode) "now u r in a break" else "STOPPED"
                            },
                            color = if (isTimerActive) {
                                if (isInBreakMode) Color(0xFF81C784) else WaterBlue
                            } else {
                                if (isInBreakMode) Color(0xFF81C784) else Color.Gray
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    } else {
                        RenderDigitalDigits(
                            viewModel = viewModel,
                            seconds = stopwatchSeconds,
                            isImmersive = isImmersive,
                            isAntiBurnCenteredByTap = isAntiBurnCenteredByTap,
                            isBlinking = false
                        )
                        Text(
                            text = if (isStopwatchActive) "KEEP FOCUSING" else "STOPPED",
                            color = if (isStopwatchActive) WaterBlue else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sound playing visuals
            if (soundPlayingNotification != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, WaterBlue),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Active Alarm", tint = WaterBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(soundPlayingNotification ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Control Actions Bar delegated to subcomponents
            if (!isFocusPhase) {
                LiveControlBreakBar(
                    viewModel = viewModel,
                    context = context,
                    wasStartedFromStopwatch = wasStartedFromStopwatch,
                    isTimerActive = isTimerActive,
                    stopwatchSeconds = stopwatchSeconds,
                    WaterBlue = WaterBlue
                )
            } else if (isTabFocusTimerSelected) {
                LiveControlTimerBar(
                    viewModel = viewModel,
                    selectedTask = selectedTask,
                    isTimerActive = isTimerActive,
                    sessionStartTimestamp = sessionStartTimestamp,
                    timerSecondsRemaining = timerSecondsRemaining,
                    focusTimerDurationMins = focusTimerDurationMins,
                    cumulativeSessionFocusSeconds = cumulativeSessionFocusSeconds,
                    globalTodaySeconds = globalTodaySeconds,
                    WaterBlue = WaterBlue
                )
            } else {
                LiveControlStopwatchBar(
                    viewModel = viewModel,
                    selectedTask = selectedTask,
                    isStopwatchActive = isStopwatchActive,
                    sessionStartTimestamp = sessionStartTimestamp,
                    stopwatchSeconds = stopwatchSeconds,
                    globalTodaySeconds = globalTodaySeconds,
                    WaterBlue = WaterBlue
                )
            }
        }
    }
}

// ==========================================================
// MERGED FROM: TimerView_LivePanels.kt
// ==========================================================



@Composable
fun RenderDigitalDigits(
    viewModel: AppViewModel,
    seconds: Int,
    isImmersive: Boolean,
    isAntiBurnCenteredByTap: Boolean,
    isBlinking: Boolean = false,
    isVerticalPhone: Boolean = false
) {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    val textString = if (seconds >= 3600) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    val antiBurnScreenEnabled by viewModel.antiBurnScreenEnabled.collectAsState()
    val minutesElapsedTotal = (System.currentTimeMillis() / 60000).toInt()
    val periodIndex = (minutesElapsedTotal / 5) % 4

    val antiBurnOffset = if (antiBurnScreenEnabled && isImmersive && !isAntiBurnCenteredByTap) {
        when (periodIndex) {
            0 -> Modifier.offset(x = (-40).dp, y = (-30).dp)
            1 -> Modifier.offset(x = (40).dp, y = (30).dp)
            2 -> Modifier.offset(x = (30).dp, y = (-40).dp)
            else -> Modifier.offset(x = (-30).dp, y = (40).dp)
        }
    } else {
        Modifier
    }

    val blinkAlpha = if (isBlinking) {
        val infiniteTransition = rememberInfiniteTransition(label = "blink")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blinkAlpha"
        )
        alpha
    } else {
        1.0f
    }

    if (isVerticalPhone) {
        Column(
            modifier = antiBurnOffset.alpha(blinkAlpha).testTag("timer_digital_display"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-16).dp)
        ) {
            if (h > 0) {
                Text(
                    text = String.format("%02d", h),
                    color = Color.White,
                    fontSize = 130.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-4).sp
                )
            }
            Text(
                text = String.format("%02d", m),
                color = Color.White,
                fontSize = 130.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-4).sp
            )
            Text(
                text = String.format("%02d", s),
                color = Color.White,
                fontSize = 130.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-4).sp
            )
        }
    } else {
        Text(
            text = textString,
            color = Color.White,
            fontSize = if (isImmersive) 110.sp else 86.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-4).sp,
            modifier = antiBurnOffset
                .alpha(blinkAlpha)
                .testTag("timer_digital_display")
        )
    }
}

@Composable
fun TaskInterlinkSearchVBar(
    selectedTask: Task?,
    onClear: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val WaterBlue = Color(0xFF38BDF8)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111113)),
        border = BorderStroke(1.dp, if (selectedTask != null) WaterBlue.copy(alpha = 0.6f) else Color(0xFF232326))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (selectedTask != null) WaterBlue.copy(alpha = 0.12f) else Color(0xFF1E1E22),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selectedTask != null) Icons.Default.Link else Icons.Default.Search,
                        contentDescription = "Link Task",
                        tint = if (selectedTask != null) WaterBlue else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (selectedTask != null) "LINKED TASK" else "TASK LINK",
                        color = Color.Gray,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = selectedTask?.title ?: "Select...",
                        color = if (selectedTask != null) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTask != null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            if (selectedTask != null) {
                IconButton(
                    onClick = {
                        onClear()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selected task",
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TagInterlinkSearchVBar(
    selectedTag: String,
    onClear: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val WaterBlue = Color(0xFF38BDF8)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111113)),
        border = BorderStroke(1.dp, if (selectedTag.isNotEmpty()) WaterBlue.copy(alpha = 0.6f) else Color(0xFF232326))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (selectedTag.isNotEmpty()) WaterBlue.copy(alpha = 0.12f) else Color(0xFF1E1E22),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = "Select Tag",
                        tint = if (selectedTag.isNotEmpty()) WaterBlue else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "FOCUS CATEGORY",
                        color = Color.Gray,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (selectedTag.isNotEmpty()) selectedTag else "Select...",
                        color = if (selectedTag.isNotEmpty()) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTag.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            if (selectedTag.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClear()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selected tag",
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveControlTimerBar(
    viewModel: AppViewModel,
    selectedTask: Task?,
    isTimerActive: Boolean,
    sessionStartTimestamp: Long?,
    timerSecondsRemaining: Int,
    focusTimerDurationMins: Int,
    cumulativeSessionFocusSeconds: Int,
    globalTodaySeconds: Int,
    WaterBlue: Color
) {
    val selectedTag by viewModel.attachedTag.collectAsState()
    val isTimerSyncInProgress by viewModel.isTimerSyncInProgress.collectAsStateWithLifecycle()
    val lastButtonClicked by viewModel.lastButtonClicked.collectAsStateWithLifecycle()

    if (isTimerActive) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TagInterlinkSearchVBar(
                selectedTag = selectedTag,
                onClear = { viewModel.attachTagToTimer("") },
                onClick = { viewModel.setShowTagSelectionDialog(true) },
                modifier = Modifier.weight(1f)
            )
            TaskInterlinkSearchVBar(
                selectedTask = selectedTask,
                onClear = { viewModel.attachTaskToTimer(null) },
                onClick = { viewModel.setShowTaskSelectionDialog(true) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.pauseTimer() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = {
                    viewModel.pauseTimer()
                    viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    } else {
        if (sessionStartTimestamp == null && timerSecondsRemaining == focusTimerDurationMins * 60) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Focused Today", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = formatLiveSeconds(globalTodaySeconds), color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Pomodoro dynamic presets row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val presets = listOf(
                    Triple(25, 5, "25/5 Sprint"),
                    Triple(50, 10, "50/10 Sprint"),
                    Triple(15, 3, "15/3 Lite")
                )
                presets.forEach { (fMins, bMins, label) ->
                    val isSelected = focusTimerDurationMins == fMins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) WaterBlue.copy(alpha = 0.18f) else Color(0xFF161616))
                            .border(1.dp, if (isSelected) WaterBlue else Color(0xFF2E2E31), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.updateTimerDuration(fMins)
                                viewModel.updateBreakDuration(bMins)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) WaterBlue else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TagInterlinkSearchVBar(
                    selectedTag = selectedTag,
                    onClear = { viewModel.attachTagToTimer("") },
                    onClick = { viewModel.setShowTagSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
                TaskInterlinkSearchVBar(
                    selectedTask = selectedTask,
                    onClear = { viewModel.attachTaskToTimer(null) },
                    onClick = { viewModel.setShowTaskSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.setTabFocusTimerSelected(true)
                        viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                        viewModel.startTimer()
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_timer_btn"),
                    enabled = !isTimerSyncInProgress
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTimerSyncInProgress && lastButtonClicked == "start_timer") {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Starting...", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Focus", modifier = Modifier.size(20.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Focus", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TagInterlinkSearchVBar(
                    selectedTag = selectedTag,
                    onClear = { viewModel.attachTagToTimer("") },
                    onClick = { viewModel.setShowTagSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
                TaskInterlinkSearchVBar(
                    selectedTask = selectedTask,
                    onClear = { viewModel.attachTaskToTimer(null) },
                    onClick = { viewModel.setShowTaskSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.startTimer()
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !isTimerSyncInProgress
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTimerSyncInProgress && lastButtonClicked == "start_timer") {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resuming...", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.pauseTimer()
                        viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("End", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ==========================================================
// MERGED FROM: TimerView_LiveStopwatch.kt
// ==========================================================



@Composable
fun LiveControlBreakBar(
    viewModel: AppViewModel,
    context: Context,
    wasStartedFromStopwatch: Boolean,
    isTimerActive: Boolean,
    stopwatchSeconds: Int,
    WaterBlue: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (wasStartedFromStopwatch) {
            Button(
                onClick = {
                    viewModel.pauseTimer()
                    viewModel.switchToFocusPhaseFromStopwatch()
                    viewModel.startStopwatch()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume Stopwatch", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val isTimerSyncInProgress by viewModel.isTimerSyncInProgress.collectAsStateWithLifecycle()
            val lastButtonClicked by viewModel.lastButtonClicked.collectAsStateWithLifecycle()
            val onClickAction = if (isTimerActive) { { viewModel.pauseTimer() } } else { { viewModel.startTimer() } }
            val btnBg = if (isTimerActive) Color.White.copy(alpha = 0.15f) else WaterBlue
            val btnFg = if (isTimerActive) Color.White else Color.Black
            val iconRes = if (isTimerActive) Icons.Default.Pause else Icons.Default.PlayArrow
            val isThisSyncing = isTimerSyncInProgress && (lastButtonClicked == "pause_timer" || lastButtonClicked == "start_timer")

            Button(
                onClick = onClickAction,
                colors = ButtonDefaults.buttonColors(containerColor = btnBg, contentColor = btnFg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp),
                enabled = !isTimerSyncInProgress
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isThisSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = btnFg)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTimerActive) "Pausing..." else "Resuming...", color = btnFg, fontSize = 13.sp)
                    } else {
                        Icon(iconRes, contentDescription = "Toggle", tint = btnFg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTimerActive) "Pause" else "Resume", color = btnFg, fontSize = 13.sp, fontWeight = if (isTimerActive) FontWeight.Normal else FontWeight.Bold)
                    }
                }
            }
        }

        Button(
            onClick = {
                if (wasStartedFromStopwatch) {
                    viewModel.pauseTimer()
                    viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                } else {
                    viewModel.skipOrEndBreak()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "End Break", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LiveControlStopwatchBar(
    viewModel: AppViewModel,
    selectedTask: Task?,
    isStopwatchActive: Boolean,
    sessionStartTimestamp: Long?,
    stopwatchSeconds: Int,
    globalTodaySeconds: Int,
    WaterBlue: Color
) {
    val selectedTag by viewModel.attachedTag.collectAsState()

    if (isStopwatchActive) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TagInterlinkSearchVBar(
                selectedTag = selectedTag,
                onClear = { viewModel.attachTagToTimer("") },
                onClick = { viewModel.setShowTagSelectionDialog(true) },
                modifier = Modifier.weight(1f)
            )
            TaskInterlinkSearchVBar(
                selectedTask = selectedTask,
                onClear = { viewModel.attachTaskToTimer(null) },
                onClick = { viewModel.setShowTaskSelectionDialog(true) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isTimerSyncInProgress by viewModel.isTimerSyncInProgress.collectAsStateWithLifecycle()
            val lastButtonClicked by viewModel.lastButtonClicked.collectAsStateWithLifecycle()
            val isThisSyncing = isTimerSyncInProgress && lastButtonClicked == "pause_stopwatch"
            Button(
                onClick = { viewModel.pauseStopwatch() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp),
                enabled = !isTimerSyncInProgress
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isThisSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pausing...", color = Color.White, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.pauseStopwatch()
                    viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    } else {
        if (sessionStartTimestamp == null && stopwatchSeconds == 0) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Focused Today", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = formatLiveSeconds(globalTodaySeconds), color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TagInterlinkSearchVBar(
                    selectedTag = selectedTag,
                    onClear = { viewModel.attachTagToTimer("") },
                    onClick = { viewModel.setShowTagSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
                TaskInterlinkSearchVBar(
                    selectedTask = selectedTask,
                    onClear = { viewModel.attachTaskToTimer(null) },
                    onClick = { viewModel.setShowTaskSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isTimerSyncInProgress by viewModel.isTimerSyncInProgress.collectAsStateWithLifecycle()
                val lastButtonClicked by viewModel.lastButtonClicked.collectAsStateWithLifecycle()
                val isThisSyncing = isTimerSyncInProgress && lastButtonClicked == "start_stopwatch"
                Button(
                    onClick = {
                        viewModel.setTabFocusTimerSelected(false)
                        viewModel.setSessionStartTimestamp(com.example.util.StableTime.currentTimeMillis())
                        viewModel.startStopwatch()
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_stopwatch_btn"),
                    enabled = !isTimerSyncInProgress
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isThisSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Starting...", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Stopwatch", modifier = Modifier.size(20.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Stopwatch", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TagInterlinkSearchVBar(
                    selectedTag = selectedTag,
                    onClear = { viewModel.attachTagToTimer("") },
                    onClick = { viewModel.setShowTagSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
                TaskInterlinkSearchVBar(
                    selectedTask = selectedTask,
                    onClear = { viewModel.attachTaskToTimer(null) },
                    onClick = { viewModel.setShowTaskSelectionDialog(true) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isTimerSyncInProgress by viewModel.isTimerSyncInProgress.collectAsStateWithLifecycle()
                val lastButtonClicked by viewModel.lastButtonClicked.collectAsStateWithLifecycle()
                val isThisSyncing = isTimerSyncInProgress && lastButtonClicked == "start_stopwatch"
                Button(
                    onClick = {
                        viewModel.startStopwatch()
                        viewModel.setTimerImmersive(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !isTimerSyncInProgress
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isThisSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resuming...", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Button(
                    onClick = {
                        viewModel.pauseStopwatch()
                        viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Stop, contentDescription = "End", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("End", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}