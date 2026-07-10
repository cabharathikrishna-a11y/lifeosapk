package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppListIcon(pkg: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var appName by remember { mutableStateOf(pkg) }

    LaunchedEffect(pkg) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                appName = pm.getApplicationLabel(info).toString()
                val icon = pm.getApplicationIcon(pkg)
                iconBitmap = icon.toBitmap(config = android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            } catch (e: Exception) {
                // Ignore exceptions (e.g. package not found)
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = appName,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 8.dp)
            )
        }
        Text(
            text = appName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
