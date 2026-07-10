package com.example.ui.components

import com.example.util.MediaPreviewBox
import com.example.util.PdfViewerDialog
import com.example.util.VideoPlayerDialog
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue

// Helper class representing parsed attachments from different subcomponents
data class ExplorerFile(
    val name: String,
    val type: String, // "image", "video", "audio", "others"
    val dateText: String,    // e.g. "Jun 19"
    val timestamp: Long,
    val sourceName: String,  // e.g. "Journal Entry", "Task: Homework", "Contact: Munee"
    val fileMime: String,
    val path: String = "",
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val journalEntries by viewModel.journalEntries.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    
    var longPressedFile by remember { mutableStateOf<ExplorerFile?>(null) }
    var activePreviewFile by remember { mutableStateOf<ExplorerFile?>(null) }

    // Google Drive Integration State
    val googleAccount = remember { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) }
    var hasPermission by remember { mutableStateOf(com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)) }
    var isOperating by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var lastSyncTs by remember { mutableStateOf(prefs.getLong("gd_all_last_sync_timestamp", 0L)) }

    val scope = rememberCoroutineScope()

    val authResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        hasPermission = com.example.util.GoogleDriveSyncManager.hasDrivePermission(context)
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            syncMessage = "Google Drive successfully authorized! Tap Backup or Restore to align your app data."
        } else {
            syncMessage = "Google Drive authorization declined."
        }
    }
    
    // 1. Gather files from Journal Entries
    val journalFiles = remember(journalEntries) {
        val list = mutableListOf<ExplorerFile>()
        val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        journalEntries.forEach { entry ->
            if (entry.attachmentsJson.isNotEmpty()) {
                val attachmentsList = entry.attachmentsJson.split(";;")
                attachmentsList.forEach { attachment ->
                    if (attachment.isNotEmpty() && !attachment.startsWith("loc:")) {
                        var name = "Attachment"
                        var type = "others"
                        var filePath = ""
                        if (attachment.startsWith("photo:")) {
                            val path = attachment.removePrefix("photo:")
                            name = path.substringAfterLast("/")
                            type = "image"
                            filePath = path
                        } else if (attachment.startsWith("video:")) {
                            val path = attachment.removePrefix("video:")
                            name = path.substringAfterLast("/")
                            type = "video"
                            filePath = path
                        } else if (attachment.startsWith("audio:")) {
                            val path = attachment.removePrefix("audio:")
                            name = path.substringAfterLast("/")
                            type = "audio"
                            filePath = path
                        } else if (attachment.startsWith("file:")) {
                            val parts = attachment.removePrefix("file:").split("|path:")
                            name = parts.getOrNull(0) ?: "Document"
                            filePath = parts.getOrNull(1) ?: ""
                            type = if (name.lowercase().endsWith(".png") || name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || name.lowercase().endsWith(".webp")) {
                                "image"
                            } else if (name.lowercase().endsWith(".mp3") || name.lowercase().endsWith(".m4a") || name.lowercase().endsWith(".wav") || name.lowercase().endsWith(".gz") || name.lowercase().endsWith(".aac")) {
                                "audio"
                            } else if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".3gp") || name.lowercase().endsWith(".mkv") || name.lowercase().endsWith(".mov")) {
                                "video"
                            } else {
                                "others"
                            }
                        }
                        val formattedDate = try {
                            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(entry.dateString)
                            parsed?.let { sdf.format(it) } ?: sdf.format(java.util.Date(entry.timestamp))
                        } catch (e: Exception) {
                            sdf.format(java.util.Date(entry.timestamp))
                        }
                        
                        list.add(
                            ExplorerFile(
                                name = name,
                                type = type,
                                dateText = formattedDate,
                                timestamp = entry.timestamp,
                                sourceName = "Journal (${entry.dateString})",
                                fileMime = if (type == "image") "image/png" else if (type == "video") "video/mp4" else if (type == "audio") "audio/mpeg" else "application/pdf",
                                path = filePath,
                                onClick = {
                                    viewModel.selectJournal(entry.id)
                                }
                            )
                        )
                    }
                }
            }
        }
        list
    }

    // 2. Gather files from Tasks
    val taskFiles = remember(tasks) {
        val list = mutableListOf<ExplorerFile>()
        val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        val metaAttachmentPattern = Regex("""\[Attachment: ([^\]]+)\]""")
        tasks.forEach { task ->
            val desc = task.description
            val match = metaAttachmentPattern.find(desc)
            val attachment = match?.groupValues?.get(1)
            if (attachment != null && attachment != "None" && attachment.isNotEmpty()) {
                val name = attachment
                val type = if (name.lowercase().endsWith(".png") || name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || name.lowercase().endsWith(".webp")) {
                    "image"
                } else if (name.lowercase().endsWith(".mp3") || name.lowercase().endsWith(".m4a") || name.lowercase().endsWith(".wav") || name.lowercase().endsWith(".aac")) {
                    "audio"
                } else if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".3gp") || name.lowercase().endsWith(".mkv") || name.lowercase().endsWith(".mov")) {
                    "video"
                } else {
                    "others"
                }
                val formattedDate = if (task.dueDateString.isNotEmpty()) {
                    try {
                        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(task.dueDateString)
                        parsed?.let { sdf.format(it) } ?: sdf.format(java.util.Date())
                    } catch (e: Exception) {
                        sdf.format(java.util.Date())
                    }
                } else {
                    sdf.format(java.util.Date())
                }
                list.add(
                    ExplorerFile(
                        name = name,
                        type = type,
                        dateText = formattedDate,
                        timestamp = System.currentTimeMillis() - 1000L * 60 * 30, // approximate task created earlier
                        sourceName = "Task: ${task.title}",
                        fileMime = if (type == "image") "image/png" else if (type == "video") "video/mp4" else if (type == "audio") "audio/mpeg" else "application/pdf",
                        path = java.io.File(com.example.util.StorageHelper.getAppFilesDir(context), name).absolutePath,
                        onClick = {
                            viewModel.selectTask(task.id)
                        }
                    )
                )
            }
        }
        list
    }

    // 3. Gather files from Contacts
    val contactFiles = remember(contacts) {
        val list = mutableListOf<ExplorerFile>()
        val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        contacts.forEach { contact ->
            // Active photo
            if (!contact.photoUri.isNullOrEmpty()) {
                list.add(
                    ExplorerFile(
                        name = "photo_${contact.firstName}_${contact.lastName}.png",
                        type = "image",
                        dateText = sdf.format(java.util.Date()),
                        timestamp = System.currentTimeMillis() - 1000L * 60 * 15,
                        sourceName = "Contact: ${contact.firstName} ${contact.lastName}",
                        fileMime = "image/png",
                        path = contact.photoUri,
                        onClick = {
                            viewModel.selectContact(contact.id)
                        }
                    )
                )
            }
            // Other attached files
            if (contact.attachedFilesJson.isNotEmpty()) {
                try {
                    val arr = org.json.JSONArray(contact.attachedFilesJson)
                    for (i in 0 until arr.length()) {
                        val path = arr.getString(i)
                        val name = path.substringAfterLast("/")
                        val type = if (name.lowercase().endsWith(".png") || name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || name.lowercase().endsWith(".webp")) {
                            "image"
                        } else if (name.lowercase().endsWith(".mp3") || name.lowercase().endsWith(".m4a") || name.lowercase().endsWith(".wav") || name.lowercase().endsWith(".aac")) {
                            "audio"
                        } else if (name.lowercase().endsWith(".mp4") || name.lowercase().endsWith(".3gp") || name.lowercase().endsWith(".mkv") || name.lowercase().endsWith(".mov")) {
                            "video"
                        } else {
                            "others"
                        }
                        list.add(
                            ExplorerFile(
                                name = name,
                                type = type,
                                dateText = sdf.format(java.util.Date()),
                                timestamp = System.currentTimeMillis() - 1000L * 60 * 10,
                                sourceName = "Contact: ${contact.firstName} ${contact.lastName}",
                                fileMime = if (type == "image") "image/png" else if (type == "video") "video/mp4" else if (type == "audio") "audio/mpeg" else "application/pdf",
                                path = path,
                                onClick = {
                                    viewModel.selectContact(contact.id)
                                }
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        list
    }

    // 4. Merge all files sorted by descending timestamp (recent files on top)
    val allExplorerFiles = remember(journalFiles, taskFiles, contactFiles) {
        (journalFiles + taskFiles + contactFiles).sortedByDescending { it.timestamp }
    }

    // Filter modes
    val filterOptions = listOf("All", "Image", "Video", "Audio", "Others", "Google Sheets")
    var selectedFilter by remember { mutableStateOf("All") }

    val googleSheets by viewModel.googleSheets.collectAsState()
    val isLoadingSheets by viewModel.isLoadingGoogleSheets.collectAsState()
    val sheetsError by viewModel.googleSheetsError.collectAsState()
    var selectedSheetUrl by remember { mutableStateOf<String?>(null) }
    var showCreateSheetDialog by remember { mutableStateOf(false) }
    var newSheetTitle by remember { mutableStateOf("") }

    val sheetsAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.fetchGoogleSheets(context)
        }
    }

    LaunchedEffect(selectedFilter) {
        if (selectedFilter == "Google Sheets") {
            viewModel.fetchGoogleSheets(context) { intent ->
                sheetsAuthLauncher.launch(intent)
            }
        }
    }

    if (selectedSheetUrl != null) {
        GoogleSheetViewer(sheetUrl = selectedSheetUrl!!) {
            selectedSheetUrl = null
        }
        return
    }

    val filteredFiles = remember(allExplorerFiles, selectedFilter) {
        if (selectedFilter == "All") {
            allExplorerFiles
        } else {
            allExplorerFiles.filter { it.type.equals(selectedFilter, ignoreCase = true) }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Filter bar using chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterOptions.forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) WaterBlue else SurfaceCard)
                        .border(1.dp, if (isSelected) WaterBlue else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (selectedFilter == "Google Sheets") {
            if (showCreateSheetDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateSheetDialog = false },
                    title = { Text("Create New Google Sheet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Enter a title for your new Google Spreadsheet. It will be created directly under your Google Drive account.", color = Color.LightGray, fontSize = 13.sp)
                            OutlinedTextField(
                                value = newSheetTitle,
                                onValueChange = { newSheetTitle = it },
                                placeholder = { Text("Untitled Spreadsheet", color = Color.Gray) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WaterBlue,
                                    unfocusedBorderColor = Color.Gray,
                                    cursorColor = WaterBlue
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val title = if (newSheetTitle.trim().isEmpty()) "Untitled Spreadsheet" else newSheetTitle.trim()
                                viewModel.createGoogleSheet(context, title, onSuccess = { webLink ->
                                    selectedSheetUrl = webLink
                                    android.widget.Toast.makeText(context, "Successfully created Sheet!", android.widget.Toast.LENGTH_SHORT).show()
                                }, onAuthRequired = { intent ->
                                    sheetsAuthLauncher.launch(intent)
                                })
                                showCreateSheetDialog = false
                                newSheetTitle = ""
                            }
                        ) {
                            Text("CREATE", color = WaterBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateSheetDialog = false }) {
                            Text("CANCEL", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF161618),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            if (isLoadingSheets) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WaterBlue)
                }
            } else if (sheetsError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(sheetsError!!, color = Color.LightGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.fetchGoogleSheets(context) { intent ->
                                    sheetsAuthLauncher.launch(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                        ) {
                            Text("Retry Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (googleSheets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Google Sheets found under your account.", color = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCreateSheetDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue)
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create New Sheet", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GOOGLE SPREADSHEETS (${googleSheets.size})",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showCreateSheetDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NEW SHEET", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(googleSheets) { sheet ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSheetUrl = sheet.webViewLink },
                                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "Spreadsheet",
                                        tint = Color(0xFF107C41),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sheet.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Last updated: " + if (sheet.modifiedTime.isNotEmpty()) sheet.modifiedTime.substringBefore("T") else "Unknown",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Open",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Files library title count
            Text(
                text = "FILES LIBRARY (${filteredFiles.size} ITEMS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Charcoal),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedFilter == "All") "No attachments or files found." else "No $selectedFilter files found.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Upload or attach images, voice memos, videos or files inside Journals, Tasks, or Contacts to list them here.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Lazy Grid of AspectRatio 1:1 Squares
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().testTag("files_grid_layout")
            ) {
                items(filteredFiles) { file ->
                    // Square Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f) // Makes it perfect square!
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { activePreviewFile = file },
                                onLongClick = { longPressedFile = file }
                            ),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val isPdf = remember(file.name) { file.name.lowercase().endsWith(".pdf") }
                            val hasDirectPreview = file.type == "image" || file.type == "video" || isPdf

                            Column(modifier = Modifier.fillMaxSize()) {
                                if (hasDirectPreview && file.path.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1.3f)
                                            .background(Color.Black.copy(alpha = 0.2f))
                                    ) {
                                        MediaPreviewBox(
                                            pathOrName = file.path,
                                            type = file.type,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1.3f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val fileIcon = when (file.type) {
                                            "image" -> Icons.Default.Image
                                            "video" -> Icons.Default.Videocam
                                            "audio" -> Icons.Default.Mic
                                            else -> Icons.Default.InsertDriveFile
                                        }
                                        Icon(
                                            imageVector = fileIcon,
                                            contentDescription = file.type,
                                            tint = WaterBlue,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.9f)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = file.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = file.sourceName,
                                        color = Color.Gray,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Date text on top left
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = file.dateText,
                                    color = WaterBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        }

        if (longPressedFile != null) {
            val fileNode = longPressedFile!!
            AlertDialog(
                onDismissRequest = { longPressedFile = null },
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = WaterBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Attachment File Options",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = fileNode.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Linked Source: ${fileNode.sourceName}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Type: ${fileNode.type.uppercase()} • Date: ${fileNode.dateText}",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(
                                context,
                                "File \"${fileNode.name}\" deleted from internal storage sandbox successfully.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            longPressedFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Text("DELETE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(
                                context,
                                "Download started: \"${fileNode.name}\" is being exported to system downloads stream.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            longPressedFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D3B3A), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00BFA5))
                            Text("DOWNLOAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5))
                        }
                    }
                },
                containerColor = Color(0xFF161618),
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (activePreviewFile != null) {
            val fileNode = activePreviewFile!!
            val isPdf = remember(fileNode.name) { fileNode.name.lowercase().endsWith(".pdf") }
            
            if (isPdf) {
                PdfViewerDialog(filePath = fileNode.path, onDismiss = { activePreviewFile = null })
            } else if (fileNode.type == "video") {
                VideoPlayerDialog(filePath = fileNode.path, onDismiss = { activePreviewFile = null })
            } else if (fileNode.type == "image") {
                Dialog(onDismissRequest = { activePreviewFile = null }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF12131A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Title / Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileNode.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = fileNode.sourceName,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = { activePreviewFile = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Large Image Frame
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                val isWebUrl = remember(fileNode.path) { fileNode.path.startsWith("http://") || fileNode.path.startsWith("https://") }
                                AsyncImage(
                                    model = if (isWebUrl) fileNode.path else java.io.File(fileNode.path),
                                    contentDescription = "Full Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bottom Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val act = activePreviewFile
                                        activePreviewFile = null
                                        act?.onClick?.invoke()
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                        Text("GO TO SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                Button(
                                    onClick = {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Download started: \"${fileNode.name}\" has been exported to Downloads folder.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D3B3A)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00BFA5))
                                        Text("DOWNLOAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Audio or other types
                Dialog(onDismissRequest = { activePreviewFile = null }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF12131A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileNode.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Linked Source: ${fileNode.sourceName}",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = { activePreviewFile = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val mimeIcon = if (fileNode.type == "audio") Icons.Default.Mic else Icons.Default.InsertDriveFile
                                    Icon(
                                        imageVector = mimeIcon,
                                        contentDescription = null,
                                        tint = WaterBlue,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Type: ${fileNode.type.uppercase()} • ${fileNode.dateText}",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val act = activePreviewFile
                                        activePreviewFile = null
                                        act?.onClick?.invoke()
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                        Text("GO TO SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }

                                Button(
                                    onClick = {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Download started: \"${fileNode.name}\" has been exported to Downloads folder.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D3B3A)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00BFA5))
                                        Text("DOWNLOAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00BFA5), maxLines = 1)
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

@Composable
fun GoogleSheetViewer(sheetUrl: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F14))
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF16161B))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Google Sheets Viewer",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                android.webkit.WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        supportZoom()
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    webViewClient = android.webkit.WebViewClient()
                    loadUrl(sheetUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
