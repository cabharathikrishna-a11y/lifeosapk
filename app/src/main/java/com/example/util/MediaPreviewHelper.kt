package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.BitmapFactory
import android.graphics.Matrix as AndroidMatrix
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun rememberVideoThumbnail(videoPath: String): ImageBitmap? {
    var bitmap by remember(videoPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(videoPath) {
        if (videoPath.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
                    retriever.setDataSource(videoPath, HashMap<String, String>())
                } else {
                    val file = File(videoPath)
                    if (file.exists()) {
                        retriever.setDataSource(videoPath)
                    } else {
                        return@withContext
                    }
                }
                val bmp = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                if (bmp != null) {
                    bitmap = bmp.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return bitmap
}

@Composable
fun rememberPdfFirstPagePreview(pdfPath: String): ImageBitmap? {
    var bitmap by remember(pdfPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pdfPath) {
        if (pdfPath.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val file = File(pdfPath)
                if (file.exists() && file.name.endsWith(".pdf", ignoreCase = true)) {
                    val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(fileDescriptor)
                    if (pdfRenderer.pageCount > 0) {
                        val page = pdfRenderer.openPage(0)
                        val scale = 300f / page.width
                        val finalWidth = (page.width * scale).toInt().coerceAtLeast(1)
                        val finalHeight = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = bmp.asImageBitmap()
                        page.close()
                    }
                    pdfRenderer.close()
                    fileDescriptor.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return bitmap
}

fun openFileInGoogleDocsOrSheets(context: android.content.Context, file: File, docType: String) {
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "File does not exist", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val mimeType = when (docType) {
            "word" -> {
                if (file.name.lowercase().endsWith(".doc")) "application/msword" 
                else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            }
            "excel" -> {
                if (file.name.lowercase().endsWith(".xls")) "application/vnd.ms-excel" 
                else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
            else -> "application/*"
        }

        val targetPackage = when (docType) {
            "word" -> "com.google.android.apps.docs.editors.docs"
            "excel" -> "com.google.android.apps.docs.editors.sheets"
            else -> null
        }

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (targetPackage != null) {
                setPackage(targetPackage)
            }
        }

        try {
            context.startActivity(intent)
            val appLabel = if (docType == "word") "Google Docs" else "Google Sheets"
            android.widget.Toast.makeText(context, "Opening in $appLabel...", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            try {
                fallbackIntent.setPackage("com.google.android.apps.docs")
                context.startActivity(fallbackIntent)
                android.widget.Toast.makeText(context, "Opening with Google Drive...", android.widget.Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                fallbackIntent.setPackage(null)
                val chooser = android.content.Intent.createChooser(fallbackIntent, "Open with...")
                context.startActivity(chooser)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Error opening file: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewBox(
    pathOrName: String,
    type: String, // "image", "video", "audio", "others"
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cleanPath = remember(pathOrName) {
        when {
            pathOrName.startsWith("photo:") -> pathOrName.removePrefix("photo:")
            pathOrName.startsWith("video:") -> pathOrName.removePrefix("video:")
            pathOrName.startsWith("audio:") -> pathOrName.removePrefix("audio:")
            pathOrName.startsWith("file:") -> {
                val parts = pathOrName.removePrefix("file:").split("|path:")
                parts.getOrNull(1) ?: parts.getOrNull(0) ?: ""
            }
            else -> {
                val internalFile = File(StorageHelper.getAppFilesDir(context), pathOrName)
                if (internalFile.exists()) {
                    internalFile.absolutePath
                } else {
                    pathOrName
                }
            }
        }
    }

    val isWebUrl = remember(cleanPath) { cleanPath.startsWith("http://") || cleanPath.startsWith("https://") }
    val file = remember(cleanPath, isWebUrl) { if (isWebUrl) File("") else File(cleanPath) }

    var showPdfViewer by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }
    var showPhotoEditorSelection by remember { mutableStateOf(false) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var showPhotoViewerOnly by remember { mutableStateOf(false) }
    
    // File change observer to trigger cache busting
    var fileLastModified by remember(cleanPath) { mutableStateOf(if (isWebUrl) 0L else file.lastModified()) }

    Box(
        modifier = modifier
            .clickable {
                when (type) {
                    "image" -> {
                        if (!isWebUrl && file.exists()) {
                            showPhotoEditorSelection = true
                        } else {
                            showPhotoViewerOnly = true
                        }
                    }
                    "video" -> {
                        if (cleanPath.isNotEmpty()) {
                            showVideoPlayer = true
                        }
                    }
                    "pdf" -> {
                        if (cleanPath.isNotEmpty()) {
                            showPdfViewer = true
                        }
                    }
                    "word" -> {
                        if (cleanPath.isNotEmpty()) {
                            openFileInGoogleDocsOrSheets(context, file, "word")
                        }
                    }
                    "excel" -> {
                        if (cleanPath.isNotEmpty()) {
                            openFileInGoogleDocsOrSheets(context, file, "excel")
                        }
                    }
                    else -> {
                        if (cleanPath.endsWith(".pdf", ignoreCase = true)) {
                            showPdfViewer = true
                        } else {
                            // Try opening with standard android system intent
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/*")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "No app to open this file", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
    ) {
        when (type) {
            "image" -> {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.15f))
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    AsyncImage(
                        model = if (isWebUrl) cleanPath else java.io.File(cleanPath),
                        contentDescription = "Image Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            "video" -> {
                val thumbnailBitmap = rememberVideoThumbnail(cleanPath)
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.15f))
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (thumbnailBitmap != null) {
                            Image(
                                bitmap = thumbnailBitmap,
                                contentDescription = "Video Thumbnail Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF141414)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            "pdf" -> {
                val pdfBitmap = rememberPdfFirstPagePreview(cleanPath)
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.15f))
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (pdfBitmap != null) {
                            Image(
                                bitmap = pdfBitmap,
                                contentDescription = "PDF Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = "PDF",
                                    tint = Color(0xFFE57373),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "PDF Document",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            "word" -> {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF4285F4).copy(alpha = 0.4f))
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B263B))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Word Document",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val shortName = cleanPath.substringAfterLast("/")
                            Text(
                                text = shortName,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Word / Google Docs",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            "excel" -> {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF0F9D58).copy(alpha = 0.4f))
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12281F))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Excel Spreadsheet",
                                tint = Color(0xFF0F9D58),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val shortName = cleanPath.substringAfterLast("/")
                            Text(
                                text = shortName,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Excel / Google Sheets",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            else -> {
                val isPdf = remember(cleanPath) { cleanPath.endsWith(".pdf", ignoreCase = true) }
                if (isPdf) {
                    val pdfBitmap = rememberPdfFirstPagePreview(cleanPath)
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder(true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.15f))
                        ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (pdfBitmap != null) {
                                Image(
                                    bitmap = pdfBitmap,
                                    contentDescription = "PDF Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "PDF",
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "PDF Document",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        border = CardDefaults.outlinedCardBorder(true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.15f))
                        ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = "File",
                                    tint = Color(0xFF2E6FF3),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val shortName = cleanPath.substringAfterLast("/")
                                Text(
                                    text = shortName,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PDF Viewer Dialog
    if (showPdfViewer && cleanPath.isNotEmpty()) {
        PdfViewerDialog(filePath = cleanPath, onDismiss = { showPdfViewer = false })
    }

    // Video Player Dialog
    if (showVideoPlayer && cleanPath.isNotEmpty()) {
        VideoPlayerDialog(filePath = cleanPath, onDismiss = { showVideoPlayer = false })
    }

    // Full Photo Viewer (Simple View Only)
    if (showPhotoViewerOnly && cleanPath.isNotEmpty()) {
        Dialog(onDismissRequest = { showPhotoViewerOnly = false }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = if (isWebUrl) cleanPath else file,
                        contentDescription = "Full Screen Preview",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { showPhotoViewerOnly = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }

    // Photo Action Selection Dialog
    if (showPhotoEditorSelection) {
        AlertDialog(
            onDismissRequest = { showPhotoEditorSelection = false },
            containerColor = Color(0xFF13141C),
            title = { Text("Photo Actions", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Would you like to view the photo or open the interactive photo editor?", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showPhotoEditorSelection = false
                        showPhotoEditor = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3))
                ) {
                    Text("Edit / Crop Photo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoEditorSelection = false
                        showPhotoViewerOnly = true
                    }
                ) {
                    Text("View Photo", color = Color.White)
                }
            }
        )
    }

    // Full Photo Editor & Cropper
    if (showPhotoEditor && cleanPath.isNotEmpty()) {
        PhotoEditorDialog(
            filePath = cleanPath,
            onDismiss = { showPhotoEditor = false },
            onSaved = {
                fileLastModified = System.currentTimeMillis()
                showPhotoEditor = false
            }
        )
    }
}

// ==========================================
// 1. HIGH-PERFORMANCE PDF VIEWER (1000+ PAGES)
// ==========================================

@Composable
fun PdfPageItem(renderer: PdfRenderer, pageIndex: Int, scale: Float) {
    val pageBitmap by produceState<Bitmap?>(initialValue = null, pageIndex, scale) {
        value = withContext(Dispatchers.IO) {
            try {
                synchronized(renderer) {
                    val page = renderer.openPage(pageIndex)
                    // Scale resolution dynamically for performance
                    val width = (page.width * scale * 1.5f).toInt().coerceAtLeast(1)
                    val height = (page.height * scale * 1.5f).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(pageBitmap!!.width.toFloat() / pageBitmap!!.height.toFloat())
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2E6FF3), strokeWidth = 3.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerDialog(filePath: String, onDismiss: () -> Unit) {
    val file = remember(filePath) { File(filePath) }
    var pageCount by remember { mutableStateOf(0) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pdfFileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var zoomScale by remember { mutableStateOf(1.0f) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var jumpPageText by remember { mutableStateOf("") }

    DisposableEffect(filePath) {
        try {
            if (file.exists()) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfFileDescriptor = pfd
                val pdfRenderer = PdfRenderer(pfd)
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                renderer?.close()
                pdfFileDescriptor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F1016)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161722))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = file.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                        Text(
                            text = "$pageCount Pages",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
                        }
                        Text("${(zoomScale * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4.0f) }) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
                        }
                    }
                }

                // Jump page control bar
                if (pageCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1D2A))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val firstVisibleItem = remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
                        Text(
                            text = "Page ${firstVisibleItem.value + 1} of $pageCount",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = jumpPageText,
                                onValueChange = { jumpPageText = it },
                                placeholder = { Text("Page", color = Color.Gray) },
                                modifier = Modifier.width(70.dp).height(42.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2E6FF3),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = Color.Black,
                                    unfocusedContainerColor = Color.Black
                                ),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val targetPage = jumpPageText.toIntOrNull()?.minus(1)
                                    if (targetPage != null && targetPage in 0 until pageCount) {
                                        coroutineScope.launch {
                                            lazyListState.scrollToItem(targetPage)
                                        }
                                    }
                                },
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3)),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Go", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Virtualized infinite list
                if (renderer != null && pageCount > 0) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(pageCount) { pageIndex ->
                            PdfPageItem(renderer = renderer!!, pageIndex = pageIndex, scale = zoomScale)
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Unable to open PDF", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. STABLE HIGH-PERFORMANCE VIDEO PLAYER
// ==========================================

@Composable
fun VideoPlayerDialog(filePath: String, onDismiss: () -> Unit) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var videoViewInstance by remember { mutableStateOf<android.widget.VideoView?>(null) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                videoViewInstance?.let { vv ->
                    currentPosition = vv.currentPosition.toLong()
                    duration = vv.duration.toLong()
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(filePath)
                            setOnPreparedListener { mp ->
                                duration = mp.duration.toLong()
                                start()
                                isPlaying = true
                            }
                            setOnCompletionListener {
                                isPlaying = false
                            }
                        }
                    },
                    update = { videoView ->
                        videoViewInstance = videoView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.Center)
                )

                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = File(filePath).name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                }

                // Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { percent ->
                                videoViewInstance?.let { vv ->
                                    val target = (percent * duration).toInt()
                                    vv.seekTo(target)
                                    currentPosition = target.toLong()
                                }
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2E6FF3),
                                activeTrackColor = Color(0xFF2E6FF3),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            videoViewInstance?.let { vv ->
                                val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                                vv.seekTo(target)
                                currentPosition = target.toLong()
                            }
                        }) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = {
                                videoViewInstance?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E6FF3))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(onClick = {
                            videoViewInstance?.let { vv ->
                                val target = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                vv.seekTo(target)
                                currentPosition = target.toLong()
                            }
                        }) {
                            Icon(Icons.Default.FastForward, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// ==========================================
// 3. INBUILT PHOTO EDITOR AND CROPPER
// ==========================================

enum class EditTab { ADJUST, FILTERS, DOODLE, CROP }

data class DoodleStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

@Composable
fun PhotoEditorDialog(
    filePath: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var workingBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var editTab by remember { mutableStateOf(EditTab.ADJUST) }

    // Color matrix sliders
    var brightness by remember { mutableStateOf(1.0f) } // 0.5f to 1.5f
    var contrast by remember { mutableStateOf(1.0f) }   // 0.5f to 1.5f
    var saturation by remember { mutableStateOf(1.0f) } // 0.0f to 2.0f

    // Filters
    var activeFilter by remember { mutableStateOf("None") }

    // Doodle state
    var doodleColor by remember { mutableStateOf(Color.Red) }
    var doodleBrushWidth by remember { mutableStateOf(8f) }
    val doodleStrokes = remember { mutableStateListOf<DoodleStroke>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }

    // Gestures inside Crop Mode
    var imageScale by remember { mutableStateOf(1f) }
    var imageTranslation by remember { mutableStateOf(Offset.Zero) }

    // Initial load
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val orig = BitmapFactory.decodeFile(filePath)
                if (orig != null) {
                    workingBitmap = orig.copy(Bitmap.Config.ARGB_8888, true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val finalColorMatrix = remember(brightness, contrast, saturation, activeFilter) {
        val cm = android.graphics.ColorMatrix()
        // Brightness scale
        cm.setScale(brightness, brightness, brightness, 1f)

        // Contrast adjustment
        val scale = contrast
        val translate = 128f * (1f - scale)
        val contrastMatrix = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(android.graphics.ColorMatrix(contrastMatrix))

        // Saturation adjustment
        val satMat = android.graphics.ColorMatrix()
        satMat.setSaturation(saturation)
        cm.postConcat(satMat)

        // Pre-defined Filter overlays
        when (activeFilter) {
            "Grayscale" -> {
                val gray = android.graphics.ColorMatrix()
                gray.setSaturation(0f)
                cm.postConcat(gray)
            }
            "Sepia" -> {
                val sepiaMatrix = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(android.graphics.ColorMatrix(sepiaMatrix))
            }
            "Invert" -> {
                val invertMatrix = floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(android.graphics.ColorMatrix(invertMatrix))
            }
            "Vintage" -> {
                val vintageMatrix = floatArrayOf(
                    0.9f, 0.3f, 0.15f, 0f, 0f,
                    0.15f, 0.9f, 0.2f, 0f, 0f,
                    0.15f, 0.15f, 0.9f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(android.graphics.ColorMatrix(vintageMatrix))
            }
            "Warm" -> {
                val warmMatrix = floatArrayOf(
                    1.2f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(android.graphics.ColorMatrix(warmMatrix))
            }
            "Cool" -> {
                val coolMatrix = floatArrayOf(
                    0.8f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(android.graphics.ColorMatrix(coolMatrix))
            }
        }

        androidx.compose.ui.graphics.ColorMatrix(cm.array)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090A0F)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13141C))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text("Photo Studio Editor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(
                        onClick = {
                            workingBitmap?.let { bmp ->
                                // Trigger save routine in background
                                val out = File(filePath)
                                try {
                                    val finalSaved = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(finalSaved)
                                    
                                    // 1. Draw image with adjustments & filters applied
                                    val cmPaint = android.graphics.Paint().apply {
                                        val arr = finalColorMatrix.values
                                        val androidCM = android.graphics.ColorMatrix(arr)
                                        colorFilter = android.graphics.ColorMatrixColorFilter(androidCM)
                                    }
                                    canvas.drawBitmap(bmp, 0f, 0f, cmPaint)

                                    // 2. Draw doodles mapped to bitmap coordinates
                                    val doodlePaint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        style = android.graphics.Paint.Style.STROKE
                                        strokeCap = android.graphics.Paint.Cap.ROUND
                                        strokeJoin = android.graphics.Paint.Join.ROUND
                                    }
                                    doodleStrokes.forEach { stroke ->
                                        doodlePaint.color = stroke.color.toArgb()
                                        doodlePaint.strokeWidth = stroke.width * (bmp.width / 400f) // Scale stroke size relative to canvas preview W=400dp
                                        val path = android.graphics.Path()
                                        if (stroke.points.isNotEmpty()) {
                                            path.moveTo(stroke.points.first().x * (bmp.width / 400f), stroke.points.first().y * (bmp.height / 300f))
                                            for (i in 1 until stroke.points.size) {
                                                path.lineTo(stroke.points[i].x * (bmp.width / 400f), stroke.points[i].y * (bmp.height / 300f))
                                            }
                                            canvas.drawPath(path, doodlePaint)
                                        }
                                    }

                                    // 3. Write directly to original path
                                    val fos = FileOutputStream(out)
                                    finalSaved.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                                    fos.flush()
                                    fos.close()
                                    onSaved()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    android.widget.Toast.makeText(context, "Failed to save edits", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Edits", tint = Color(0xFF2E6FF3))
                    }
                }

                // Image Preview Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (workingBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 400.dp, height = 300.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        ) {
                            if (editTab == EditTab.CROP) {
                                // Crop Mode Gestures Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF141414))
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                imageScale = (imageScale * zoom).coerceIn(0.5f, 4f)
                                                imageTranslation += pan
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap = workingBitmap!!.asImageBitmap(),
                                        contentDescription = "Editing Image",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = imageScale,
                                                scaleY = imageScale,
                                                translationX = imageTranslation.x,
                                                translationY = imageTranslation.y
                                            ),
                                        contentScale = ContentScale.Fit,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(finalColorMatrix)
                                    )

                                    // Bounding Box Frame overlay for crop
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val boxSize = 200.dp.toPx()
                                        val left = (size.width - boxSize) / 2
                                        val top = (size.height - boxSize) / 2
                                        val right = left + boxSize
                                        val bottom = top + boxSize

                                        // Draw outside darkened region
                                        drawRect(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            size = size
                                        )
                                        // Blend in-center transparent hole
                                        drawIntoCanvas { canvas ->
                                            val paint = android.graphics.Paint().apply {
                                                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                                            }
                                            canvas.nativeCanvas.drawRect(left, top, right, bottom, paint)
                                        }
                                        // Draw white frame lines
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(left, top),
                                            size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                }
                            } else {
                                // Regular adjust, filters, or doodle draw mode
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        bitmap = workingBitmap!!.asImageBitmap(),
                                        contentDescription = "Editing Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(finalColorMatrix)
                                    )

                                    // Doodle Overlay
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(editTab) {
                                                if (editTab == EditTab.DOODLE) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            currentPoints.add(offset)
                                                        },
                                                        onDrag = { change, _ ->
                                                            change.consume()
                                                            currentPoints.add(change.position)
                                                        },
                                                        onDragEnd = {
                                                            doodleStrokes.add(DoodleStroke(currentPoints.toList(), doodleColor, doodleBrushWidth))
                                                            currentPoints.clear()
                                                        }
                                                    )
                                                }
                                            }
                                    ) {
                                        // Draw historic strokes
                                        doodleStrokes.forEach { stroke ->
                                            if (stroke.points.size > 1) {
                                                for (i in 0 until stroke.points.size - 1) {
                                                    drawLine(
                                                        color = stroke.color,
                                                        start = stroke.points[i],
                                                        end = stroke.points[i + 1],
                                                        strokeWidth = stroke.width,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                    )
                                                }
                                            }
                                        }
                                        // Draw active line currently drawing
                                        if (currentPoints.size > 1) {
                                            for (i in 0 until currentPoints.size - 1) {
                                                drawLine(
                                                    color = doodleColor,
                                                    start = currentPoints[i],
                                                    end = currentPoints[i + 1],
                                                    strokeWidth = doodleBrushWidth,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator(color = Color(0xFF2E6FF3))
                    }
                }

                // Control Center & Tabs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13141C))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (editTab) {
                        EditTab.ADJUST -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Brightness Slider
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Brightness", color = Color.White, fontSize = 12.sp)
                                        Text(String.format("%.2f", brightness), color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Slider(
                                        value = brightness,
                                        onValueChange = { brightness = it },
                                        valueRange = 0.5f..1.5f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF2E6FF3), activeTrackColor = Color(0xFF2E6FF3))
                                    )
                                }
                                // Contrast Slider
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Contrast", color = Color.White, fontSize = 12.sp)
                                        Text(String.format("%.2f", contrast), color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Slider(
                                        value = contrast,
                                        onValueChange = { contrast = it },
                                        valueRange = 0.5f..1.5f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF2E6FF3), activeTrackColor = Color(0xFF2E6FF3))
                                    )
                                }
                                // Saturation Slider
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Saturation", color = Color.White, fontSize = 12.sp)
                                        Text(String.format("%.2f", saturation), color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Slider(
                                        value = saturation,
                                        onValueChange = { saturation = it },
                                        valueRange = 0.0f..2.0f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF2E6FF3), activeTrackColor = Color(0xFF2E6FF3))
                                    )
                                }
                            }
                        }
                        EditTab.FILTERS -> {
                            val filtersList = listOf("None", "Grayscale", "Sepia", "Invert", "Vintage", "Warm", "Cool")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filtersList.forEach { filterName ->
                                    val isSelected = filterName == activeFilter
                                    Button(
                                        onClick = { activeFilter = filterName },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) Color(0xFF2E6FF3) else Color.White.copy(alpha = 0.1f)
                                        ),
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text(filterName, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        EditTab.DOODLE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Color row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Brush Color", color = Color.White, fontSize = 12.sp)
                                    val colorsList = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Black)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        colorsList.forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (doodleColor == color) 2.dp else 0.dp,
                                                        color = Color.White,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { doodleColor = color }
                                            )
                                        }
                                    }
                                }
                                // Size slider
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Brush Width", color = Color.White, fontSize = 12.sp)
                                        Text("${doodleBrushWidth.toInt()}px", color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Slider(
                                        value = doodleBrushWidth,
                                        onValueChange = { doodleBrushWidth = it },
                                        valueRange = 2f..24f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF2E6FF3), activeTrackColor = Color(0xFF2E6FF3))
                                    )
                                }
                                // Undo and Clear buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { if (doodleStrokes.isNotEmpty()) doodleStrokes.removeAt(doodleStrokes.lastIndex) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Text("Undo", color = Color.White, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { doodleStrokes.clear() },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f))
                                    ) {
                                        Text("Clear", color = Color.Red, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        EditTab.CROP -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pinch & Position image in central box", color = Color.LightGray, fontSize = 12.sp)
                                Button(
                                    onClick = {
                                        workingBitmap?.let { bmp ->
                                            try {
                                                // Calculate sub-rect mapping from coordinate screen space
                                                // Center mapping crop calculations
                                                val scaledWidth = bmp.width * imageScale
                                                val scaledHeight = bmp.height * imageScale
                                                
                                                // Coordinates relative to centering
                                                val cropW = (bmp.width / imageScale).toInt().coerceIn(10, bmp.width)
                                                val cropH = (bmp.height / imageScale).toInt().coerceIn(10, bmp.height)

                                                val leftX = (((bmp.width - cropW) / 2) - (imageTranslation.x / imageScale)).toInt().coerceIn(0, bmp.width - 1)
                                                val topY = (((bmp.height - cropH) / 2) - (imageTranslation.y / imageScale)).toInt().coerceIn(0, bmp.height - 1)
                                                val finalW = cropW.coerceAtMost(bmp.width - leftX)
                                                val finalH = cropH.coerceAtMost(bmp.height - topY)

                                                if (finalW > 0 && finalH > 0) {
                                                    val cropped = Bitmap.createBitmap(bmp, leftX, topY, finalW, finalH)
                                                    workingBitmap = cropped
                                                    imageScale = 1f
                                                    imageTranslation = Offset.Zero
                                                    android.widget.Toast.makeText(context, "Image Cropped", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                android.widget.Toast.makeText(context, "Unable to crop this ratio", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E6FF3)),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Apply Crop", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f))

                    // Base Rotator/Flipper and Tab Navigation Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Transform helpers
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    workingBitmap?.let { bmp ->
                                        val mat = android.graphics.Matrix().apply { postRotate(90f) }
                                        workingBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    workingBitmap?.let { bmp ->
                                        val mat = android.graphics.Matrix().apply { postScale(-1f, 1f) }
                                        workingBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal", tint = Color.White)
                            }
                        }

                        // Tab switches
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                EditTab.ADJUST to Icons.Default.Tune,
                                EditTab.FILTERS to Icons.Default.FilterList,
                                EditTab.DOODLE to Icons.Default.Brush,
                                EditTab.CROP to Icons.Default.Crop
                            ).forEach { (tab, icon) ->
                                val isSelected = editTab == tab
                                IconButton(
                                    onClick = { editTab = tab },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF2E6FF3) else Color.Transparent)
                                ) {
                                    Icon(icon, contentDescription = tab.name, tint = if (isSelected) Color.White else Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
