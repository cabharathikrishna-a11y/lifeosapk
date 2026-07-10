package com.example.ui.components

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WaterBlue
import com.example.util.AppLockHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun AppLockOverlay(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val lockType = remember { AppLockHelper.getLockType(context) }
    val correctCode = remember { AppLockHelper.getLockCode(context) ?: "" }
    val biometricsEnabled = remember { AppLockHelper.isBiometricsEnabled(context) }

    var currentInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBiometricsActiveBySession by remember { mutableStateOf(biometricsEnabled) }
    var biometricFailCount by remember { mutableStateOf(0) }
    var isBiometricDialogShown by remember { mutableStateOf(biometricsEnabled) }

    // Security Question Backup State
    var showForgotPasswordState by remember { mutableStateOf(false) }
    val savedQuestions = remember { AppLockHelper.getSecurityQuestions(context) }
    // Randomly select one security question out of the 3 configured during init/setup
    val randomQuestionIndex = remember { Random.nextInt(3) }
    val selectedQuestionPair = remember {
        if (savedQuestions.size >= 3) savedQuestions[randomQuestionIndex] else Pair("What is your pet's name?", "")
    }
    var securityAnswerInput by remember { mutableStateOf("") }
    var securityAnswerError by remember { mutableStateOf<String?>(null) }

    // Auto-prompt system biometrics or dialog if active
    LaunchedEffect(isBiometricsActiveBySession) {
        if (isBiometricsActiveBySession) {
            isBiometricDialogShown = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        if (showForgotPasswordState) {
            // Security Question Verification Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Forgot Lock",
                    tint = WaterBlue,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = "SECURITY RECOVERY",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Answer the backup question preset during your secure lock setup.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Backup Question:",
                            color = WaterBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = selectedQuestionPair.first,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = securityAnswerInput,
                            onValueChange = {
                                securityAnswerInput = it
                                securityAnswerError = null
                            },
                            placeholder = { Text("Enter answer", color = Color.DarkGray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222)
                            )
                        )

                        if (securityAnswerError != null) {
                            Text(
                                text = securityAnswerError!!,
                                color = Color.Red,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                val inputNormalized = securityAnswerInput.trim().lowercase()
                                val correctNormalized = selectedQuestionPair.second.trim().lowercase()
                                if (inputNormalized == correctNormalized && correctNormalized.isNotEmpty()) {
                                    Toast.makeText(context, "Recovery Successful: App Lock bypassed and deactivated.", Toast.LENGTH_LONG).show()
                                    // Deactivate lock so they can set a new one
                                    AppLockHelper.setAppLockEnabled(context, false)
                                    AppLockHelper.setLockCode(context, null)
                                    onUnlocked()
                                } else {
                                    securityAnswerError = "Incorrect answer. Please try again."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Verify & Unlock App", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                showForgotPasswordState = false
                                securityAnswerInput = ""
                                securityAnswerError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Unlock Screen", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // Main App Unlocking Interface
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(top = 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Encrypted",
                        tint = WaterBlue,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "LIFE OS SECURE GATE",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lockType == "pin") "Enter PIN to access your vault" else "Enter Password to unlock",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Input Box / PIN dots Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.5f),
                    contentAlignment = Alignment.Center
                ) {
                    if (lockType == "pin") {
                        // Custom Row of 4-6 Dot Indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dotCount = maxOf(4, correctCode.length)
                            for (i in 0 until dotCount) {
                                val isFilled = i < currentInput.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            color = if (isFilled) WaterBlue else Color(0xFF222222),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isFilled) WaterBlue else Color.DarkGray,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    } else {
                        // Password Text Field Block
                        OutlinedTextField(
                            value = currentInput,
                            onValueChange = {
                                currentInput = it
                                errorMessage = null
                                // Auto check password if they type and press enter (handled by button)
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Star else Icons.Default.Lock,
                                        contentDescription = "Toggle Visibility",
                                        tint = Color.Gray
                                    )
                                }
                            },
                            placeholder = { Text("Password", color = Color.DarkGray, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = WaterBlue,
                                unfocusedBorderColor = Color(0xFF222222)
                            )
                        )
                    }
                }

                // Keyboard / Input Pad Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(3f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (lockType == "pin") {
                        // Numeric Keypad Layout
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("Forgot", "0", "Back")
                        )

                        keys.forEach { rowKeys ->
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowKeys.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .background(
                                                color = if (key == "Forgot" || key == "Back") Color.Transparent else Color(0xFF0C0C0C),
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (key == "Forgot" || key == "Back") Color.Transparent else Color(0xFF222222),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                when (key) {
                                                    "Forgot" -> {
                                                        showForgotPasswordState = true
                                                    }
                                                    "Back" -> {
                                                        if (currentInput.isNotEmpty()) {
                                                            currentInput = currentInput.dropLast(1)
                                                        }
                                                    }
                                                    else -> {
                                                        if (currentInput.length < correctCode.length) {
                                                            errorMessage = null
                                                            val newInput = currentInput + key
                                                            currentInput = newInput
                                                            
                                                            // Auto check PIN if complete
                                                            if (newInput.length == correctCode.length) {
                                                                if (newInput == correctCode) {
                                                                    onUnlocked()
                                                                } else {
                                                                    errorMessage = "Invalid PIN"
                                                                    currentInput = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (key == "Back") {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Backspace",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else if (key == "Forgot") {
                                            Text(
                                                text = "Forgot?",
                                                color = WaterBlue,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Password Submit Buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Button(
                                onClick = {
                                    if (currentInput == correctCode) {
                                        onUnlocked()
                                    } else {
                                        errorMessage = "Invalid Password"
                                        currentInput = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Unlock App", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showForgotPasswordState = true }) {
                                    Text("Forgot Password?", color = WaterBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isBiometricsActiveBySession && biometricsEnabled) {
                        // Display biometric scan fallback trigger
                        IconButton(
                            onClick = { isBiometricDialogShown = true },
                            modifier = Modifier
                                .background(Color(0xFF111111), CircleShape)
                                .border(1.dp, WaterBlue.copy(alpha = 0.5f), CircleShape)
                                .size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Trigger Biometric Scan",
                                tint = WaterBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // Biometric dialog / simulation layout overlay
        if (isBiometricDialogShown && isBiometricsActiveBySession) {
            BiometricVerificationOverlay(
                onSuccess = {
                    isBiometricDialogShown = false
                    onUnlocked()
                },
                onFail = {
                    biometricFailCount++
                    if (biometricFailCount >= 3) {
                        isBiometricsActiveBySession = false // Shut down fingerprint till next cycle
                        isBiometricDialogShown = false
                        Toast.makeText(context, "Fingerprint verification disabled after 3 failed attempts. Enter PIN/Password.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Scan Failed! Attempts: $biometricFailCount/3", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {
                    isBiometricDialogShown = false
                }
            )
        }
    }
}

@Composable
fun BiometricVerificationOverlay(
    onSuccess: () -> Unit,
    onFail: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isHoldingDown by remember { mutableStateOf(false) }
    var touchProgress by remember { mutableStateOf(0f) }
    var scanStateText by remember { mutableStateOf("Hold your finger on the sensor below") }

    // Pulsing circle animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Touch holding loop simulation
    LaunchedEffect(isHoldingDown) {
        if (isHoldingDown) {
            scanStateText = "Scanning biological characteristics..."
            var progress = 0.0f
            while (progress < 1f) {
                delay(40)
                progress += 0.04f
                touchProgress = progress
            }
            touchProgress = 1.0f
            scanStateText = "Verifying biometric integrity..."
            delay(300)
            
            // Randomly succeed or fail to allow thorough simulation checking
            // We favor success (85%) but allow testing failures by letting go early or random 15% fail
            if (Random.nextFloat() < 0.85f) {
                scanStateText = "Access Authorized!"
                delay(300)
                onSuccess()
            } else {
                scanStateText = "Verification Failed"
                delay(300)
                onFail()
                touchProgress = 0f
                isHoldingDown = false
            }
        } else {
            touchProgress = 0f
            scanStateText = "Hold your finger on the sensor below"
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel PIN Bypass", color = Color.Gray, fontSize = 11.sp)
            }
        },
        containerColor = Color(0xFF0F0F0F),
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Face, contentDescription = "Scanner", tint = WaterBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("App Biometric Scanner", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = scanStateText,
                    color = if (scanStateText.startsWith("Verification")) Color.Red else Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(if (isHoldingDown) 1.0f else pulseScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(WaterBlue.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Fingerprint Touch Area with visual holding-down ring
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF000000))
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        WaterBlue, 
                                        Color(0xFF03A9F4).copy(alpha = 0.5f), 
                                        WaterBlue.copy(alpha = touchProgress)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        try {
                                            isHoldingDown = true
                                            awaitRelease()
                                        } finally {
                                            if (touchProgress < 1.0f) {
                                                isHoldingDown = false
                                                touchProgress = 0f
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Hold Scan",
                            tint = if (isHoldingDown) WaterBlue else Color.Gray,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Text(
                    text = "Press & Hold sensor area to verify Identity in streaming emulator.",
                    color = Color.DarkGray,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )

                // Simulated direct test buttons for quick navigation in browser
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSuccess() },
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Green)
                    ) {
                        Text("Simulate OK", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = { onFail() },
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Simulate Fail", fontSize = 10.sp)
                    }
                }
            }
        }
    )
}
