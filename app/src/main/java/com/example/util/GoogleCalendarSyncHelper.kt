package com.example.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.data.Task
import java.text.SimpleDateFormat
import java.util.*

data class CalendarInfo(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val displayName: String
)

object GoogleCalendarSyncHelper {

    private const val TAG = "GoogleCalendarSync"

    // Helper to check and get a calendar ID (preferring Google account calendars or user's selected preferences)
    fun getOrCreateCalendarId(context: Context): Long? {
        val prefs = context.getSharedPreferences("app_calendar_prefs", Context.MODE_PRIVATE)
        val selectedAccount = prefs.getString("selected_calendar_account", null)
        val selectedName = prefs.getString("selected_calendar_name", null)
        val selectedId = prefs.getLong("selected_calendar_id", -1L)

        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            var matchedId: Long? = null
            var googleFallbackId: Long? = null
            var fallbackId: Long? = null

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val accountName = it.getString(1) ?: ""
                    val accountType = it.getString(2) ?: ""
                    val displayName = it.getString(3) ?: ""
                    
                    // Priority 1: Match saved calendar ID precisely
                    if (selectedId != -1L && id == selectedId) {
                        Log.d(TAG, "Found precise selected calendar ID match: $id")
                        return id
                    }
                    
                    // Priority 2: Match saved Account name & Display Name
                    if (selectedAccount != null && selectedName != null &&
                        accountName == selectedAccount && displayName == selectedName) {
                        matchedId = id
                    }
                    
                    // Priority 3: Fallbacks
                    if (accountType == "com.google" && googleFallbackId == null) {
                        googleFallbackId = id
                    }
                    if (fallbackId == null) {
                        fallbackId = id
                    }
                }
            }
            if (matchedId != null) {
                Log.d(TAG, "Found preference-matched calendar ID: $matchedId")
                return matchedId
            }
            if (googleFallbackId != null) {
                Log.d(TAG, "Found Google Account fallback calendar ID: $googleFallbackId")
                return googleFallbackId
            }
            if (fallbackId != null) {
                Log.d(TAG, "Found general fallback calendar ID: $fallbackId")
                return fallbackId
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission missing for querying calendars: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendars: ${e.message}", e)
        }

        return null
    }

    // Helper to query all available calendars on the device
    fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        val list = mutableListOf<CalendarInfo>()
        val resolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val accountName = it.getString(1) ?: "Local"
                    val accountType = it.getString(2) ?: "Local"
                    val displayName = it.getString(3) ?: "My Calendar"
                    list.add(CalendarInfo(id, accountName, accountType, displayName))
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying calendars: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendars: ${e.message}", e)
        }
        return list
    }

    // Bidirectional sync
    suspend fun syncGoogleCalendar(
        context: Context,
        localTasks: List<Task>,
        onImportTask: suspend (String, String, Int, String) -> Long,
        onUpdateTask: suspend (Task) -> Unit
    ): String {
        val calendarId = getOrCreateCalendarId(context)
            ?: return "No calendar found on device. Please set up a Google account first."

        var importedCount = 0
        var exportedCount = 0

        val resolver = context.contentResolver
        val timeZone = TimeZone.getDefault().id

        // 1. IMPORT FROM GOOGLE CALENDAR
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Define query window: from 30 days ago to 60 days in the future
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val startMillis = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 90)
        val endMillis = calendar.timeInMillis

        val selection = "(${CalendarContract.Events.CALENDAR_ID} = ?) AND (${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (deleted != 1)"
        val selectionArgs = arrayOf(calendarId.toString(), startMillis.toString(), endMillis.toString())

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY
        )

        var eventCursor: Cursor? = null
        try {
            eventCursor = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            eventCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val title = cursor.getString(1) ?: "Google Event"
                    val description = cursor.getString(2) ?: ""
                    val dtStart = cursor.getLong(3)
                    val dtEnd = cursor.getLong(4)
                    val allDay = cursor.getInt(5) == 1

                    val eventDateStr = if (allDay) {
                        val utcFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        utcFormatter.timeZone = TimeZone.getTimeZone("UTC")
                        utcFormatter.format(Date(dtStart))
                    } else {
                        sdfDate.format(Date(dtStart))
                    }

                    // Check if this event has been deleted locally
                    val isDeletedLocally = DeletedTaskLogHelper.isGCalEventDeletedLocally(context, eventId.toString()) ||
                            DeletedTaskLogHelper.isTaskDeletedLocally(context, title, eventDateStr)
                    
                    if (isDeletedLocally) {
                        Log.d(TAG, "Sync: Event '$title' on $eventDateStr (ID: $eventId) was deleted locally. Deleting from Google Calendar.")
                        try {
                            resolver.delete(
                                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                null,
                                null
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed deleting GCal Event $eventId: ${e.message}", e)
                        }
                        continue
                    }

                    // Extract AppTaskId if exists in description to check if local task was deleted
                    val appTaskIdRegex = Regex("""\[AppTaskId:\s*(\d+)\]""")
                    val appTaskIdMatch = appTaskIdRegex.find(description)
                    val appTaskId = appTaskIdMatch?.groupValues?.get(1)?.toIntOrNull()

                    if (appTaskId != null) {
                        val localExists = localTasks.any { it.id == appTaskId }
                        if (!localExists) {
                            // Local task was deleted, so delete corresponding Google Calendar Event
                            try {
                                resolver.delete(
                                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                    null,
                                    null
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed deleting GCal Event $eventId for deleted local task $appTaskId: ${e.message}", e)
                            }
                            continue
                        }
                    }

                    // Check if we already have this synced locally
                    val matchedLocal = localTasks.find { task ->
                        (appTaskId != null && task.id == appTaskId) ||
                        task.description.contains("[GCalEventId: $eventId]")
                    }

                    val alreadySynced = matchedLocal != null || localTasks.any { task ->
                        task.title.trim().equals(title.trim(), ignoreCase = true) && task.dueDateString == eventDateStr
                    }

                    if (matchedLocal != null) {
                        // Timing, Title, or Reminder sync from Google Calendar to local task
                        val gCalTime = parseTaskTime(matchedLocal.description)
                        val gCalDuration = parseTaskDuration(matchedLocal.description)

                        val expectedTimeStr = if (allDay) {
                            "None"
                        } else {
                            val newHourFormatter = SimpleDateFormat("hh:mm a", Locale.US)
                            newHourFormatter.format(Date(dtStart))
                        }

                        val expectedDuration = if (allDay) {
                            1440
                        } else if (dtEnd > dtStart) {
                            ((dtEnd - dtStart) / 60000).toInt().coerceAtLeast(15)
                        } else {
                            30
                        }

                        val actualGCalTime = Calendar.getInstance().apply { timeInMillis = dtStart }
                        val actualHour = actualGCalTime.get(Calendar.HOUR_OF_DAY)
                        val actualMinute = actualGCalTime.get(Calendar.MINUTE)

                        val timeChanged = if (allDay) {
                            gCalTime != null || matchedLocal.description.contains("[Time: None]").not()
                        } else {
                            gCalTime == null || gCalTime.first != actualHour || gCalTime.second != actualMinute
                        }
                        val durationChanged = gCalDuration != expectedDuration
                        val dateChanged = matchedLocal.dueDateString != eventDateStr
                        val titleChanged = !matchedLocal.title.trim().equals(title.trim(), ignoreCase = true)

                        val remindersList = getEventReminders(context, eventId)
                        val currentRemindersList = getTaskRemindersInMinutes(matchedLocal.description)
                        val remindersChanged = remindersList.sorted() != currentRemindersList.sorted()

                        if (timeChanged || durationChanged || dateChanged || titleChanged || remindersChanged) {
                            var descriptionWithoutTags = matchedLocal.description
                            
                            // Remove existing tags if present
                            descriptionWithoutTags = descriptionWithoutTags.replace(Regex("""\[Time:\s*[^\]]+\]"""), "").trim()
                            descriptionWithoutTags = descriptionWithoutTags.replace(Regex("""\[Duration:\s*[^\]]+\]"""), "").trim()
                            descriptionWithoutTags = descriptionWithoutTags.replace(Regex("""\[Reminders:\s*[^\]]+\]"""), "").trim()
                            
                            descriptionWithoutTags = descriptionWithoutTags.trim()

                            // Rebuild description with new tags
                            val remindersTag = if (remindersList.isNotEmpty()) {
                                " [Reminders: ${remindersList.map { formatMinutesToReminderString(it) }.joinToString(", ")}]"
                            } else {
                                ""
                            }
                            
                            val updatedDesc = if (descriptionWithoutTags.isEmpty()) {
                                "[Time: $expectedTimeStr] [Duration: ${expectedDuration}m]$remindersTag"
                            } else {
                                "$descriptionWithoutTags\n[Time: $expectedTimeStr] [Duration: ${expectedDuration}m]$remindersTag"
                            }

                            val updatedTask = matchedLocal.copy(
                                title = title,
                                dueDateString = eventDateStr,
                                estimatedMinutes = expectedDuration,
                                description = updatedDesc
                            )
                            onUpdateTask(updatedTask)
                        }
                    } else if (!alreadySynced && !description.contains("[AppTaskId:")) {
                        // Estimate duration
                        val estMinutes = if (allDay) {
                            1440
                        } else if (dtEnd > dtStart) {
                            ((dtEnd - dtStart) / 60000).toInt().coerceAtLeast(15)
                        } else {
                            30
                        }

                        val timeStr = if (allDay) {
                            "None"
                        } else {
                            val hourFormatter = SimpleDateFormat("hh:mm a", Locale.US)
                            hourFormatter.format(Date(dtStart))
                        }
                        
                        val remindersList = getEventReminders(context, eventId)
                        val remindersTag = if (remindersList.isNotEmpty()) {
                            " [Reminders: ${remindersList.map { formatMinutesToReminderString(it) }.joinToString(", ")}]"
                        } else {
                            ""
                        }

                        val cleanDesc = if (description.isEmpty()) {
                            "[Time: $timeStr] [Duration: ${estMinutes}m]$remindersTag\n\n[GCalEventId: $eventId]"
                        } else {
                            "$description\n[Time: $timeStr] [Duration: ${estMinutes}m]$remindersTag\n\n[GCalEventId: $eventId]"
                        }

                        val newTaskId = onImportTask(title, cleanDesc, estMinutes, eventDateStr)
                        importedCount++

                        // Update Google Calendar event description with the newly created local AppTaskId
                        try {
                            val updatedDescription = if (description.isEmpty()) {
                                "[AppTaskId: $newTaskId]"
                            } else {
                                "$description\n\n[AppTaskId: $newTaskId]"
                            }
                            val updateValues = ContentValues().apply {
                                put(CalendarContract.Events.DESCRIPTION, updatedDescription)
                            }
                            resolver.update(
                                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                updateValues,
                                null,
                                null
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed updating Google Calendar event $eventId with AppTaskId: ${e.message}", e)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            return "Calendar permissions are required to sync Google Calendar."
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing from Google Calendar: ${e.message}", e)
            return "Sync failed: ${e.message}"
        }

        // 2. EXPORT AND UPDATE TO GOOGLE CALENDAR
        for (task in localTasks) {
            if (task.dueDateString.isNotEmpty()) {
                if (!task.description.contains("[GCalEventId:")) {
                    // Export new event
                    try {
                        val dateParts = task.dueDateString.split("-")
                        if (dateParts.size == 3) {
                            val year = dateParts[0].toIntOrNull() ?: continue
                            val month = (dateParts[1].toIntOrNull() ?: continue) - 1
                            val day = dateParts[2].toIntOrNull() ?: continue

                            // Try parsing [Time: hh:mm AM/PM] or standard time from task description
                            var startHour = 9
                            var startMinute = 0
                            val parsedTime = parseTaskTime(task.description)
                            if (parsedTime != null) {
                                startHour = parsedTime.first
                                startMinute = parsedTime.second
                            }

                            val isAllDay = parsedTime == null || task.description.contains("[Time: None]")

                            val startCal = if (isAllDay) {
                                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                            } else {
                                Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                    set(Calendar.HOUR_OF_DAY, startHour)
                                    set(Calendar.MINUTE, startMinute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                            }

                            val durationMin = parseTaskDuration(task.description).coerceAtLeast(15)
                            val endCal = Calendar.getInstance(if (isAllDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()).apply {
                                if (isAllDay) {
                                    timeInMillis = startCal.timeInMillis + (24 * 60 * 60 * 1000L)
                                } else {
                                    timeInMillis = startCal.timeInMillis + (durationMin * 60 * 1000L)
                                }
                            }

                            val reminderMins = getTaskRemindersInMinutes(task.description)
                            val values = ContentValues().apply {
                                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                                put(CalendarContract.Events.TITLE, task.title)
                                put(CalendarContract.Events.DESCRIPTION, "${task.description}\n\n[AppTaskId: ${task.id}]")
                                put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
                                put(CalendarContract.Events.DTEND, endCal.timeInMillis)
                                put(CalendarContract.Events.EVENT_TIMEZONE, if (isAllDay) "UTC" else timeZone)
                                put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
                                put(CalendarContract.Events.HAS_ALARM, if (reminderMins.isNotEmpty()) 1 else 0)
                            }

                            val uri: Uri? = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                            if (uri != null) {
                                val newEventId = ContentUris.parseId(uri)
                                setEventReminders(context, newEventId, reminderMins)

                                // Update our local task description to reflect GCal event id
                                val updatedDesc = if (task.description.isEmpty()) {
                                    "[GCalEventId: $newEventId]"
                                } else {
                                    "${task.description}\n\n[GCalEventId: $newEventId]"
                                }
                                onUpdateTask(task.copy(description = updatedDesc))
                                exportedCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed exporting task '${task.title}': ${e.message}", e)
                    }
                } else {
                    // Update existing event to sync changes (e.g., end time/duration/reminders changes)
                    try {
                        val idRegex = Regex("""\[GCalEventId:\s*(\d+)\]""")
                        val match = idRegex.find(task.description)
                        val eventId = match?.groupValues?.get(1)?.toLongOrNull()
                        if (eventId != null) {
                            val dateParts = task.dueDateString.split("-")
                            if (dateParts.size == 3) {
                                val year = dateParts[0].toIntOrNull() ?: continue
                                val month = (dateParts[1].toIntOrNull() ?: continue) - 1
                                val day = dateParts[2].toIntOrNull() ?: continue

                                var startHour = 9
                                var startMinute = 0
                                val parsedTime = parseTaskTime(task.description)
                                if (parsedTime != null) {
                                    startHour = parsedTime.first
                                    startMinute = parsedTime.second
                                }

                                val isAllDay = parsedTime == null || task.description.contains("[Time: None]")

                                val startCal = if (isAllDay) {
                                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                } else {
                                    Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                        set(Calendar.HOUR_OF_DAY, startHour)
                                        set(Calendar.MINUTE, startMinute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                }

                                val durationMin = parseTaskDuration(task.description).coerceAtLeast(15)
                                val endCal = Calendar.getInstance(if (isAllDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()).apply {
                                    if (isAllDay) {
                                        timeInMillis = startCal.timeInMillis + (24 * 60 * 60 * 1000L)
                                    } else {
                                        timeInMillis = startCal.timeInMillis + (durationMin * 60 * 1000L)
                                    }
                                }

                                val reminderMins = getTaskRemindersInMinutes(task.description)
                                val values = ContentValues().apply {
                                    put(CalendarContract.Events.TITLE, task.title)
                                    put(CalendarContract.Events.DESCRIPTION, "${task.description}\n\n[AppTaskId: ${task.id}]")
                                    put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
                                    put(CalendarContract.Events.DTEND, endCal.timeInMillis)
                                    put(CalendarContract.Events.EVENT_TIMEZONE, if (isAllDay) "UTC" else timeZone)
                                    put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
                                    put(CalendarContract.Events.HAS_ALARM, if (reminderMins.isNotEmpty()) 1 else 0)
                                }

                                resolver.update(
                                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                                    values,
                                    null,
                                    null
                                )
                                setEventReminders(context, eventId, reminderMins)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed updating task on GCal '${task.title}': ${e.message}", e)
                    }
                }
            }
        }

        return "Sync Complete! Imported $importedCount new events, Exported $exportedCount tasks."
    }

    // Helper to query all reminders for a given calendar event ID
    private fun getEventReminders(context: Context, eventId: Long): List<Int> {
        val list = mutableListOf<Int>()
        val projection = arrayOf(CalendarContract.Reminders.MINUTES)
        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    list.add(it.getInt(0))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying reminders for event $eventId: ${e.message}")
        }
        return list
    }

    // Helper to clear and write reminders for a given calendar event ID
    private fun setEventReminders(context: Context, eventId: Long, minutesList: List<Int>) {
        val resolver = context.contentResolver
        try {
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old reminders for event $eventId: ${e.message}")
        }

        for (mins in minutesList) {
            try {
                val values = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, mins)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting reminder ($mins min) for event $eventId: ${e.message}")
            }
        }
    }

    // Helper to parse time from description
    private fun parseTaskTime(description: String): Pair<Int, Int>? {
        val amPmRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\s*(AM|PM)\]""", RegexOption.IGNORE_CASE)
        val amPmMatch = amPmRegex.find(description)
        if (amPmMatch != null) {
            var hour = amPmMatch.groupValues[1].toIntOrNull() ?: 0
            val minute = amPmMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = amPmMatch.groupValues[3].uppercase(Locale.US)
            if (ampm == "PM" && hour < 12) {
                hour += 12
            } else if (ampm == "AM" && hour == 12) {
                hour = 0
            }
            return Pair(hour, minute)
        }

        val stdRegex = Regex("""\[Time:\s*(\d{1,2}):(\d{2})\]""")
        val stdMatch = stdRegex.find(description)
        if (stdMatch != null) {
            val hour = stdMatch.groupValues[1].toIntOrNull() ?: 0
            val minute = stdMatch.groupValues[2].toIntOrNull() ?: 0
            return Pair(hour, minute)
        }
        return null
    }

    private fun parseTaskDuration(description: String): Int {
        val regex = Regex("""\[Duration:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
        val match = regex.find(description)
        if (match != null) {
            val durationStr = match.groupValues[1].trim().lowercase(Locale.US)
            
            // Check for hour/hours/hr/hrs/h
            if (durationStr.contains("hour") || durationStr.contains("hr") || durationStr.contains("h")) {
                // Find the decimal number or integer before/in the unit
                val numRegex = Regex("""(\d+\.?\d*)""")
                val numMatch = numRegex.find(durationStr)
                if (numMatch != null) {
                    val numFloat = numMatch.groupValues[1].toFloatOrNull()
                    if (numFloat != null && numFloat > 0f) {
                        return (numFloat * 60).toInt()
                    }
                }
            }
            
            // Otherwise, try to extract minutes
            val digits = durationStr.filter { it.isDigit() }
            val durationInt = digits.toIntOrNull()
            if (durationInt != null && durationInt > 0) {
                return durationInt
            }
        }
        return 15
    }

    private fun getTaskRemindersInMinutes(description: String): List<Int> {
        val metaRemindersPattern = Regex("""\[Reminders: ([^\]]+)\]""")
        val match = metaRemindersPattern.find(description) ?: return emptyList()
        val content = match.groupValues[1]
        return content.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "None" }
            .mapNotNull { parseReminderStringToMinutes(it) }
    }

    private fun parseReminderStringToMinutes(reminderStr: String): Int? {
        val clean = reminderStr.lowercase().trim()
        if (clean.contains("at time of event") || clean.contains("at time")) {
            return 0
        }
        val cleanBefore = clean.replace(" before", "").trim()
        val parts = cleanBefore.split(" ")
        if (parts.size < 2) return null
        val num = parts[0].toIntOrNull() ?: return null
        val unit = parts[1]
        return when {
            unit.startsWith("min") -> num
            unit.startsWith("hour") -> num * 60
            unit.startsWith("day") -> num * 24 * 60
            else -> null
        }
    }

    private fun formatMinutesToReminderString(minutes: Int): String {
        return when {
            minutes == 0 -> "At time of event"
            minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} days before"
            minutes % 60 == 0 -> "${minutes / 60} hours before"
            else -> "$minutes minutes before"
        }
    }
}
