package com.example.ui.components

import com.example.util.rememberVideoThumbnail
import com.example.util.rememberPdfFirstPagePreview
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.JournalEntry
import com.example.ui.AppViewModel
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import com.example.util.MediaCompressionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.BiasAlignment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import com.example.util.MapDownloadManager
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.core.model.LatLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalBookView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries by viewModel.journalEntries.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val onThisDayOnScreenEnabled by viewModel.onThisDayOnScreenEnabled.collectAsState()
    var isOnThisDayReminderDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var isSidebarExpanded by remember { mutableStateOf(false) }
    val defaultJournalView by viewModel.defaultJournalView.collectAsState()
    var currentJournalTab by remember(defaultJournalView) { mutableStateOf(defaultJournalView) }

    var selectedOnThisDayDayMonth by remember { mutableStateOf(SimpleDateFormat("MM-dd", Locale.US).format(Date())) }
    var mapScrollToDate by remember { mutableStateOf<String?>(null) }

    // Custom Full-Screen Editor State
    var showEditorScreen by remember { mutableStateOf(false) }
    var activeEditingEntryId by remember { mutableStateOf<Int?>(null) }
    var editingTitle by remember { mutableStateOf("") }
    var editingTextValue by remember { mutableStateOf(TextFieldValue("")) }
    var editingDate by remember { mutableStateOf("") }
    var editingTime by remember { mutableStateOf("") }
    var editingAttachments by remember { mutableStateOf<List<String>>(emptyList()) }

    var viewingEntry by remember { mutableStateOf<JournalEntry?>(null) }
    val timelineListState = rememberLazyListState()
    val monthlyListState = rememberLazyListState(initialFirstVisibleItemIndex = 60)

    val extSelectedJournalId by viewModel.selectedJournalId.collectAsState()
    LaunchedEffect(extSelectedJournalId) {
        extSelectedJournalId?.let { idVal ->
            val entry = entries.find { it.id == idVal }
            if (entry != null) {
                activeEditingEntryId = entry.id
                editingTitle = entry.title
                editingTextValue = androidx.compose.ui.text.input.TextFieldValue(entry.text)
                editingDate = entry.dateString
                editingTime = try {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
                } catch (e: Exception) {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                }
                editingAttachments = if (entry.attachmentsJson.isNotEmpty()) {
                    entry.attachmentsJson.split(";;")
                } else {
                    emptyList()
                }
                showEditorScreen = true
                viewingEntry = null
                viewModel.clearSelectedJournalId()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = showEditorScreen || viewingEntry != null) {
        if (showEditorScreen) {
            showEditorScreen = false
        } else if (viewingEntry != null) {
            viewingEntry = null
        }
    }

    // Helpers to manage recordings
    var audioRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var currentAudioRecordingFile by remember { mutableStateOf<File?>(null) }

    // Launches to request missing camera/mic/location permissions
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordOk = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraOk = permissions[Manifest.permission.CAMERA] ?: false
        val locationOk = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (recordOk) {
            Toast.makeText(context, "Microphone enabled", Toast.LENGTH_SHORT).show()
        }
    }

    // Capture Photo helper launcher
    var activePhotoFile by remember { mutableStateOf<File?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && activePhotoFile != null) {
            val optimizedFile = MediaCompressionHelper.compressImageFile(context, activePhotoFile!!)
            editingAttachments = editingAttachments + "photo:${optimizedFile.absolutePath}"
        }
    }

    // Capture Video helper launcher
    var activeVideoFile by remember { mutableStateOf<File?>(null) }
    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && activeVideoFile != null) {
            editingAttachments = editingAttachments + "video:${activeVideoFile!!.absolutePath}"
        }
    }

    // Attach File helper launcher
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val copiedFile = copyFileToInternalSandbox(context, uri)
            if (copiedFile != null) {
                editingAttachments = editingAttachments + "file:${copiedFile.name}|path:${copiedFile.absolutePath}"
            }
        }
    }

    // Real-Time Auto-Save triggering block
    LaunchedEffect(editingTitle, editingTextValue.text, editingDate, editingTime, editingAttachments) {
        val entryId = activeEditingEntryId ?: return@LaunchedEffect
        delay(550) // Debounce frequency
        val parsedTimestamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$editingDate $editingTime")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }

        val updatedEntry = JournalEntry(
            id = entryId,
            title = editingTitle,
            text = editingTextValue.text,
            dateString = editingDate,
            timestamp = parsedTimestamp,
            attachmentsJson = editingAttachments.joinToString(";;")
        )
        viewModel.updateJournalEntry(updatedEntry)
    }

    if (showEditorScreen && activeEditingEntryId != null) {
        // Fullscreen Advanced Diary Editor screen
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Charcoal
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { showEditorScreen = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Timeline", tint = Color.White)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Green)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Autosaved", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Trash option
                        IconButton(onClick = {
                            val activeId = activeEditingEntryId
                            if (activeId != null) {
                                val found = entries.find { it.id == activeId }
                                if (found != null) {
                                    viewModel.deleteJournalEntry(found)
                                }
                            }
                            showEditorScreen = false
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Discard Chronicle", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Line 1: Heading/Title
                TextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it },
                    placeholder = { Text("Heading", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.fillMaxWidth().testTag("journal_heading_input")
                )

                // Customizable DateTime Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val calendar = java.util.Calendar.getInstance()
                                if (editingDate.isNotEmpty()) {
                                    try {
                                        val parts = editingDate.split("-")
                                        if (parts.size == 3) {
                                            calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                                            calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                                            calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                                        }
                                    } catch (e: Exception) {}
                                }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        editingDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                    },
                                    calendar.get(java.util.Calendar.YEAR),
                                    calendar.get(java.util.Calendar.MONTH),
                                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        TextField(
                            value = editingDate,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Custom Date (YYYY-MM-DD)", fontSize = 9.sp) },
                            placeholder = { Text("Pick Date") },
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = WaterBlue,
                                disabledLabelColor = Color.LightGray,
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val calendar = java.util.Calendar.getInstance()
                                if (editingTime.isNotEmpty()) {
                                    try {
                                        val parts = editingTime.split(":")
                                        if (parts.size == 2) {
                                            calendar.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
                                            calendar.set(java.util.Calendar.MINUTE, parts[1].toInt())
                                        }
                                    } catch (e: Exception) {}
                                }
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        editingTime = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
                                    },
                                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                    calendar.get(java.util.Calendar.MINUTE),
                                    true
                                ).show()
                            }
                    ) {
                        TextField(
                            value = editingTime,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Custom Time (HH:MM)", fontSize = 9.sp) },
                            placeholder = { Text("Pick Time") },
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = WaterBlue,
                                disabledLabelColor = Color.LightGray,
                                disabledContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Unlimited Description text field tracking selection values (Scrollable column with inline media preview)
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        TextField(
                            value = editingTextValue,
                            onValueChange = { editingTextValue = it },
                            placeholder = { Text("Write your epic journey chronicle...", color = Color.Gray, fontSize = 14.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).testTag("journal_body_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // RENDER DETAILED MEDIA PREVIEW RIGHT IN EDITOR AS REQUESTED!
                        val mediaAttachments = editingAttachments.filter { it.startsWith("photo:") || it.startsWith("video:") || it.startsWith("audio:") || it.startsWith("file:") }
                        if (mediaAttachments.isNotEmpty()) {
                            Text("MEDIA FILES ATTACHED", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            mediaAttachments.forEach { attach ->
                                JournalMediaItem(
                                    context = context,
                                    attach = attach,
                                    isEditing = true,
                                    onDelete = {
                                        editingAttachments = editingAttachments.filter { it != attach }
                                    }
                                )
                            }
                        }
                    }

                    // Autocomplete drop down suggestions for @ contact mentions
                    val typedWord = remember(editingTextValue.text, editingTextValue.selection) {
                        try {
                            val text = editingTextValue.text
                            val cursor = editingTextValue.selection.end
                            if (cursor in 1..text.length) {
                                val sub = text.substring(0, cursor)
                                val spaceIdx = sub.lastIndexOf(' ')
                                val word = if (spaceIdx != -1) sub.substring(spaceIdx + 1) else sub
                                if (word.startsWith("@")) word else ""
                            } else ""
                        } catch (e: Exception) {
                            ""
                        }
                    }

                    if (typedWord.isNotEmpty()) {
                        val term = typedWord.removePrefix("@").lowercase()
                        val matchingContacts = contacts.filter {
                            it.firstName.lowercase().contains(term) || it.lastName.lowercase().contains(term)
                        }

                        if (matchingContacts.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .heightIn(max = 120.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = BorderStroke(1.dp, WaterBlue.copy(alpha = 0.5f))
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    items(matchingContacts) { contact ->
                                        val contactName = "${contact.firstName}${contact.lastName}"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val completed = "@$contactName "
                                                    val text = editingTextValue.text
                                                    val cursor = editingTextValue.selection.end
                                                    val sub = text.substring(0, cursor)
                                                    val spaceIdx = sub.lastIndexOf(' ')
                                                    val prefix = if (spaceIdx != -1) text.substring(0, spaceIdx + 1) else ""
                                                    val suffix = text.substring(cursor)
                                                    val resultText = prefix + completed + suffix
                                                    val newCursor = (prefix + completed).length
                                                    editingTextValue = TextFieldValue(
                                                        text = resultText,
                                                        selection = TextRange(newCursor)
                                                    )
                                                }
                                                .padding(vertical = 6.dp, horizontal = 10.dp)
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("${contact.firstName} ${contact.lastName}", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // In-editor rich attachments indicators row
                if (editingAttachments.isNotEmpty()) {
                    Text("Attachments (${editingAttachments.size}):", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        editingAttachments.forEachIndexed { index, attach ->
                            val label = when {
                                attach.startsWith("photo:") -> "📷 Photo"
                                attach.startsWith("video:") -> "🎥 Video"
                                attach.startsWith("audio:") -> "🎙️ Voice Record"
                                attach.startsWith("loc:") -> "📍 Map Tag"
                                else -> "📁 Doc Attached"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable {
                                        // Option to remove attachment
                                        editingAttachments = editingAttachments.filterIndexed { idx, _ -> idx != index }
                                        Toast.makeText(context, "Attachment removed", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }

                // Toolbar panel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bold editor format text option
                        IconButton(onClick = {
                            val selStart = editingTextValue.selection.start
                            val selEnd = editingTextValue.selection.end
                            val original = editingTextValue.text
                            val newText = if (selStart != selEnd) {
                                original.substring(0, selStart) + "**" + original.substring(selStart, selEnd) + "**" + original.substring(selEnd)
                            } else {
                                original.substring(0, selStart) + "**bold**" + original.substring(selStart)
                            }
                            editingTextValue = TextFieldValue(text = newText, selection = TextRange(selStart + 2, selEnd + 6))
                        }) {
                            Text("B", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        // Point wise list option
                        IconButton(onClick = {
                            val selStart = editingTextValue.selection.start
                            val original = editingTextValue.text
                            val newText = original.substring(0, selStart) + "\n• " + original.substring(selStart)
                            editingTextValue = TextFieldValue(text = newText, selection = TextRange(selStart + 3))
                        }) {
                            Text("• List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        // Get Device Location option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else {
                                Toast.makeText(context, "Pinpointing GPS...", Toast.LENGTH_SHORT).show()
                                triggerFetchLocation(context) { lat, lng, city ->
                                    val locAttach = "loc:$city|coords:$lat,$lng"
                                    editingAttachments = editingAttachments + locAttach
                                    Toast.makeText(context, "Added Geotag: $city", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Add Location", tint = WaterBlue)
                        }

                        // Snaps direct internal photo option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            } else {
                                val outPhotoFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "journal_photo_${System.currentTimeMillis()}.jpg")
                                activePhotoFile = outPhotoFile
                                val photoUri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", outPhotoFile)
                                takePhotoLauncher.launch(photoUri)
                            }
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Snapper", tint = Color.White)
                        }

                        // Snaps direct internal video option
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            } else {
                                val outVideoFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "journal_video_${System.currentTimeMillis()}.mp4")
                                activeVideoFile = outVideoFile
                                val videoUri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", outVideoFile)
                                captureVideoLauncher.launch(videoUri)
                            }
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Recorder", tint = Color.White)
                        }

                        // Record voice audio option
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                } else {
                                    if (isRecordingAudio) {
                                        // Stop recording
                                        try {
                                            audioRecorder?.stop()
                                            audioRecorder?.release()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        audioRecorder = null
                                        isRecordingAudio = false
                                        val rec = currentAudioRecordingFile
                                        if (rec != null) {
                                            val compressedFile = File(rec.parentFile, "${rec.name}.gz")
                                            val compressSuccess = MediaCompressionHelper.compressFileGzip(rec, compressedFile)
                                            val finalAudioFile = if (compressSuccess) {
                                                rec.delete()
                                                compressedFile
                                            } else {
                                                rec
                                            }
                                            editingAttachments = editingAttachments + "audio:${finalAudioFile.absolutePath}"
                                            Toast.makeText(context, "Voice memo attached & compressed!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // Start recording code
                                        val recFile = File(com.example.util.StorageHelper.getAppFilesDir(context), "voice_${System.currentTimeMillis()}.mp3")
                                        currentAudioRecordingFile = recFile
                                        try {
                                            audioRecorder = MediaRecorder().apply {
                                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                setAudioEncodingBitRate(32000) // 32 kbps voice codec compression
                                                setAudioSamplingRate(16000) // 16 kHz high compression speech sample rate
                                                setOutputFile(recFile.absolutePath)
                                                prepare()
                                                start()
                                            }
                                            isRecordingAudio = true
                                            Toast.makeText(context, "🎙️ Recording audio (Tap mic to stop)...", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Failure preparing recorder", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isRecordingAudio) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice Memo Record",
                                tint = if (isRecordingAudio) Color.Red else Color.LightGray
                            )
                        }

                        // Document selector attachment option
                        IconButton(onClick = {
                            pickDocumentLauncher.launch("*/*")
                        }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach Document", tint = Color.LightGray)
                        }
                    }
                }
            }
        }
    } else {
        // Main Chronicles Dashboard Layout representation with Sidebar Toggle control
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar containing plus action only
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSidebarExpanded = !isSidebarExpanded }) {
                            Icon(Icons.Default.Menu, contentDescription = "Toggle Sidebar Manager", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "JOURNAL - ${currentJournalTab.uppercase()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var isSummarizing by remember { mutableStateOf(false) }
                        
                        IconButton(
                            onClick = {
                                isSummarizing = true
                                viewModel.summarizeDayIntoJournalEntry { outcome ->
                                    isSummarizing = false
                                    Toast.makeText(context, "AI Daily Summary Added to Journal!", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF0F2622))
                                .size(40.dp)
                                .testTag("summarize_today_btn")
                        ) {
                            if (isSummarizing) {
                                CircularProgressIndicator(
                                    color = Color(0xFF00BFA5),
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "AI Summarize Today",
                                    tint = Color(0xFF00BFA5)
                                )
                            }
                        }

                        // Large Plus icon triggering inserting new draft
                        IconButton(
                            onClick = {
                                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val nowStrDate = sdfDate.format(Date())
                                val nowStrTime = sdfTime.format(Date())
                                val initialText = "Date & Time: $nowStrDate $nowStrTime\n\n"

                                scope.launch {
                                    val generatedId = viewModel.createJournalEntryWithId(
                                        title = "",
                                        text = initialText,
                                        dateString = nowStrDate,
                                        timestamp = System.currentTimeMillis(),
                                        attachments = ""
                                    )
                                    activeEditingEntryId = generatedId
                                    editingTitle = ""
                                    editingTextValue = TextFieldValue(initialText)
                                    editingDate = nowStrDate
                                    editingTime = nowStrTime
                                    editingAttachments = emptyList()
                                    showEditorScreen = true
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(WaterBlue)
                                .size(40.dp)
                                .testTag("create_diary_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Diary entry", tint = Color.Black)
                        }

                        // Calendar date picker button
                        IconButton(
                            onClick = {
                                val calendar = java.util.Calendar.getInstance()
                                val datePickerDialog = android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedCal = java.util.Calendar.getInstance().apply {
                                            set(year, month, dayOfMonth)
                                        }
                                        val selectedDateStr = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                        
                                        when (currentJournalTab) {
                                            "Timeline" -> {
                                                val index = entries.indexOfFirst { it.dateString == selectedDateStr }
                                                if (index != -1) {
                                                    scope.launch {
                                                        timelineListState.animateScrollToItem(index)
                                                    }
                                                    Toast.makeText(context, "Scrolled to entry for $selectedDateStr", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val closestIndex = entries.indices.minByOrNull { i ->
                                                        val entryDate = try {
                                                            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(entries[i].dateString)
                                                        } catch (e: Exception) { null }
                                                        if (entryDate != null) {
                                                            Math.abs(entryDate.time - selectedCal.timeInMillis)
                                                        } else {
                                                            Long.MAX_VALUE
                                                        }
                                                    } ?: -1
                                                    if (closestIndex != -1) {
                                                        scope.launch {
                                                            timelineListState.animateScrollToItem(closestIndex)
                                                        }
                                                        val closestDate = entries[closestIndex].dateString
                                                        Toast.makeText(context, "No entry on $selectedDateStr. Scrolled to closest ($closestDate)", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "No journal entries found.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            "Monthly" -> {
                                                val currentCal = java.util.Calendar.getInstance()
                                                val yearDiff = selectedCal.get(java.util.Calendar.YEAR) - currentCal.get(java.util.Calendar.YEAR)
                                                val monthDiff = selectedCal.get(java.util.Calendar.MONTH) - currentCal.get(java.util.Calendar.MONTH)
                                                val totalMonthOffset = yearDiff * 12 + monthDiff
                                                val targetIndex = totalMonthOffset + 60
                                                if (targetIndex in 0..72) {
                                                    scope.launch {
                                                        monthlyListState.animateScrollToItem(targetIndex)
                                                    }
                                                    val monthName = selectedCal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.getDefault()) ?: ""
                                                    Toast.makeText(context, "Scrolled to $monthName ${selectedCal.get(java.util.Calendar.YEAR)}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Selected month is outside the 5-year range.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            "On This Day" -> {
                                                selectedOnThisDayDayMonth = String.format(java.util.Locale.US, "%02d-%02d", month + 1, dayOfMonth)
                                                Toast.makeText(context, "Viewing anniversary for ${selectedOnThisDayDayMonth}", Toast.LENGTH_SHORT).show()
                                            }
                                            "Map View" -> {
                                                mapScrollToDate = selectedDateStr
                                            }
                                        }
                                    },
                                    calendar.get(java.util.Calendar.YEAR),
                                    calendar.get(java.util.Calendar.MONTH),
                                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                )
                                datePickerDialog.show()
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF232D37))
                                .size(40.dp)
                                .testTag("journal_calendar_picker_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select Date",
                                tint = WaterBlue
                            )
                        }
                    }
                }

                // Dashboard views switcher
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        when (currentJournalTab) {
                            "Timeline" -> {
                                Column {
                                    Text(
                                        text = "TIMELINE CHRONOLOGY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    val currentDayMonth = remember(entries) { SimpleDateFormat("MM-dd", Locale.US).format(Date()) }
                                    val matchedAnniversaryEntries = remember(entries) { entries.filter { it.dateString.endsWith(currentDayMonth) } }

                                    if (onThisDayOnScreenEnabled && matchedAnniversaryEntries.isNotEmpty() && !isOnThisDayReminderDismissed) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp)
                                                .border(1.dp, WaterBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = "Anniversary Icon",
                                                    tint = WaterBlue,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "On This Day Reminder",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "You have ${matchedAnniversaryEntries.size} historic entries written on this day in history!",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "View Anniversary Entries",
                                                        color = WaterBlue,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.clickable {
                                                            currentJournalTab = "On This Day"
                                                            viewModel.updateDefaultJournalView("On This Day")
                                                        }
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { isOnThisDayReminderDismissed = true }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (entries.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No chronicles documented. Click the + to persist today's record.", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyColumn(
                                                state = timelineListState,
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                            items(entries) { entry ->
                                                val photoPath = remember(entry) {
                                                    entry.attachmentsJson
                                                        .split(";;")
                                                        .find { it.trim().startsWith("photo:") }
                                                        ?.removePrefix("photo:")
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewingEntry = entry }
                                                        .background(SurfaceCard, RoundedCornerShape(12.dp))
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Box with Date and Month on Left Edge
                                                    Box(
                                                        modifier = Modifier
                                                            .width(68.dp)
                                                            .height(58.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (!photoPath.isNullOrEmpty()) Color.Transparent else WaterBlue)
                                                            .padding(4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (!photoPath.isNullOrEmpty()) {
                                                            AsyncImage(
                                                                model = if (photoPath.startsWith("http")) photoPath else java.io.File(photoPath),
                                                                contentDescription = "Background photo",
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .background(Color.Black.copy(alpha = 0.5f))
                                                            )
                                                        }
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            val dateParts = try {
                                                                val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                                val d = inputSdf.parse(entry.dateString)
                                                                val outMonth = SimpleDateFormat("MMM", Locale.getDefault()).format(d)
                                                                val outDay = SimpleDateFormat("dd", Locale.US).format(d)
                                                                Pair(outMonth.uppercase(), outDay)
                                                            } catch (e: Exception) {
                                                                Pair("MEM", "??")
                                                            }
                                                            Text(
                                                                text = dateParts.first,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                            Text(
                                                                text = dateParts.second,
                                                                fontSize = 20.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    // Content with constraints
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = if (entry.title.isNotEmpty()) entry.title else "Untitled Chronicle",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color.White,
                                                            fontSize = 14.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        // Strictly show just 1 or 2 lines for card description matching requirement
                                                        Text(
                                                            text = parseMarkdown(entry.text),
                                                            color = Color.LightGray,
                                                            fontSize = 12.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            lineHeight = 16.sp
                                                        )

                                                        // Check location tags
                                                        if (entry.attachmentsJson.contains("loc:")) {
                                                            val cleanLoc = entry.attachmentsJson
                                                                .split(";;")
                                                                .find { it.trim().startsWith("loc:") }
                                                                ?.removePrefix("loc:")
                                                                ?.split("|coords:")
                                                                ?.getOrNull(0) ?: ""
                                                            if (cleanLoc.isNotEmpty()) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = WaterBlue, modifier = Modifier.size(10.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(cleanLoc, color = WaterBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }

                                                        // Parse and display Hashtags and contact mentions on Card's very last line
                                                        val hashTags = remember(entry.text) {
                                                            Regex("""#\w+""").findAll(entry.text).map { it.value }.toList()
                                                        }
                                                        val contactTags = remember(entry.text) {
                                                            Regex("""@\w+""").findAll(entry.text).map { it.value }.toList()
                                                        }

                                                        if (hashTags.isNotEmpty() || contactTags.isNotEmpty()) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                                            ) {
                                                                contactTags.forEach { contact ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(4.dp))
                                                                            .background(Color(0xFF2E4057))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(text = contact, color = WaterBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                                hashTags.forEach { tag ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .clip(RoundedCornerShape(4.dp))
                                                                            .background(Color(0xFF1D2C42))
                                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text(text = tag, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(6.dp))


                                                }
                                            }
                                        }

                                        val showButton by remember {
                                                derivedStateOf {
                                                    timelineListState.firstVisibleItemIndex > 0
                                                }
                                            }
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = showButton,
                                                enter = fadeIn() + expandIn(),
                                                exit = fadeOut() + shrinkOut(),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(16.dp)
                                            ) {
                                                FloatingActionButton(
                                                    onClick = {
                                                        scope.launch {
                                                            timelineListState.animateScrollToItem(0)
                                                        }
                                                    },
                                                    containerColor = WaterBlue,
                                                    contentColor = Color.Black,
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp,
                                                        contentDescription = "Scroll to top"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Monthly" -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val monthOffsets = remember { (-60..12).toList() }
                                    val todayCal = remember { java.util.Calendar.getInstance() }
                                    val todayDateStr = remember {
                                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                    }

                                    // Calendar Header Actions
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "MONTHLY PHOTO CALENDAR",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )

                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    monthlyListState.animateScrollToItem(60)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Today,
                                                    contentDescription = null,
                                                    tint = WaterBlue,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Jump to Today",
                                                    color = WaterBlue,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        state = monthlyListState,
                                        verticalArrangement = Arrangement.spacedBy(20.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(monthOffsets) { offset ->
                                            // Get month configuration
                                            val monthData = remember(offset) {
                                                val cal = java.util.Calendar.getInstance()
                                                cal.add(java.util.Calendar.MONTH, offset)
                                                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                                val monthName = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.getDefault()) ?: ""
                                                val year = cal.get(java.util.Calendar.YEAR)
                                                val monthValue = cal.get(java.util.Calendar.MONTH)
                                                val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                                                val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                                                val yearMonthStr = String.format(java.util.Locale.US, "%04d-%02d", year, monthValue + 1)

                                                val numBlanks = firstDayOfWeek - 1
                                                val cellsList = mutableListOf<Int?>()
                                                repeat(numBlanks) { cellsList.add(null) }
                                                for (d in 1..daysInMonth) { cellsList.add(d) }
                                                while (cellsList.size % 7 != 0) { cellsList.add(null) }

                                                Triple(monthName.uppercase(), year, yearMonthStr to cellsList)
                                            }

                                            val (monthName, year, cellsPair) = monthData
                                            val (yearMonthStr, cellsList) = cellsPair
                                            val weeks = remember(cellsList) { cellsList.chunked(7) }

                                            val isCurrentMonthYear = remember(monthName, year) {
                                                monthName.uppercase() == todayCal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, java.util.Locale.getDefault())?.uppercase() &&
                                                        year == todayCal.get(java.util.Calendar.YEAR)
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.5f)),
                                                border = if (isCurrentMonthYear) BorderStroke(1.dp, WaterBlue.copy(alpha = 0.3f)) else null,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    // Month Header inside the card
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "$monthName $year",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isCurrentMonthYear) WaterBlue else Color.White
                                                        )
                                                        if (isCurrentMonthYear) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(WaterBlue.copy(alpha = 0.2f))
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("Current Month", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WaterBlue)
                                                            }
                                                        }
                                                    }

                                                    // Days of Week labels
                                                    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 6.dp)
                                                    ) {
                                                        daysOfWeek.forEach { label ->
                                                            Text(
                                                                text = label,
                                                                modifier = Modifier.weight(1f),
                                                                textAlign = TextAlign.Center,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.Gray
                                                            )
                                                        }
                                                    }

                                                    // Month Calendar Grid Rows
                                                    weeks.forEach { week ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            week.forEach { day ->
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .aspectRatio(1f)
                                                                ) {
                                                                    if (day != null) {
                                                                        val targetDateString = String.format(java.util.Locale.US, "%s-%02d", yearMonthStr, day)
                                                                        val isDayToday = (targetDateString == todayDateStr)

                                                                        val matchEntriesForDay = remember(entries, targetDateString) {
                                                                            entries.filter { it.dateString == targetDateString }
                                                                        }
                                                                        val matchEntry = matchEntriesForDay.firstOrNull()
                                                                        val photoPath = remember(matchEntry) {
                                                                            matchEntry?.attachmentsJson
                                                                                ?.split(";;")
                                                                                ?.find { it.trim().startsWith("photo:") }
                                                                                ?.removePrefix("photo:")
                                                                        }

                                                                        Box(
                                                                            modifier = Modifier
                                                                                .fillMaxSize()
                                                                                .clip(RoundedCornerShape(8.dp))
                                                                                .background(
                                                                                    if (matchEntry != null) {
                                                                                        if (!photoPath.isNullOrEmpty()) Color.Transparent else WaterBlue.copy(alpha = 0.15f)
                                                                                    } else {
                                                                                        SurfaceCard
                                                                                    }
                                                                                )
                                                                                .border(
                                                                                    width = if (isDayToday) 2.dp else (if (matchEntry != null) 1.dp else 0.dp),
                                                                                    color = if (isDayToday) WaterBlue else (if (matchEntry != null) WaterBlue.copy(alpha = 0.6f) else Color.Transparent),
                                                                                    shape = RoundedCornerShape(8.dp)
                                                                                )
                                                                                .clickable {
                                                                                    if (matchEntry != null) {
                                                                                        viewingEntry = matchEntry
                                                                                    } else {
                                                                                        // Open creator with targetDateString pre-populated!
                                                                                        val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                                                                        val nowStrTime = sdfTime.format(java.util.Date())
                                                                                        val targetInitialText = "Date & Time: $targetDateString $nowStrTime\n\n"

                                                                                        val parsedDate = try {
                                                                                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(targetDateString)
                                                                                        } catch (e: Exception) {
                                                                                            null
                                                                                        }
                                                                                        val entryTimestamp = parsedDate?.time ?: System.currentTimeMillis()

                                                                                        scope.launch {
                                                                                            val generatedId = viewModel.createJournalEntryWithId(
                                                                                                title = "",
                                                                                                text = targetInitialText,
                                                                                                dateString = targetDateString,
                                                                                                timestamp = entryTimestamp,
                                                                                                attachments = ""
                                                                                            )
                                                                                            activeEditingEntryId = generatedId
                                                                                            editingTitle = ""
                                                                                            editingTextValue = androidx.compose.ui.text.input.TextFieldValue(targetInitialText)
                                                                                            editingDate = targetDateString
                                                                                            editingTime = nowStrTime
                                                                                            editingAttachments = emptyList()
                                                                                            showEditorScreen = true
                                                                                        }
                                                                                        Toast.makeText(context, "Drafting chronicle for $targetDateString...", Toast.LENGTH_SHORT).show()
                                                                                    }
                                                                                },
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            if (!photoPath.isNullOrEmpty()) {
                                                                                AsyncImage(
                                                                                    model = if (photoPath.startsWith("http")) photoPath else java.io.File(photoPath),
                                                                                    contentDescription = "Memory illustration for Day $day",
                                                                                    contentScale = ContentScale.Crop,
                                                                                    modifier = Modifier.fillMaxSize()
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .fillMaxSize()
                                                                                        .background(Color.Black.copy(alpha = 0.45f))
                                                                                )
                                                                            }

                                                                            Column(
                                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                                verticalArrangement = Arrangement.Center
                                                                            ) {
                                                                                Text(
                                                                                    text = day.toString(),
                                                                                    color = if (!photoPath.isNullOrEmpty()) {
                                                                                        Color.White
                                                                                    } else {
                                                                                        if (isDayToday) WaterBlue else (if (matchEntry != null) WaterBlue else Color.White)
                                                                                    },
                                                                                    fontSize = 13.sp,
                                                                                    fontWeight = if (isDayToday || matchEntry != null) FontWeight.ExtraBold else FontWeight.Medium,
                                                                                    textAlign = TextAlign.Center
                                                                                )

                                                                                if (matchEntriesForDay.isNotEmpty()) {
                                                                                    Spacer(modifier = Modifier.height(2.dp))
                                                                                    Row(
                                                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                                                        verticalAlignment = Alignment.CenterVertically
                                                                                    ) {
                                                                                        repeat(matchEntriesForDay.size.coerceAtMost(3)) {
                                                                                            Box(
                                                                                                modifier = Modifier
                                                                                                    .size(4.dp)
                                                                                                    .clip(CircleShape)
                                                                                                    .background(WaterBlue)
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
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "On This Day" -> {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "DOCUMENTED ON ANNIVERSARY ($selectedOnThisDayDayMonth)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray
                                        )
                                        
                                        val todayDayMonth = remember { SimpleDateFormat("MM-dd", Locale.US).format(Date()) }
                                        if (selectedOnThisDayDayMonth != todayDayMonth) {
                                            TextButton(
                                                onClick = {
                                                    selectedOnThisDayDayMonth = todayDayMonth
                                                    Toast.makeText(context, "Reset to Today's anniversary", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Reset to Today's anniversary",
                                                        tint = WaterBlue,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Reset to Today",
                                                        color = WaterBlue,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val matchedAnniversaryEntries = entries.filter { it.dateString.endsWith(selectedOnThisDayDayMonth) }

                                    if (matchedAnniversaryEntries.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No historical records documented on anniversary day ($selectedOnThisDayDayMonth).", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                                        }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(matchedAnniversaryEntries) { entry ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewingEntry = entry },
                                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(entry.title, fontWeight = FontWeight.ExtraBold, color = WaterBlue, fontSize = 13.sp)
                                                            Text(entry.dateString, color = Color.Gray, fontSize = 10.sp)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(parseMarkdown(entry.text), color = Color.White, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Map View" -> {
                                JournalMapView(
                                    viewModel = viewModel,
                                    entries = entries,
                                    onEntryClick = { entry ->
                                        viewingEntry = entry
                                    },
                                    scrollToDate = mapScrollToDate,
                                    onScrollToDateHandled = {
                                        mapScrollToDate = null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // Floating Custom Navigation Drawer Sidebar Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isSidebarExpanded,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { isSidebarExpanded = false }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSidebarExpanded,
                enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }),
                exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.fillMaxHeight().width(300.dp).align(Alignment.CenterStart)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(end = 12.dp)
                        .shadow(8.dp, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "CHRONICLES",
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterBlue,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        val categories = listOf(
                            Triple("Timeline", "📅", "Timeline View"),
                            Triple("Monthly", "📆", "Monthly Calendar"),
                            Triple("On This Day", "🎉", "On This Day"),
                            Triple("Map View", "📍", "Locations Map")
                        )

                        categories.forEach { (tabId, icon, label) ->
                            val isSelected = currentJournalTab == tabId
                            val itemBg = if (isSelected) WaterBlue.copy(alpha = 0.15f) else Color.Transparent
                            val itemBorder = if (isSelected) 1.dp to WaterBlue else 0.dp to Color.Transparent
                            val textColor = if (isSelected) WaterBlue else Color.White

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(itemBg)
                                    .border(itemBorder.first, itemBorder.second, RoundedCornerShape(8.dp))
                                    .clickable {
                                        currentJournalTab = tabId
                                        viewModel.updateDefaultJournalView(tabId)
                                        isSidebarExpanded = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }



    // Immersive Fullscreen Reading View Page to inspect entry details with fully active multi-format inbuilt players
    if (viewingEntry != null) {
        val entry = viewingEntry!!
        val attachments = remember(entry.attachmentsJson) {
            if (entry.attachmentsJson.isNotEmpty()) entry.attachmentsJson.split(";;") else emptyList()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Charcoal
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar (Top bar with Back control, and edit/delete in top right corner!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewingEntry = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }

                    // Top Right buttons: edit (pencil) and delete (trash)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            try {
                                android.util.Log.d("JournalBookView", "Pencil click initiated for entry ID: ${entry.id}")
                                activeEditingEntryId = entry.id
                                editingTitle = entry.title
                                editingTextValue = androidx.compose.ui.text.input.TextFieldValue(entry.text)
                                editingDate = entry.dateString
                                
                                val parsedTime = try {
                                    val entrySdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    entrySdfTime.format(Date(entry.timestamp))
                                } catch (ex: Exception) {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                }
                                editingTime = parsedTime

                                editingAttachments = if (entry.attachmentsJson.isNotEmpty()) {
                                    entry.attachmentsJson.split(";;")
                                } else {
                                    emptyList()
                                }
                                
                                showEditorScreen = true
                                viewingEntry = null
                                android.util.Log.d("JournalBookView", "Pencil edit mode activated successfully. showEditorScreen = true")
                            } catch (e: Exception) {
                                android.util.Log.e("JournalBookView", "Failure switching pencil edit mode: ${e.message}", e)
                                // Resilient failover path
                                activeEditingEntryId = entry.id
                                editingTitle = entry.title
                                editingTextValue = androidx.compose.ui.text.input.TextFieldValue(entry.text)
                                editingDate = entry.dateString
                                editingTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                editingAttachments = emptyList()
                                showEditorScreen = true
                                viewingEntry = null
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Epic Chronicle",
                                tint = WaterBlue
                            )
                        }

                        IconButton(onClick = {
                            viewModel.deleteJournalEntry(entry)
                            viewingEntry = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Memory",
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable details column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header title/headline
                    Text(
                        text = if (entry.title.isNotEmpty()) entry.title else "Untitled memory",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    // Date & Time, Location tags, etc.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val entrySdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val formattedTimeStr = entrySdfTime.format(Date(entry.timestamp))

                        Text(
                            text = "${entry.dateString} at $formattedTimeStr",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // If any location exists, show location
                        if (entry.attachmentsJson.contains("loc:")) {
                            val cleanLoc = entry.attachmentsJson
                                .split(";;")
                                .find { it.trim().startsWith("loc:") }
                                ?.removePrefix("loc:")
                                ?.split("|coords:")
                                ?.getOrNull(0) ?: ""
                            if (cleanLoc.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        try {
                                            val fullLocStr = entry.attachmentsJson
                                                .split(";;")
                                                .find { it.trim().startsWith("loc:") } ?: ""
                                            val parts = fullLocStr.removePrefix("loc:").split("|coords:")
                                            val address = parts.getOrNull(0)?.trim() ?: ""
                                            val coords = parts.getOrNull(1)?.trim() ?: ""
                                            
                                            val uriString = if (coords.isNotEmpty()) {
                                                "https://www.google.com/maps/search/?api=1&query=$coords"
                                            } else {
                                                "https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode(address)}"
                                            }
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString)).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("JournalBookView", "Failed to open Google Maps", e)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = WaterBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = cleanLoc,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)

                    // Description text wordings
                    if (entry.text.isNotEmpty()) {
                        Text(
                            text = parseMarkdown(entry.text),
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    } else {
                        // User requested "if no wording just leave a space ..."
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media and files showing up AFTER the description wordings!
                    attachments.forEach { attach ->
                        JournalMediaItem(
                            context = context,
                            attach = attach,
                            isEditing = false,
                            onDelete = {
                                val newList = attachments.filter { it != attach }
                                val updated = entry.copy(attachmentsJson = newList.joinToString(";;"))
                                viewModel.updateJournalEntry(updated)
                                viewingEntry = updated
                            }
                        )
                    }
                }
            }
        }
    }
}

// Location lookup helper
private fun getCityNameFromCoords(context: Context, latitude: Double, longitude: Double): String {
    val isDemoAustin = Math.abs(latitude - 30.2672) < 0.1 && Math.abs(longitude - (-97.7431)) < 0.1
    if (isDemoAustin) {
        return "Guntur, Andhra Pradesh"
    }

    try {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: address.adminArea
                val state = address.adminArea
                if (city != null) {
                    return if (state != null) "$city, $state" else city
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Guntur, Andhra Pradesh"
}

private fun triggerFetchLocation(context: Context, onLocationFound: (latitude: Double, longitude: Double, name: String) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
        return
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
        return
    }

    try {
        val providers = locationManager.getProviders(true)
        var lastLoc: Location? = null
        for (provider in providers) {
            val loc = locationManager.getLastKnownLocation(provider) ?: continue
            if (lastLoc == null || loc.accuracy < lastLoc.accuracy) {
                lastLoc = loc
            }
        }

        if (lastLoc != null) {
            val cityName = getCityNameFromCoords(context, lastLoc.latitude, lastLoc.longitude)
            onLocationFound(lastLoc.latitude, lastLoc.longitude, cityName)
        } else {
            locationManager.requestSingleUpdate(
                LocationManager.GPS_PROVIDER,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val cityName = getCityNameFromCoords(context, location.latitude, location.longitude)
                        onLocationFound(location.latitude, location.longitude, cityName)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                },
                null
            )
            onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
        }
    } catch (e: SecurityException) {
        onLocationFound(16.3067, 80.4365, "Guntur, Andhra Pradesh")
    } catch (e: Exception) {
        onLocationFound(11.6643, 78.1460, "Salem, Tamil Nadu")
    }
}

// Document sandbox copy utility
private fun copyFileToInternalSandbox(context: Context, uri: Uri): File? {
    return com.example.util.StorageHelper.copyFileToInternalSandbox(context, uri)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun JournalMediaItem(
    context: android.content.Context,
    attach: String,
    isEditing: Boolean,
    onDelete: () -> Unit
) {
    var showOptionsDialog by remember { mutableStateOf(false) }
    val path = remember(attach) {
        when {
            attach.startsWith("photo:") -> attach.removePrefix("photo:")
            attach.startsWith("video:") -> attach.removePrefix("video:")
            attach.startsWith("audio:") -> attach.removePrefix("audio:")
            attach.startsWith("file:") -> {
                val filePart = attach.removePrefix("file:").split("|path:")
                filePart.getOrNull(1) ?: ""
            }
            else -> ""
        }
    }
    val isWebUrl = remember(path) { path.startsWith("http://") || path.startsWith("https://") }
    val file = remember(path, isWebUrl) { if (isWebUrl) java.io.File("") else java.io.File(path) }
    val displayName = remember(file, attach, isWebUrl) { 
        if (attach.startsWith("file:")) {
            val filePart = attach.removePrefix("file:").split("|path:")
            filePart.getOrNull(0) ?: "Attached Document"
        } else if (isWebUrl) {
            path.substringAfterLast("/").substringBefore("?")
        } else {
            file.name.substringAfter("doc_").substringAfter("_")
        }
    }

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Attachment Options", color = Color.White) },
            text = { Text("Choose action for '$displayName':", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        showOptionsDialog = false
                        if (isWebUrl) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open link: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val success = saveFileToDownloads(context, file, displayName)
                            if (success) {
                                Toast.makeText(context, "Saved successfully to Downloads folder!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text(if (isWebUrl) "Open in Browser" else "Save to Device")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOptionsDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            containerColor = SurfaceCard
        )
    }

    when {
        attach.startsWith("photo:") -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            try {
                                if (isWebUrl) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(path)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val authority = "com.example.fileprovider"
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open Photo"))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Box {
                    AsyncImage(
                        model = if (isWebUrl) path else file,
                        contentDescription = "Attached photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    if (isEditing) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete Attachment", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        attach.startsWith("video:") -> {
            var requestedVideoPlay by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            requestedVideoPlay = true
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Box {
                    if (!requestedVideoPlay || isEditing) {
                        val thumbnailBitmap = rememberVideoThumbnail(path)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailBitmap != null) {
                                Image(
                                    bitmap = thumbnailBitmap,
                                    contentDescription = "Video Thumbnail Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    } else {
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        var videoPosition by remember { mutableStateOf(0) }
                        var isBackgrounded by remember { mutableStateOf(false) }
                        var videoViewReference by remember { mutableStateOf<VideoView?>(null) }

                        DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                                    videoViewReference?.let { vv ->
                                        if (vv.isPlaying) {
                                            videoPosition = vv.currentPosition
                                            vv.pause()
                                            isBackgrounded = true
                                            com.example.util.BackgroundMediaManager.play(path, videoPosition)
                                        }
                                    }
                                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                                    if (isBackgrounded) {
                                        isBackgrounded = false
                                        val bgPath = com.example.util.BackgroundMediaManager.currentPlayingPath.value
                                        if (bgPath == path) {
                                            val currentBgPos = com.example.util.BackgroundMediaManager.getCurrentPosition()
                                            com.example.util.BackgroundMediaManager.stop()
                                            videoViewReference?.let { vv ->
                                                vv.seekTo(currentBgPos)
                                                vv.start()
                                            }
                                        }
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(path)
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    videoViewReference = this
                                    start()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                    if (isEditing) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete Attachment", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        attach.startsWith("audio:") -> {
            val globalPlayingPath by com.example.util.BackgroundMediaManager.currentPlayingPath.collectAsState()
            val globalIsPlaying by com.example.util.BackgroundMediaManager.isPlaying.collectAsState()

            val playPath = remember(path) {
                val sourceFile = File(path)
                if (sourceFile.exists() && MediaCompressionHelper.isGzipFile(sourceFile)) {
                    val tempPlayFile = File(context.cacheDir, "play_decompressed_${sourceFile.name.removeSuffix(".gz")}")
                    if (!tempPlayFile.exists() || tempPlayFile.length() == 0L) {
                        MediaCompressionHelper.decompressFileGzip(sourceFile, tempPlayFile)
                    }
                    tempPlayFile.absolutePath
                } else {
                    path
                }
            }

            val isPlayingMusic = (globalPlayingPath == playPath && globalIsPlaying)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            if (isPlayingMusic) {
                                com.example.util.BackgroundMediaManager.pause()
                            } else {
                                if (globalPlayingPath == playPath) {
                                    com.example.util.BackgroundMediaManager.resume()
                                } else {
                                    com.example.util.BackgroundMediaManager.play(playPath)
                                }
                            }
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2C42))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isPlayingMusic) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = WaterBlue,
                            modifier = Modifier.size(24.dp)
                        )

                        Column {
                            Text("Audio Voice Note Playback", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(displayName, color = Color.LightGray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (isEditing) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                        }
                    }
                }
            }
        }

        attach.startsWith("file:") -> {
            val isPdf = remember(displayName) { displayName.lowercase().endsWith(".pdf") }
            val pdfBitmap = if (isPdf) rememberPdfFirstPagePreview(path) else null

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            try {
                                val authority = "com.example.fileprovider"
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open File"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = {
                            showOptionsDialog = true
                        }
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2C42))
            ) {
                Column {
                    if (isPdf && pdfBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = pdfBitmap,
                                contentDescription = "PDF Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.InsertDriveFile else Icons.Default.FileOpen,
                                contentDescription = null,
                                tint = WaterBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${file.length() / 1024} KB • Hold for options", color = Color.LightGray, fontSize = 9.sp)
                            }
                        }

                        if (isEditing) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun saveFileToDownloads(context: android.content.Context, sourceFile: java.io.File, displayName: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val mime = when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "3gp" -> "video/3gp"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            null
        }
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            true
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = java.io.File(downloadsDir, displayName)
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// Extract all entries with location coordinates
data class EntryMarker(
    val entry: JournalEntry,
    val title: String,
    val cityName: String,
    val lat: Double,
    val lng: Double
)

data class MarkerCluster(
    var centerLat: Double,
    var centerLng: Double,
    val markers: MutableList<EntryMarker>
)

fun clusterMarkers(markers: List<EntryMarker>, zoomLevel: Byte): List<MarkerCluster> {
    val clusters = mutableListOf<MarkerCluster>()
    // At very high zooms, don't cluster unless they are virtually on top of each other
    val radius = if (zoomLevel >= 14) {
        0.0005
    } else {
        // Adjust cluster radius dynamically by zoom level
        84.375 / (1 shl zoomLevel.toInt())
    }

    for (marker in markers) {
        val targetCluster = clusters.find { cluster ->
            val dLat = Math.abs(cluster.centerLat - marker.lat)
            val dLng = Math.abs(cluster.centerLng - marker.lng)
            dLat < radius && dLng < radius
        }

        if (targetCluster != null) {
            targetCluster.markers.add(marker)
            targetCluster.centerLat = targetCluster.markers.map { it.lat }.average()
            targetCluster.centerLng = targetCluster.markers.map { it.lng }.average()
        } else {
            clusters.add(
                MarkerCluster(
                    centerLat = marker.lat,
                    centerLng = marker.lng,
                    markers = mutableListOf(marker)
                )
            )
        }
    }
    return clusters
}

fun createClusterBitmap(context: android.content.Context, count: Int): org.mapsforge.core.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (36 * density).toInt()
    val androidBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#00D2FF") // WaterBlue color
        style = android.graphics.Paint.Style.FILL
    }
    // Draw glowing outer circle
    paint.alpha = 80
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, paint)

    // Draw inner circle
    paint.alpha = 255
    paint.color = android.graphics.Color.parseColor("#141416") // Dark slate background
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, paint)

    // Draw WaterBlue border on the inner circle
    paint.color = android.graphics.Color.parseColor("#00D2FF")
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2 * density
    canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, paint)

    // Draw text count
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    paint.textSize = 12 * density
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.isFakeBoldText = true

    val textHeight = paint.descent() - paint.ascent()
    val textOffset = textHeight / 2 - paint.descent()
    canvas.drawText(count.toString(), size / 2f, size / 2f + textOffset, paint)

    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, androidBitmap)
    return org.mapsforge.map.android.graphics.AndroidGraphicFactory.convertToBitmap(drawable)
}

fun createSinglePinBitmap(context: android.content.Context): org.mapsforge.core.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val androidBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#00D2FF") // WaterBlue color
        style = android.graphics.Paint.Style.FILL
    }
    // Draw soft outer halo
    paint.alpha = 60
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, paint)

    // Draw solid core
    paint.alpha = 255
    paint.color = android.graphics.Color.parseColor("#00D2FF")
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    // Draw white highlight border
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, androidBitmap)
    return org.mapsforge.map.android.graphics.AndroidGraphicFactory.convertToBitmap(drawable)
}

fun createMyLocationPinBitmap(context: android.content.Context): org.mapsforge.core.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val androidBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#10FA70")
        style = android.graphics.Paint.Style.FILL
    }
    paint.alpha = 60
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, paint)

    paint.alpha = 255
    paint.color = android.graphics.Color.parseColor("#10FA70")
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, androidBitmap)
    return org.mapsforge.map.android.graphics.AndroidGraphicFactory.convertToBitmap(drawable)
}

fun updateMforgeMarkers(
    mapView: org.mapsforge.map.android.view.MapView,
    context: android.content.Context,
    markersList: List<EntryMarker>,
    myLocation: org.mapsforge.core.model.LatLong?,
    onClusterClick: (List<JournalEntry>, String) -> Unit
) {
    val zoomLevel = mapView.model.mapViewPosition.zoomLevel
    val clusters = clusterMarkers(markersList, zoomLevel)

    val layers = mapView.layerManager.layers
    val toRemove = layers.filterIsInstance<org.mapsforge.map.layer.overlay.Marker>()
    layers.removeAll(toRemove)

    clusters.forEach { cluster ->
        val bitmap = if (cluster.markers.size > 1) {
            createClusterBitmap(context, cluster.markers.size)
        } else {
            createSinglePinBitmap(context)
        }

        val latLong = org.mapsforge.core.model.LatLong(cluster.centerLat, cluster.centerLng)
        val m = object : org.mapsforge.map.layer.overlay.Marker(latLong, bitmap, 0, -bitmap.getHeight() / 2) {
            override fun onTap(
                tapLatLong: org.mapsforge.core.model.LatLong?,
                viewPosition: org.mapsforge.core.model.Point?,
                tapPoint: org.mapsforge.core.model.Point?
            ): Boolean {
                if (contains(viewPosition, tapPoint)) {
                    val entries = cluster.markers.map { it.entry }
                    val name = cluster.markers.firstOrNull()?.cityName ?: "Cluster"
                    onClusterClick(entries, name)
                    return true
                }
                return false
            }
        }
        layers.add(m)
    }

    if (myLocation != null) {
        val locBitmap = createMyLocationPinBitmap(context)
        val locMarker = object : org.mapsforge.map.layer.overlay.Marker(myLocation, locBitmap, 0, -locBitmap.getHeight() / 2) {
            override fun onTap(
                tapLatLong: org.mapsforge.core.model.LatLong?,
                viewPosition: org.mapsforge.core.model.Point?,
                tapPoint: org.mapsforge.core.model.Point?
            ): Boolean {
                if (contains(viewPosition, tapPoint)) {
                    Toast.makeText(context, "You are here!", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }
        }
        layers.add(locMarker)
    }

    mapView.layerManager.redrawLayers()
}

@Composable
fun JournalMapView(
    viewModel: AppViewModel,
    entries: List<JournalEntry>,
    onEntryClick: (JournalEntry) -> Unit,
    modifier: Modifier = Modifier,
    scrollToDate: String? = null,
    onScrollToDateHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    var mforgeViewRef by remember { mutableStateOf<org.mapsforge.map.android.view.MapView?>(null) }
    var userLocationLatLong by remember { mutableStateOf<org.mapsforge.core.model.LatLong?>(null) }
    val currentMyLocation = androidx.compose.runtime.rememberUpdatedState(userLocationLatLong)

    val isDownloaded = remember { mutableStateOf(MapDownloadManager.isMapFileDownloaded(context, "southern")) }
    val isDownloading by MapDownloadManager.isDownloading.collectAsState()
    val downloadProgress by MapDownloadManager.downloadProgress.collectAsState()
    val downloadError by MapDownloadManager.downloadError.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineOk = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseOk = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineOk || coarseOk) {
            Toast.makeText(context, "Pinpointing GPS...", Toast.LENGTH_SHORT).show()
            triggerFetchLocation(context) { lat, lng, city ->
                val userLoc = org.mapsforge.core.model.LatLong(lat, lng)
                userLocationLatLong = userLoc
                mforgeViewRef?.setCenter(userLoc)
                mforgeViewRef?.setZoomLevel(14.toByte())
                Toast.makeText(context, "Centered at $city", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedLocationEntries by remember { mutableStateOf<List<JournalEntry>>(emptyList()) }
    var showLocationEntriesDialog by remember { mutableStateOf(false) }
    var selectedLocationName by remember { mutableStateOf("") }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isDownloading) {
        if (!isDownloading) {
            isDownloaded.value = MapDownloadManager.isMapFileDownloaded(context, "southern")
        }
    }

    val markers = remember(entries) {
        entries.mapNotNull { entry ->
            if (entry.attachmentsJson.contains("loc:")) {
                val part = entry.attachmentsJson.split(";;").find { it.trim().startsWith("loc:") }
                if (part != null) {
                    val cleanLoc = part.removePrefix("loc:")
                    val parts = cleanLoc.split("|coords:")
                    val cityName = parts.getOrNull(0) ?: ""
                    val coords = parts.getOrNull(1)?.split(",")
                    val lat = coords?.getOrNull(0)?.toDoubleOrNull()
                    val lng = coords?.getOrNull(1)?.toDoubleOrNull()
                    if (lat != null && lng != null) {
                        EntryMarker(entry, entry.title, cityName, lat, lng)
                    } else null
                } else null
            } else null
        }
    }

    LaunchedEffect(markers, userLocationLatLong, mforgeViewRef) {
        val map = mforgeViewRef ?: return@LaunchedEffect
        updateMforgeMarkers(map, context, markers, userLocationLatLong) { entries, name ->
            selectedLocationEntries = entries
            selectedLocationName = name
            showLocationEntriesDialog = true
        }
    }

    LaunchedEffect(scrollToDate, markers, mforgeViewRef) {
        val map = mforgeViewRef ?: return@LaunchedEffect
        if (!scrollToDate.isNullOrEmpty() && markers.isNotEmpty()) {
            val matchedMarker = markers.find { it.entry.dateString == scrollToDate }
            if (matchedMarker != null) {
                map.setCenter(org.mapsforge.core.model.LatLong(matchedMarker.lat, matchedMarker.lng))
                map.setZoomLevel(14.toByte())
                Toast.makeText(context, "Centered map on entry from $scrollToDate", Toast.LENGTH_SHORT).show()
            } else {
                val closestMarker = markers.minByOrNull { m ->
                    val mDate = try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(m.entry.dateString)
                    } catch (e: Exception) { null }
                    val targetDate = try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(scrollToDate)
                    } catch (e: Exception) { null }
                    if (mDate != null && targetDate != null) {
                        Math.abs(mDate.time - targetDate.time)
                    } else {
                        Long.MAX_VALUE
                    }
                }
                if (closestMarker != null) {
                    map.setCenter(org.mapsforge.core.model.LatLong(closestMarker.lat, closestMarker.lng))
                    map.setZoomLevel(14.toByte())
                    Toast.makeText(context, "Centered map on closest entry (${closestMarker.entry.dateString})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No location-tagged entries found.", Toast.LENGTH_SHORT).show()
                }
            }
            onScrollToDateHandled()
        }
    }

    if (!isDownloaded.value) {
        // Full screen download map box
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF070709))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Map",
                        tint = WaterBlue,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Southern India Map Download Required",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "To view your journal locations offline, please download the Southern India vector map file (511 MB). This file will be stored locally on your device.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress ?: 0f },
                            color = WaterBlue,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        val pct = ((downloadProgress ?: 0f) * 100).toInt()
                        Text(
                            text = "Downloading offline map: $pct%",
                            color = WaterBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        if (downloadError != null) {
                            Text(
                                text = "Download Failed: $downloadError",
                                color = Color.Red,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = {
                                showDownloadConfirmDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Download Southern India (511 MB)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (showDownloadConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showDownloadConfirmDialog = false },
                                title = { Text("Download Offline Map Data", color = Color.White) },
                                text = {
                                    Text(
                                        "This will download 511 MB of Southern India vector map data to your device for offline location viewing. We recommend using a Wi-Fi connection. Continue?",
                                        color = Color.LightGray
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showDownloadConfirmDialog = false
                                            coroutineScope.launch {
                                                MapDownloadManager.downloadMap(context)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                                    ) {
                                        Text("Download (511 MB)")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showDownloadConfirmDialog = false },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                containerColor = Color(0xFF141416)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Render Map
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF070709))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        try {
                            org.mapsforge.map.android.graphics.AndroidGraphicFactory.createInstance(ctx.applicationContext as android.app.Application)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        org.mapsforge.map.android.view.MapView(ctx).apply {
                            isClickable = true
                            getMapScaleBar().setVisible(true)
                            getMapZoomControls().setZoomLevelMin(5.toByte())
                            getMapZoomControls().setZoomLevelMax(20.toByte())
                            getMapZoomControls().setShowMapZoomControls(false)

                            val tileCache = org.mapsforge.map.android.util.AndroidUtil.createTileCache(
                                ctx,
                                "mapcache",
                                model.displayModel.tileSize,
                                1f,
                                model.frameBufferModel.overdrawFactor
                            )

                            val mapDataStore = org.mapsforge.map.datastore.MultiMapDataStore(org.mapsforge.map.datastore.MultiMapDataStore.DataPolicy.RETURN_FIRST)
                            MapDownloadManager.zones.forEach { zone ->
                                if (MapDownloadManager.isMapFileDownloaded(ctx, zone.id)) {
                                    try {
                                        val f = MapDownloadManager.getMapFile(ctx, zone.id)
                                        val store = org.mapsforge.map.reader.MapFile(f)
                                        mapDataStore.addMapDataStore(store, false, false)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            val tileRendererLayer = org.mapsforge.map.layer.renderer.TileRendererLayer(
                                tileCache,
                                mapDataStore,
                                model.mapViewPosition,
                                org.mapsforge.map.android.graphics.AndroidGraphicFactory.INSTANCE
                            )
                            tileRendererLayer.setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)

                            layerManager.layers.add(tileRendererLayer)

                            // Add initial markers with clustering
                            updateMforgeMarkers(this, ctx, markers, currentMyLocation.value) { entries, name ->
                                selectedLocationEntries = entries
                                selectedLocationName = name
                                showLocationEntriesDialog = true
                            }

                            // Listen for zoom level changes to re-cluster
                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            val positionObserver = object : org.mapsforge.map.model.common.Observer {
                                private var lastZoom = -1
                                override fun onChange() {
                                    val currentZoom = model.mapViewPosition.zoomLevel.toInt()
                                    if (currentZoom != lastZoom) {
                                        lastZoom = currentZoom
                                        handler.post {
                                            updateMforgeMarkers(this@apply, ctx, markers, currentMyLocation.value) { entries, name ->
                                                selectedLocationEntries = entries
                                                selectedLocationName = name
                                                showLocationEntriesDialog = true
                                            }
                                        }
                                    }
                                }
                            }
                            model.mapViewPosition.addObserver(positionObserver)

                            // Auto-remove observer on detach
                            addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: android.view.View) {}
                                override fun onViewDetachedFromWindow(v: android.view.View) {
                                    try {
                                        model.mapViewPosition.removeObserver(positionObserver)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            })

                            if (markers.isNotEmpty()) {
                                val first = markers.first()
                                setCenter(org.mapsforge.core.model.LatLong(first.lat, first.lng))
                                setZoomLevel(8.toByte())
                            } else {
                                // Default center on Bangalore (Southern India)
                                setCenter(org.mapsforge.core.model.LatLong(12.9716, 77.5946))
                                setZoomLevel(7.toByte())
                            }
                            mforgeViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                DisposableEffect(Unit) {
                    onDispose {
                        try {
                            mforgeViewRef?.destroyAll()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // My Location Button Overlay on Map
                FloatingActionButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            Toast.makeText(context, "Pinpointing GPS...", Toast.LENGTH_SHORT).show()
                            triggerFetchLocation(context) { lat, lng, city ->
                                val userLoc = org.mapsforge.core.model.LatLong(lat, lng)
                                userLocationLatLong = userLoc
                                mforgeViewRef?.setCenter(userLoc)
                                mforgeViewRef?.setZoomLevel(14.toByte())
                                Toast.makeText(context, "Centered at $city", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    containerColor = WaterBlue,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag("map_my_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location"
                    )
                }
            }

            // Horizontal List of Documented Locations for Quick Centering/Panning
            if (markers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "DOCUMENTED PLACES (${markers.size})",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    items(markers) { marker ->
                        Card(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable {
                                    mforgeViewRef?.setCenter(org.mapsforge.core.model.LatLong(marker.lat, marker.lng))
                                    mforgeViewRef?.setZoomLevel(14.toByte())
                                }
                                .testTag("map_marker_card_${marker.entry.id}"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = WaterBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = marker.cityName,
                                        color = WaterBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = marker.title,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = marker.entry.dateString,
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No journal entries have locations tagged yet.\nWrite a journal entry and click the GPS pin icon to add a location!",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showLocationEntriesDialog && selectedLocationEntries.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showLocationEntriesDialog = false },
            title = {
                Column {
                    Text(
                        text = "Journal Entries",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (selectedLocationName.isNotEmpty()) {
                        Text(
                            text = selectedLocationName,
                            color = WaterBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(selectedLocationEntries) { entry ->
                        Card(
                            onClick = {
                                showLocationEntriesDialog = false
                                onEntryClick(entry)
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = entry.title.ifEmpty { "Untitled" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = entry.dateString,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.text,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationEntriesDialog = false }) {
                    Text("CLOSE", color = WaterBlue)
                }
            },
            containerColor = Color(0xFF141416)
        )
    }
}

private fun performGeocoding(
    context: Context,
    query: String,
    onResult: (Double, Double, String) -> Unit
) {
    if (query.trim().isEmpty()) return
    try {
        if (android.location.Geocoder.isPresent()) {
            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                onResult(addr.latitude, addr.longitude, addr.locality ?: addr.featureName ?: query)
            } else {
                Toast.makeText(context, "Location \"$query\" not found", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Geocoder is not present, check if it's coordinates "lat,lng" format
            val parts = query.split(",")
            val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
            val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (lat != null && lng != null) {
                onResult(lat, lng, query)
            } else {
                Toast.makeText(context, "Geocoder unavailable on this emulator", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        // Fallback to lat,lng parsing if search is formatted as coordinate
        val parts = query.split(",")
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
        val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (lat != null && lng != null) {
            onResult(lat, lng, query)
        } else {
            Toast.makeText(context, "Search error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

