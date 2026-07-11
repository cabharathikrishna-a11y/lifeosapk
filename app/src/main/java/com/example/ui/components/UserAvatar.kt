package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ProfileImageCacheManager

@Composable
fun UserAvatar(
    emojiOrBase64: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    size: Dp = 24.dp,
    fallback: String = "🎯",
    username: String? = null,
    photoUpdatedAt: Long = 0L
) {
    val context = LocalContext.current
    var isCacheReady by remember(username, photoUpdatedAt) { mutableStateOf(false) }

    val cacheFile = remember(username) {
        if (!username.isNullOrEmpty()) java.io.File(context.cacheDir, "profile_${username}.jpg") else null
    }

    LaunchedEffect(username, emojiOrBase64, photoUpdatedAt) {
        if (!username.isNullOrEmpty() && !emojiOrBase64.isNullOrEmpty()) {
            ProfileImageCacheManager.getProfileImage(context, username, photoUpdatedAt, emojiOrBase64)
            isCacheReady = true
        }
    }

    if (cacheFile != null && cacheFile.exists()) {
        val imageRequest = remember(username, photoUpdatedAt) {
            coil.request.ImageRequest.Builder(context)
                .data(cacheFile)
                .memoryCacheKey("profile_${username}_$photoUpdatedAt")
                .diskCacheKey("profile_${username}_$photoUpdatedAt")
                .build()
        }
        coil.compose.AsyncImage(
            model = imageRequest,
            contentDescription = "User Avatar",
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentScale = ContentScale.Crop
        )
        return
    }

    if (emojiOrBase64.isNullOrEmpty()) {
        Box(
            modifier = modifier
                .size(size)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallback,
                fontSize = fontSize,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
        return
    }

    if (emojiOrBase64.startsWith("base64:")) {
        val base64Data = emojiOrBase64.substringAfter("base64:")
        val bitmap = remember(base64Data) {
            try {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "User Avatar",
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = modifier
                    .size(size)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallback,
                    fontSize = fontSize,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emojiOrBase64,
                fontSize = fontSize,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}
