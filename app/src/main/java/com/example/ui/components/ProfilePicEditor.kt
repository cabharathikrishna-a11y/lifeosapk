package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WaterBlue
import java.io.ByteArrayOutputStream

@Composable
fun ProfilePicEditor(
    initialValue: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    var activeValue by remember { mutableStateOf(initialValue) }
    
    // Choose between "Emoji" and "Custom Photo"
    val isCustomPhoto = activeValue.startsWith("base64:")
    var selectedTab by remember { mutableStateOf(if (isCustomPhoto) 1 else 0) }

    // Emoji state
    var emojiInput by remember { mutableStateOf(if (!isCustomPhoto) activeValue else "👨‍💻") }

    // Gallery picker state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Cropper adjustments
    var zoomScale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isCroppingMode by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val bitmap = uriToBitmap(context, uri)
            if (bitmap != null) {
                loadedBitmap = bitmap
                zoomScale = 1f
                offsetX = 0f
                offsetY = 0f
                isCroppingMode = true
            }
        }
    }

    // Update parent whenever we have a confirmed new value
    val confirmValue = { newValue: String ->
        activeValue = newValue
        onValueChange(newValue)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current avatar preview
        Box(
            modifier = Modifier.size(90.dp),
            contentAlignment = Alignment.Center
        ) {
            UserAvatar(
                emojiOrBase64 = activeValue,
                size = 90.dp,
                fontSize = 48.sp,
                fallback = "🎯"
            )
        }

        // Selection tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = WaterBlue,
            divider = {},
            indicator = { tabPositions ->
                if (tabPositions.isNotEmpty()) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = WaterBlue
                    )
                }
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    confirmValue(emojiInput)
                },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Use Emoji", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    if (!activeValue.startsWith("base64:")) {
                        // Prompt to pick if they don't have a photo yet
                        imagePickerLauncher.launch("image/*")
                    }
                },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Custom Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (selectedTab == 0) {
            // Emoji Input Panel
            OutlinedTextField(
                value = emojiInput,
                onValueChange = {
                    emojiInput = it
                    confirmValue(it)
                },
                label = { Text("Profile Emoji (Single Character)", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = WaterBlue,
                    unfocusedBorderColor = Color(0xFF333333)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Custom Photo Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Charcoal, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (isCustomPhoto) {
                    Text(
                        text = "✓ Custom photo cropped & ready",
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // WhatsApp-style Full Screen Dialog Cropper
        if (isCroppingMode && loadedBitmap != null) {
            Dialog(
                onDismissRequest = { isCroppingMode = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    val screenWidth = maxWidth
                    val screenHeight = maxHeight
                    
                    val circleDiameterDp = minOf(screenWidth, screenHeight) - 48.dp
                    val circleDiameterPx = with(density) { circleDiameterDp.toPx() }
                    
                    val bmWidth = loadedBitmap!!.width.toFloat()
                    val bmHeight = loadedBitmap!!.height.toFloat()
                    
                    // We want the image to fit so that it fully covers the circle initially
                    val baseScale = circleDiameterPx / minOf(bmWidth, bmHeight)
                    val layoutWidthDp = with(density) { (bmWidth * baseScale).toDp() }
                    val layoutHeightDp = with(density) { (bmHeight * baseScale).toDp() }

                    // Interactive touch-to-drag and pinch-to-zoom area (entire screen)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(loadedBitmap) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                    
                                    val totalScale = baseScale * zoomScale
                                    val wz = bmWidth * totalScale
                                    val hz = bmHeight * totalScale
                                    
                                    val maxOffsetX = (wz - circleDiameterPx) / 2f
                                    val maxOffsetY = (hz - circleDiameterPx) / 2f
                                    
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // The loaded image itself, responsive to zoom & pan offset
                        Image(
                            bitmap = loadedBitmap!!.asImageBitmap(),
                            contentDescription = "Upload Source",
                            modifier = Modifier
                                .size(width = layoutWidthDp, height = layoutHeightDp)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Crop
                        )

                        // WhatsApp-style translucent background mask with clean circular hole
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            
                            val path = androidx.compose.ui.graphics.Path().apply {
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasWidth, canvasHeight))
                            }
                            val circlePath = androidx.compose.ui.graphics.Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(
                                    canvasWidth / 2f - circleDiameterPx / 2f,
                                    canvasHeight / 2f - circleDiameterPx / 2f,
                                    canvasWidth / 2f + circleDiameterPx / 2f,
                                    canvasHeight / 2f + circleDiameterPx / 2f
                                ))
                            }
                            
                            val maskPath = androidx.compose.ui.graphics.Path.combine(
                                androidx.compose.ui.graphics.PathOperation.Difference,
                                path,
                                circlePath
                            )
                            
                            drawPath(
                                path = maskPath,
                                color = Color.Black.copy(alpha = 0.75f)
                            )
                            
                            // Fine white boundary around circular crop zone
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f),
                                radius = circleDiameterPx / 2f,
                                center = androidx.compose.ui.geometry.Offset(canvasWidth / 2f, canvasHeight / 2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        }

                        // Text instructions at top of screen
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Move and Zoom",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Drag to reposition • Pinch with two fingers to resize",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        // WhatsApp-style Cancel and Apply actions at bottom of screen
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isCroppingMode = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }

                            Button(
                                onClick = {
                                    val cropped = cropBitmapToSquareAndScale(
                                        loadedBitmap!!,
                                        zoomScale,
                                        offsetX,
                                        offsetY,
                                        circleDiameterPx,
                                        baseScale
                                    )
                                    val base64 = bitmapToBase64(cropped)
                                    confirmValue(base64)
                                    isCroppingMode = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper methods for Bitmap crop & conversion
private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

private fun cropBitmapToSquareAndScale(
    bitmap: Bitmap,
    zoomScale: Float,
    offsetX: Float,
    offsetY: Float,
    circleDiameterPx: Float,
    baseScale: Float
): Bitmap {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    
    val totalScale = baseScale * zoomScale
    val cropSizeOnBitmap = circleDiameterPx / totalScale
    
    // Calculate top-left coordinates on the original bitmap
    val startX = bitmapWidth / 2f - (offsetX + circleDiameterPx / 2f) / totalScale
    val startY = bitmapHeight / 2f - (offsetY + circleDiameterPx / 2f) / totalScale
    
    val startXCoerced = startX.toInt().coerceIn(0, (bitmapWidth - cropSizeOnBitmap).toInt().coerceAtLeast(0))
    val startYCoerced = startY.toInt().coerceIn(0, (bitmapHeight - cropSizeOnBitmap).toInt().coerceAtLeast(0))
    val sizeCoerced = cropSizeOnBitmap.toInt().coerceIn(50, minOf(bitmap.width, bitmap.height))
    
    val cropped = Bitmap.createBitmap(bitmap, startXCoerced, startYCoerced, sizeCoerced, sizeCoerced)
    val scaled = Bitmap.createScaledBitmap(cropped, 192, 192, true)
    
    if (cropped != bitmap) {
        cropped.recycle()
    }
    return scaled
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    val bytes = outputStream.toByteArray()
    return "base64:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
}
