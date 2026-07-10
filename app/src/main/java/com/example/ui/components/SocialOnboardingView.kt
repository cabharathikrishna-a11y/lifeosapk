package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.AppBlockHelper
import kotlinx.coroutines.delay

@Composable
fun SocialOnboardingView(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val isFirstTime = remember { !appPrefs.contains("social_blocker_settings_shown_version") }

    // Preferences for Social Apps
    // 1. Instagram
    var igBlocked by remember { mutableStateOf(if (isFirstTime) true else AppBlockHelper.isAppInBlockList(context, "com.instagram.android")) }
    var igSelective by remember { mutableStateOf(AppBlockHelper.isIgSelectiveBlockingEnabled(context)) }
    var igReels by remember { mutableStateOf(AppBlockHelper.isIgReelsBlocked(context)) }
    var igStories by remember { mutableStateOf(AppBlockHelper.isIgStoriesBlocked(context)) }
    var igExplore by remember { mutableStateOf(AppBlockHelper.isIgExploreBlocked(context)) }
    var igLimit by remember { mutableStateOf(AppBlockHelper.getDailyLimitMinutes(context, "com.instagram.android")) }

    // 2. Snapchat
    var snapBlocked by remember { mutableStateOf(if (isFirstTime) true else AppBlockHelper.isAppInBlockList(context, "com.snapchat.android")) }
    var snapSelective by remember { mutableStateOf(AppBlockHelper.isSnapSelectiveBlockingEnabled(context)) }
    var snapSpotlight by remember { mutableStateOf(AppBlockHelper.isSnapSpotlightBlocked(context)) }
    var snapMap by remember { mutableStateOf(AppBlockHelper.isSnapMapBlocked(context)) }
    var snapDiscover by remember { mutableStateOf(AppBlockHelper.isSnapDiscoverBlocked(context)) }
    var snapLimit by remember { mutableStateOf(AppBlockHelper.getDailyLimitMinutes(context, "com.snapchat.android")) }

    // 3. Facebook
    var fbBlocked by remember { mutableStateOf(AppBlockHelper.isAppInBlockList(context, "com.facebook.katana")) }
    var fbSelective by remember { mutableStateOf(AppBlockHelper.isFbSelectiveBlockingEnabled(context)) }
    var fbReels by remember { mutableStateOf(AppBlockHelper.isFbReelsBlocked(context)) }
    var fbWatch by remember { mutableStateOf(AppBlockHelper.isFbWatchBlocked(context)) }
    var fbStories by remember { mutableStateOf(AppBlockHelper.isFbStoriesBlocked(context)) }
    var fbLimit by remember { mutableStateOf(AppBlockHelper.getDailyLimitMinutes(context, "com.facebook.katana")) }

    // Permissions States
    var hasUsageStats by remember { mutableStateOf(false) }
    var hasAccessibility by remember { mutableStateOf(false) }

    // Periodically check permissions state
    LaunchedEffect(Unit) {
        while (true) {
            hasUsageStats = AppBlockHelper.hasUsageStatsPermission(context)
            hasAccessibility = isAccessibilityServiceEnabled(context)
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06070D))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WaterBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Focus Guard Setup",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Configure your social media limits and app blocker defaults to secure your focus and study hours.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Notification / Alert Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = WaterBlue.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Icon",
                        tint = WaterBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "By default, Instagram and Snapchat have been added to your app blocker list. Bedtime / Wake-up alarm has been configured OFF by default.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // 1. Instagram Config Card
            SocialBlockerConfigCard(
                appName = "Instagram",
                packageName = "com.instagram.android",
                isBlocked = igBlocked,
                onBlockedChange = { igBlocked = it },
                useSelective = igSelective,
                onSelectiveChange = { igSelective = it },
                screenLimit = igLimit,
                onLimitChange = { igLimit = it },
                subToggles = listOf(
                    SubToggleItem("Block Reels", igReels) { igReels = it },
                    SubToggleItem("Block Stories", igStories) { igStories = it },
                    SubToggleItem("Block Explore Feed", igExplore) { igExplore = it }
                )
            )

            // 2. Snapchat Config Card
            SocialBlockerConfigCard(
                appName = "Snapchat",
                packageName = "com.snapchat.android",
                isBlocked = snapBlocked,
                onBlockedChange = { snapBlocked = it },
                useSelective = snapSelective,
                onSelectiveChange = { snapSelective = it },
                screenLimit = snapLimit,
                onLimitChange = { snapLimit = it },
                subToggles = listOf(
                    SubToggleItem("Block Spotlight", snapSpotlight) { snapSpotlight = it },
                    SubToggleItem("Block Snap Map", snapMap) { snapMap = it },
                    SubToggleItem("Block Discover", snapDiscover) { snapDiscover = it }
                )
            )

            // 3. Facebook Config Card
            SocialBlockerConfigCard(
                appName = "Facebook",
                packageName = "com.facebook.katana",
                isBlocked = fbBlocked,
                onBlockedChange = { fbBlocked = it },
                useSelective = fbSelective,
                onSelectiveChange = { fbSelective = it },
                screenLimit = fbLimit,
                onLimitChange = { fbLimit = it },
                subToggles = listOf(
                    SubToggleItem("Block FB Reels", fbReels) { fbReels = it },
                    SubToggleItem("Block FB Watch", fbWatch) { fbWatch = it },
                    SubToggleItem("Block FB Stories", fbStories) { fbStories = it }
                )
            )

            // Permissions Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Charcoal),
                border = BorderStroke(1.dp, Color(0xFF222225)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "System Permissions Requirements",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                    // Usage Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usage Access", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tracks screen-time usage limit dynamically.", color = Color.Gray, fontSize = 11.sp)
                        }
                        if (hasUsageStats) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Granted",
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f), contentColor = WaterBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Accessibility Service
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accessibility Service", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Enforces fine-grained content blocking safely.", color = Color.Gray, fontSize = 11.sp)
                        }
                        if (hasAccessibility) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Enabled",
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue.copy(alpha = 0.15f), contentColor = WaterBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Save and Continue Button
            Button(
                onClick = {
                    // Apply Settings
                    // 1. Instagram Settings
                    if (igBlocked) {
                        AppBlockHelper.addBlockedApp(context, "com.instagram.android")
                    } else {
                        AppBlockHelper.removeBlockedApp(context, "com.instagram.android")
                    }
                    AppBlockHelper.setIgSelectiveBlockingEnabled(context, igSelective)
                    AppBlockHelper.setIgReelsBlocked(context, igReels)
                    AppBlockHelper.setIgStoriesBlocked(context, igStories)
                    AppBlockHelper.setIgExploreBlocked(context, igExplore)
                    AppBlockHelper.setDailyLimitMinutes(context, "com.instagram.android", igLimit)

                    // 2. Snapchat Settings
                    if (snapBlocked) {
                        AppBlockHelper.addBlockedApp(context, "com.snapchat.android")
                    } else {
                        AppBlockHelper.removeBlockedApp(context, "com.snapchat.android")
                    }
                    AppBlockHelper.setSnapSelectiveBlockingEnabled(context, snapSelective)
                    AppBlockHelper.setSnapSpotlightBlocked(context, snapSpotlight)
                    AppBlockHelper.setSnapMapBlocked(context, snapMap)
                    AppBlockHelper.setSnapDiscoverBlocked(context, snapDiscover)
                    AppBlockHelper.setDailyLimitMinutes(context, "com.snapchat.android", snapLimit)

                    // 3. Facebook Settings
                    if (fbBlocked) {
                        AppBlockHelper.addBlockedApp(context, "com.facebook.katana")
                    } else {
                        AppBlockHelper.removeBlockedApp(context, "com.facebook.katana")
                    }
                    AppBlockHelper.setFbSelectiveBlockingEnabled(context, fbSelective)
                    AppBlockHelper.setFbReelsBlocked(context, fbReels)
                    AppBlockHelper.setFbWatchBlocked(context, fbWatch)
                    AppBlockHelper.setFbStoriesBlocked(context, fbStories)
                    AppBlockHelper.setDailyLimitMinutes(context, "com.facebook.katana", fbLimit)

                    // Default Morning Wakeup Alarm is off by default:
                    val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    if (!appPrefs.contains("wakeup_alarm_enabled")) {
                        appPrefs.edit().putBoolean("wakeup_alarm_enabled", false).apply()
                    }

                    // Save that we showed this startup page
                    val currentVersion = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).let {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                it.longVersionCode.toInt()
                            } else {
                                @Suppress("DEPRECATION")
                                it.versionCode
                            }
                        }
                    } catch (e: Exception) {
                        1
                    }
                    appPrefs.edit().putInt("social_blocker_settings_shown_version", currentVersion).apply()

                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Apply Settings & Launch Life OS",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

data class SubToggleItem(
    val title: String,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

@Composable
fun SocialBlockerConfigCard(
    appName: String,
    packageName: String,
    isBlocked: Boolean,
    onBlockedChange: (Boolean) -> Unit,
    useSelective: Boolean,
    onSelectiveChange: (Boolean) -> Unit,
    screenLimit: Int,
    onLimitChange: (Int) -> Unit,
    subToggles: List<SubToggleItem>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Charcoal),
        border = BorderStroke(1.dp, Color(0xFF222225)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App Name and Master Block Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = appName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = packageName,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = isBlocked,
                    onCheckedChange = onBlockedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WaterBlue,
                        checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                    )
                )
            }

            if (isBlocked) {
                HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                // Selective Content Blocker Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selective Content Blocking",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Blocks specific sections instead of the whole app.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = useSelective,
                        onCheckedChange = onSelectiveChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WaterBlue,
                            checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                        )
                    )
                }

                // If selective is enabled, show the sub-toggles
                if (useSelective) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subToggles.forEach { toggleItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = toggleItem.title,
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                                Switch(
                                    checked = toggleItem.isChecked,
                                    onCheckedChange = toggleItem.onCheckedChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = WaterBlue,
                                        checkedTrackColor = WaterBlue.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.scaleModifier(0.85f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF222225), thickness = 0.5.dp)

                // Screen Time daily limit changing option
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Screen Time Limit",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Automatically block the app after limit expires.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = if (screenLimit > 0) "$screenLimit min" else "No Limit",
                            color = if (screenLimit > 0) WaterBlue else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Plus / Minus adjustments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (screenLimit >= 5) onLimitChange(screenLimit - 5) else onLimitChange(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("-5m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { if (screenLimit >= 15) onLimitChange(screenLimit - 15) else onLimitChange(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("-15m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onLimitChange(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(36.dp)
                        ) {
                            Text("Disable", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onLimitChange(screenLimit + 15) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("+15m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onLimitChange(screenLimit + 30) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E24)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text("+30m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Simple modifier extension to safely scale custom Switches
fun Modifier.scaleModifier(scale: Float): Modifier = this.then(
    Modifier.scale(scale)
)

// Helper to check if accessibility service is enabled
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return AppBlockHelper.isAccessibilityServiceEnabled(context)
}
