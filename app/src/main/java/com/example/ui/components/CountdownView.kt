package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.Charcoal
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import com.example.data.Deadline
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject

data class CountdownReminder(
    val daysBefore: Int,
    val timeString: String // "HH:mm"
)

data class CountdownItem(
    val id: String,
    val name: String,
    val targetTimestamp: Long,
    val category: String, // Birthdays, Anniversaries, Others
    val contactId: Int? = null,
    val isDbBacked: Boolean = false,
    val dbId: Int = 0,
    val originalDateStr: String = "" // DD/MM/YYYY representation
)

fun parseDateStringToCalendar(dateStr: String): Calendar? {
    if (dateStr.isBlank()) return null
    val cleaned = dateStr.trim().replace("-", "/") // normalize dividers
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()

    try {
        if (cleaned.contains("/")) {
            val parts = cleaned.split("/")
            if (parts.size >= 2) {
                if (parts[0].length == 4) { // YYYY/MM/DD
                    val year = parts[0].toIntOrNull() ?: today.get(Calendar.YEAR)
                    val month = parts[1].toIntOrNull() ?: 1
                    val day = parts[2].toIntOrNull() ?: 1
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month - 1)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                } else { // DD/MM/YYYY or DD/MM
                    val day = parts[0].toIntOrNull() ?: 1
                    val month = parts[1].toIntOrNull() ?: 1
                    val year = if (parts.size >= 3) parts[2].toIntOrNull() else null
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    cal.set(Calendar.MONTH, month - 1)
                    if (year != null && parts.size >= 3 && parts[2].trim().length >= 4) {
                        cal.set(Calendar.YEAR, year)
                    } else {
                        cal.set(Calendar.YEAR, today.get(Calendar.YEAR))
                    }
                }
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return null
}

fun hasYearMentioned(dateStr: String): Boolean {
    if (dateStr.isBlank()) return false
    val cleaned = dateStr.trim().replace("-", "/")
    if (cleaned.contains("/")) {
        val parts = cleaned.split("/")
        return parts.size >= 3 && parts[2].trim().length >= 4 && parts[2].toIntOrNull() != null
    }
    return false
}

fun formatAutoDate(input: String, previous: String): String {
    if (input.length < previous.length) {
        return input
    }
    val clean = input.take(10)
    return buildString {
        for (i in clean.indices) {
            val char = clean[i]
            if (i == 2) {
                if (char != '/') append('/')
            } else if (i == 5) {
                if (char != '/') append('/')
            }
            append(char)
        }
        if (this.length == 2 && !this.endsWith("/")) {
            append('/')
        } else if (this.length == 5 && !this.endsWith("/")) {
            append('/')
        }
    }.take(10)
}

@Composable
fun CountdownView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val contacts by viewModel.contacts.collectAsState()
    val deadlines by viewModel.deadlines.collectAsState()

    var activeCategoryFilter by remember { mutableStateOf("All") }
    val categories = listOf("All", "Birthdays", "Anniversaries", "Others")

    var showAddDialog by remember { mutableStateOf(false) }
    var eventName by remember { mutableStateOf("") }
    var eventDateText by remember { mutableStateOf("") } // Input is dd/mm/yyyy

    // Selected item for pop-out details view (for "others"/manual)
    var selectedItemForDetail by remember { mutableStateOf<CountdownItem?>(null) }
    var detailEditMode by remember { mutableStateOf(false) }
    var detailNameEdit by remember { mutableStateOf("") }
    var detailDateEdit by remember { mutableStateOf("") }

    // Dynamic Birthdays derived directly from Contacts DOB formatted as DD/MM/YYYY
    val derivedBirthdayCountdowns = remember(contacts) {
        contacts.filter { it.dobString.isNotEmpty() }.mapNotNull { contact ->
            val dateStr = contact.dobString.trim()
            val parsedCal = parseDateStringToCalendar(dateStr) ?: return@mapNotNull null
            
            val dobMonth = parsedCal.get(Calendar.MONTH)
            val dobDay = parsedCal.get(Calendar.DAY_OF_MONTH)
            val hasYear = hasYearMentioned(dateStr)
            val birthYear = if (hasYear) parsedCal.get(Calendar.YEAR) else null

            val today = Calendar.getInstance()
            val birthdayCal = Calendar.getInstance().apply {
                set(Calendar.MONTH, dobMonth)
                set(Calendar.DAY_OF_MONTH, dobDay)
                set(Calendar.YEAR, today.get(Calendar.YEAR))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Yearly Cycle checking: if birthday already happened this year (ignoring today), move to next year
            if (birthdayCal.timeInMillis < today.timeInMillis - 24 * 3600 * 1000L) {
                birthdayCal.set(Calendar.YEAR, today.get(Calendar.YEAR) + 1)
            }

            val upcomingCycleYear = birthdayCal.get(Calendar.YEAR)
            val ageStr = if (birthYear != null && birthYear > 0) " (${upcomingCycleYear - birthYear}th Birthday)" else ""

            CountdownItem(
                id = "contact_bday_${contact.id}",
                name = "${contact.firstName} ${contact.lastName}'s Birthday$ageStr",
                targetTimestamp = birthdayCal.timeInMillis,
                category = "Birthdays",
                contactId = contact.id,
                originalDateStr = dateStr
            )
        }
    }

    // Dynamic Anniversaries derived from Contacts Anniversary formatted as DD/MM/YYYY
    val derivedAnniversaryCountdowns = remember(contacts) {
        contacts.filter { it.anniversaryString.isNotEmpty() }.mapNotNull { contact ->
            val dateStr = contact.anniversaryString.trim()
            val parsedCal = parseDateStringToCalendar(dateStr) ?: return@mapNotNull null
            
            val month = parsedCal.get(Calendar.MONTH)
            val day = parsedCal.get(Calendar.DAY_OF_MONTH)
            val hasYear = hasYearMentioned(dateStr)
            val annivYear = if (hasYear) parsedCal.get(Calendar.YEAR) else null

            val today = Calendar.getInstance()
            val anniversaryCal = Calendar.getInstance().apply {
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.YEAR, today.get(Calendar.YEAR))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Yearly Cycle checking
            if (anniversaryCal.timeInMillis < today.timeInMillis - 24 * 3600 * 1000L) {
                anniversaryCal.set(Calendar.YEAR, today.get(Calendar.YEAR) + 1)
            }

            val upcomingCycleYear = anniversaryCal.get(Calendar.YEAR)
            val ageStr = if (annivYear != null && annivYear > 0) " (${upcomingCycleYear - annivYear}th Anniversary)" else ""

            CountdownItem(
                id = "contact_anniv_${contact.id}",
                name = "${contact.firstName} ${contact.lastName}'s Anniversary$ageStr",
                targetTimestamp = anniversaryCal.timeInMillis,
                category = "Anniversaries",
                contactId = contact.id,
                originalDateStr = dateStr
            )
        }
    }

    // Database Persistent Deadlines mapped as "Others" Countdowns
    val derivedDeadlineCountdowns = remember(deadlines) {
        deadlines.filter { !it.isCompleted }.map { d ->
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateStr = sdf.format(Date(d.targetTimestamp))
            CountdownItem(
                id = "db_deadline_${d.id}",
                name = d.name,
                targetTimestamp = d.targetTimestamp,
                category = "Others",
                isDbBacked = true,
                dbId = d.id,
                originalDateStr = dateStr
            )
        }
    }

    // Combine all countdowns
    val allCountdowns = remember(derivedBirthdayCountdowns, derivedAnniversaryCountdowns, derivedDeadlineCountdowns) {
        derivedBirthdayCountdowns + derivedAnniversaryCountdowns + derivedDeadlineCountdowns
    }

    // Filtered countdowns based on category chip
    val filteredCountdowns = allCountdowns.filter { item ->
        activeCategoryFilter == "All" || item.category.equals(activeCategoryFilter, ignoreCase = true)
    }.sortedBy { it.targetTimestamp }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Compact single-line row containing horizontally scrollable filters and the add action button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { cat ->
                    val isSelected = activeCategoryFilter == cat
                    val bg = if (isSelected) WaterBlue else Charcoal
                    val txtColor = if (isSelected) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable { activeCategoryFilter = cat }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(cat, fontSize = 11.sp, color = txtColor, fontWeight = FontWeight.Bold)
                    }
                }
            }



            IconButton(
                onClick = {
                    eventName = ""
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    eventDateText = sdf.format(Date(System.currentTimeMillis() + 10 * 24 * 3600 * 1000L))
                    showAddDialog = true
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(WaterBlue)
                    .size(36.dp)
                    .testTag("add_countdown_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Countdown",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Countdown Grid Layout
        if (filteredCountdowns.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No upcoming countdowns in this category.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 250.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCountdowns) { item ->
                    val diffMs = item.targetTimestamp - System.currentTimeMillis()
                    val daysRemaining = maxOf(0, ((diffMs + 12 * 3600 * 1000L) / (24 * 3600 * 1000L)).toInt()) // robust round up of fractional day boundary

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.category == "Birthdays" || item.category == "Anniversaries") {
                                    item.contactId?.let { contactId ->
                                        viewModel.selectContact(contactId)
                                    }
                                } else {
                                    // Pop out details view
                                    selectedItemForDetail = item
                                    detailEditMode = false
                                    detailNameEdit = item.name
                                    detailDateEdit = item.originalDateStr
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Category Tag badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(WaterBlue.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(item.category.uppercase(), color = WaterBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                if (item.category == "Birthdays") {
                                    Icon(Icons.Default.Cake, contentDescription = "Synced Birthday", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                } else if (item.category == "Anniversaries") {
                                    Icon(Icons.Default.Favorite, contentDescription = "Synced Anniversary", tint = WaterBlue, modifier = Modifier.size(16.dp))
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (item.isDbBacked) {
                                                viewModel.deleteDeadline(Deadline(id = item.dbId, name = item.name, targetTimestamp = item.targetTimestamp))
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (item.name.contains(" (")) {
                                val index = item.name.indexOf(" (")
                                val mainPart = item.name.substring(0, index)
                                val agePart = item.name.substring(index).trim()
                                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                    Text(
                                        text = mainPart,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = agePart,
                                        fontWeight = FontWeight.Medium,
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            // Countdown digits (ONLY SHOWS DAYS!)
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$daysRemaining",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = WaterBlue
                                )
                                Text("days left", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // "Automatically deploy the bars" - Progress indication bar
                            val progressValue = remember(daysRemaining) {
                                if (item.category == "Birthdays" || item.category == "Anniversaries") {
                                    val percent = (365f - daysRemaining) / 365f
                                    maxOf(0.05f, minOf(1.0f, percent))
                                } else {
                                    val totalSampleDays = 30f
                                    val percent = (totalSampleDays - daysRemaining) / totalSampleDays
                                    maxOf(0.1f, minOf(1.0f, percent))
                                }
                            }

                            LinearProgressIndicator(
                                progress = progressValue,
                                color = WaterBlue,
                                trackColor = Color.LightGray.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Modal dialogue popup to insert a brand new milestone
    if (showAddDialog) {
        val handleDismissAttempt = {
            if (eventName.isNotEmpty()) {
                showUnsavedDialog = true
            } else {
                showAddDialog = false
            }
        }

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved Changes", color = Color.White) },
                text = { Text("You have unsaved changes. Do you want to save or discard them?", color = Color.LightGray) },
                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        if (eventName.isNotEmpty()) {
                            val parsedCal = parseDateStringToCalendar(eventDateText)
                            val targetTime = parsedCal?.timeInMillis ?: (System.currentTimeMillis() + 10 * 24 * 3600 * 1000L)
                            viewModel.createDeadline(eventName, (maxOf(0L, targetTime - System.currentTimeMillis()) / (24 * 3600 * 1000L)))
                        }
                        showAddDialog = false
                    }) {
                        Text("Save", color = WaterBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        showAddDialog = false
                    }) {
                        Text("Discard", color = Color(0xFFF9325D))
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { handleDismissAttempt() },
            title = { Text("Add Milestone Countdown", fontWeight = FontWeight.Bold, color = Color.White) },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = eventName,
                        onValueChange = { eventName = it },
                        label = { Text("Milestone Title") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("countdown_title_input")
                    )

                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val calendar = Calendar.getInstance()
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        eventDateText = String.format(java.util.Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        TextField(
                            value = eventDateText,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Target Date (DD/MM/YYYY)") },
                            placeholder = { Text("Click to select date...") },
                            colors = TextFieldDefaults.colors(
                                disabledTextColor = Color.White,
                                disabledLabelColor = Color.LightGray,
                                disabledContainerColor = SurfaceCard,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("countdown_date_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventName.isNotEmpty()) {
                            val parsedCal = parseDateStringToCalendar(eventDateText)
                            val targetTime = parsedCal?.timeInMillis ?: (System.currentTimeMillis() + 10 * 24 * 3600 * 1000L)
                            viewModel.createDeadline(eventName, (maxOf(0L, targetTime - System.currentTimeMillis()) / (24 * 3600 * 1000L)))
                        }
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // Pop-out Details view for individual "Others" category items, allowing viewing & editing & deleting
    selectedItemForDetail?.let { item ->
        AlertDialog(
            onDismissRequest = { 
                selectedItemForDetail = null
                detailEditMode = false
            },
            title = {
                Text(
                    text = if (detailEditMode) "Edit Milestone" else "Milestone Details",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (detailEditMode) {
                        TextField(
                            value = detailNameEdit,
                            onValueChange = { detailNameEdit = it },
                            label = { Text("Milestone Title") },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = SurfaceCard,
                                unfocusedContainerColor = SurfaceCard
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val context = LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calendar = Calendar.getInstance()
                                    val parsed = parseDateStringToCalendar(detailDateEdit)
                                    if (parsed != null) {
                                        calendar.timeInMillis = parsed.timeInMillis
                                    }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            detailDateEdit = String.format(java.util.Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                        ) {
                            TextField(
                                value = detailDateEdit,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("Target Date (DD/MM/YYYY)") },
                                placeholder = { Text("Click to select date...") },
                                colors = TextFieldDefaults.colors(
                                    disabledTextColor = Color.White,
                                    disabledLabelColor = Color.LightGray,
                                    disabledContainerColor = SurfaceCard,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Column {
                            Text("Title:", color = Color.Gray, fontSize = 12.sp)
                            if (item.name.contains(" (")) {
                                val index = item.name.indexOf(" (")
                                val mainPart = item.name.substring(0, index)
                                val agePart = item.name.substring(index).trim()
                                Column {
                                    Text(mainPart, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(agePart, color = WaterBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            } else {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        Column {
                            Text("Target Date:", color = Color.Gray, fontSize = 12.sp)
                            Text(item.originalDateStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        val diffMs = item.targetTimestamp - System.currentTimeMillis()
                        val daysRemaining = maxOf(0, ((diffMs + 12 * 3600 * 1000L) / (24 * 3600 * 1000L)).toInt())
                        
                        Column {
                            Text("Time Remaining:", color = Color.Gray, fontSize = 12.sp)
                            Text("$daysRemaining Days Left", color = WaterBlue, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }

                        // Display visual bars inside pop-out detailed view too!
                        val progressPercent = maxOf(0.1f, minOf(1.0f, (30f - daysRemaining) / 30f))
                        Column {
                            Text("Visual Timeline Tracker:", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                            LinearProgressIndicator(
                                progress = progressPercent,
                                color = WaterBlue,
                                trackColor = Color.LightGray.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (detailEditMode) {
                        Button(
                            onClick = {
                                if (detailNameEdit.isNotEmpty()) {
                                    val parsedCal = parseDateStringToCalendar(detailDateEdit)
                                    val targetTime = parsedCal?.timeInMillis ?: item.targetTimestamp
                                    if (item.isDbBacked) {
                                        viewModel.updateDeadline(
                                            Deadline(
                                                id = item.dbId,
                                                name = detailNameEdit,
                                                targetTimestamp = targetTime,
                                                isCompleted = false
                                            )
                                        )
                                    }
                                }
                                selectedItemForDetail = null
                                detailEditMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }

                        TextButton(onClick = { detailEditMode = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { detailEditMode = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WaterBlue, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }

                        Button(
                            onClick = {
                                if (item.isDbBacked) {
                                    viewModel.deleteDeadline(
                                        Deadline(
                                            id = item.dbId,
                                            name = item.name,
                                            targetTimestamp = item.targetTimestamp
                                        )
                                    )
                                }
                                selectedItemForDetail = null
                                detailEditMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        )
    }


}
