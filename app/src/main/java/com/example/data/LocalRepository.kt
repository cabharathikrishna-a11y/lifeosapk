package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class LocalRepository(val db: AppDatabase, val context: android.content.Context) {

    private val backupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backupJob: Job? = null

    private fun triggerAutoBackup() {
        val appContext = context.applicationContext
        if (com.example.util.GoogleDriveSyncManager.hasDrivePermission(appContext)) {
            backupJob?.cancel()
            backupJob = backupScope.launch {
                delay(5000L) // Debounce by 5 seconds
                try {
                    android.util.Log.d("LocalRepository", "Auto-backing up to Google Drive...")
                    com.example.util.GoogleDriveSyncManager.backupAllAppData(appContext, db, {})
                } catch (e: Exception) {
                    android.util.Log.e("LocalRepository", "Auto-backup failed", e)
                }
            }
        }
    }

    init {
        val observer = object : InvalidationTracker.Observer(
            arrayOf(
                "tasks", "habits", "habit_completions", "journal_entries", "ledger_entries",
                "deadlines", "financial_goals", "contacts", "app_files", "focus_records",
                "keep_notes", "custom_lists", "family_members", "financial_accounts",
                "financial_logs", "finance_transactions", "finance_categories", "health_records"
            )
        ) {
            override fun onInvalidated(tables: Set<String>) {
                android.util.Log.d("LocalRepository", "Tables invalidated: $tables, checking auto-backup...")
                triggerAutoBackup()
            }
        }
        db.invalidationTracker.addObserver(observer)
    }

    private val taskDao = db.taskDao()
    private val habitDao = db.habitDao()
    private val journalDao = db.journalDao()
    private val ledgerDao = db.ledgerDao()
    private val deadlineDao = db.deadlineDao()
    private val financialGoalDao = db.financialGoalDao()
    private val contactDao = db.contactDao()
    private val appFileDao = db.appFileDao()
    private val customListDao = db.customListDao()
    private val familyMemberDao = db.familyMemberDao()
    private val financialAccountDao = db.financialAccountDao()
    private val financialLogDao = db.financialLogDao()
    private val financeTransactionDao = db.financeTransactionDao()
    private val financeCategoryDao = db.financeCategoryDao()
    private val focusRecordDao = db.focusRecordDao()

    // Custom List Operations
    val allLists: Flow<List<CustomList>> = customListDao.getAllLists()

    suspend fun insertList(list: CustomList): Long = withContext(NonCancellable) {
        customListDao.insertList(list)
    }

    suspend fun updateList(list: CustomList) = withContext(NonCancellable) {
        customListDao.updateList(list)
    }

    suspend fun deleteList(list: CustomList) = withContext(NonCancellable) {
        customListDao.deleteList(list)
    }

    // Task Operations
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    
    suspend fun insertTask(task: Task): Long = withContext(NonCancellable) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) = withContext(NonCancellable) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) = withContext(NonCancellable) {
        taskDao.deleteTask(task)
        // Also delete subtasks if it's a parent
        taskDao.deleteSubtasks(task.id)
    }

    // Habit Operations
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    suspend fun insertHabit(habit: Habit): Long = withContext(NonCancellable) {
        habitDao.insertHabit(habit)
    }

    suspend fun updateHabit(habit: Habit) = withContext(NonCancellable) {
        habitDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) = withContext(NonCancellable) {
        habitDao.deleteHabit(habit)
    }

    suspend fun insertHabitCompletion(habitId: Int, dateString: String) = withContext(NonCancellable) {
        habitDao.insertCompletion(HabitCompletion(habitId = habitId, dateString = dateString))
    }

    suspend fun deleteHabitCompletion(habitId: Int, dateString: String) = withContext(NonCancellable) {
        habitDao.deleteCompletion(habitId, dateString)
    }

    // Journal Operations
    val allJournalEntries: Flow<List<JournalEntry>> = journalDao.getAllJournalEntries()

    fun searchJournal(query: String): Flow<List<JournalEntry>> {
        return journalDao.searchJournalEntries("%$query%")
    }

    suspend fun insertJournal(entry: JournalEntry): Long = withContext(NonCancellable) {
        journalDao.insertJournalEntry(entry)
    }

    suspend fun deleteJournal(entry: JournalEntry) = withContext(NonCancellable) {
        journalDao.deleteJournalEntry(entry)
    }

    // Financial Operations
    val allLedgerEntries: Flow<List<LedgerEntry>> = ledgerDao.getAllLedgerEntries()

    suspend fun insertLedger(entry: LedgerEntry) = withContext(NonCancellable) {
        ledgerDao.insertLedgerEntry(entry)
    }

    suspend fun deleteLedger(entry: LedgerEntry) = withContext(NonCancellable) {
        ledgerDao.deleteLedgerEntry(entry)
    }

    // Deadline Operations
    val allDeadlines: Flow<List<Deadline>> = deadlineDao.getAllDeadlines()

    suspend fun insertDeadline(deadline: Deadline): Long = withContext(NonCancellable) {
        deadlineDao.insertDeadline(deadline)
    }

    suspend fun updateDeadline(deadline: Deadline) = withContext(NonCancellable) {
        deadlineDao.updateDeadline(deadline)
    }

    suspend fun deleteDeadline(deadline: Deadline) = withContext(NonCancellable) {
        deadlineDao.deleteDeadline(deadline)
    }

    // Financial Goal Operations
    val allFinancialGoals: Flow<List<FinancialGoal>> = financialGoalDao.getAllFinancialGoals()

    suspend fun insertFinancialGoal(goal: FinancialGoal): Long = withContext(NonCancellable) {
        financialGoalDao.insertFinancialGoal(goal)
    }

    suspend fun updateFinancialGoal(goal: FinancialGoal) = withContext(NonCancellable) {
        financialGoalDao.updateFinancialGoal(goal)
    }

    suspend fun deleteFinancialGoal(goal: FinancialGoal) = withContext(NonCancellable) {
        financialGoalDao.deleteFinancialGoal(goal)
    }

    // Contact Operations
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun insertContact(contact: Contact): Long = withContext(NonCancellable) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) = withContext(NonCancellable) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: Contact) = withContext(NonCancellable) {
        contactDao.deleteContact(contact)
    }

    // File Operations
    val allFiles: Flow<List<AppFile>> = appFileDao.getAllFiles()

    suspend fun insertFile(file: AppFile): Long = withContext(NonCancellable) {
        appFileDao.insertFile(file)
    }

    suspend fun deleteFile(file: AppFile) = withContext(NonCancellable) {
        appFileDao.deleteFile(file)
    }

    // Family Ledger Operations
    val allFamilyMembers: Flow<List<FamilyMember>> = familyMemberDao.getAllMembers()
    val allFinancialAccounts: Flow<List<FinancialAccount>> = financialAccountDao.getAllAccounts()
    val allFinancialLogs: Flow<List<FinancialLog>> = financialLogDao.getAllLogs()
    val allFinanceTransactions: Flow<List<FinanceTransaction>> = financeTransactionDao.getAllTransactions()
    val allFinanceCategories: Flow<List<FinanceCategory>> = financeCategoryDao.getAllCategories()

    suspend fun insertFamilyMember(member: FamilyMember): Long = withContext(NonCancellable) {
        familyMemberDao.insertMember(member)
    }

    suspend fun deleteFamilyMember(member: FamilyMember) = withContext(NonCancellable) {
        familyMemberDao.deleteMember(member)
    }

    suspend fun insertFinancialAccount(account: FinancialAccount): Long = withContext(NonCancellable) {
        financialAccountDao.insertAccount(account)
    }

    suspend fun deleteFinancialAccount(account: FinancialAccount) = withContext(NonCancellable) {
        financialAccountDao.deleteAccount(account)
    }

    suspend fun insertFinancialLog(log: FinancialLog): Long = withContext(NonCancellable) {
        financialLogDao.insertLog(log)
    }

    suspend fun deleteFinancialLog(log: FinancialLog) = withContext(NonCancellable) {
        financialLogDao.deleteLog(log)
    }

    suspend fun insertFinanceTransaction(transaction: FinanceTransaction): Long = withContext(NonCancellable) {
        financeTransactionDao.insertTransaction(transaction)
    }

    suspend fun deleteFinanceTransaction(transaction: FinanceTransaction) = withContext(NonCancellable) {
        financeTransactionDao.deleteTransaction(transaction)
    }

    suspend fun insertFinanceCategory(category: FinanceCategory): Long = withContext(NonCancellable) {
        financeCategoryDao.insertCategory(category)
    }

    suspend fun deleteFinanceCategory(category: FinanceCategory) = withContext(NonCancellable) {
        financeCategoryDao.deleteCategory(category)
    }

    // Focus Record Operations
    val allFocusRecords: Flow<List<FocusRecordEntity>> = focusRecordDao.getAllRecords()

    suspend fun insertFocusRecord(record: FocusRecordEntity): Long = withContext(NonCancellable) {
        focusRecordDao.insertRecord(record)
    }

    suspend fun updateFocusRecord(record: FocusRecordEntity) = withContext(NonCancellable) {
        focusRecordDao.updateRecord(record)
    }

    suspend fun deleteFocusRecord(record: FocusRecordEntity) = withContext(NonCancellable) {
        focusRecordDao.deleteRecord(record)
    }

    suspend fun getFocusRecordsForDate(dateStr: String): List<FocusRecordEntity> {
        return focusRecordDao.getRecordsForDate(dateStr)
    }

    suspend fun deleteFocusRecordsForDate(dateStr: String) = withContext(NonCancellable) {
        focusRecordDao.deleteRecordsForDate(dateStr)
    }

    // Keep Note Operations
    private val keepNoteDao = db.keepNoteDao()

    val allKeepNotes: Flow<List<KeepNote>> = keepNoteDao.getAllKeepNotes()

    suspend fun getAllKeepNotesDirect(): List<KeepNote> {
        return keepNoteDao.getAllKeepNotesDirect()
    }

    suspend fun insertKeepNote(note: KeepNote): Long = withContext(NonCancellable) {
        keepNoteDao.insertKeepNote(note)
    }

    suspend fun updateKeepNote(note: KeepNote) = withContext(NonCancellable) {
        keepNoteDao.updateKeepNote(note)
    }

    suspend fun deleteKeepNote(note: KeepNote) = withContext(NonCancellable) {
        keepNoteDao.deleteKeepNote(note)
    }

    suspend fun clearAllKeepNotes() = withContext(NonCancellable) {
        keepNoteDao.clearAllKeepNotes()
    }

    // Health Record Operations
    private val healthRecordDao = db.healthRecordDao()

    fun getHealthRecordFlow(dateString: String): Flow<HealthRecord?> {
        return healthRecordDao.getHealthRecordFlow(dateString)
    }

    suspend fun getHealthRecordDirect(dateString: String): HealthRecord? {
        return healthRecordDao.getHealthRecordDirect(dateString)
    }

    fun getAllHealthRecordsFlow(): Flow<List<HealthRecord>> {
        return healthRecordDao.getAllHealthRecordsFlow()
    }

    suspend fun insertOrUpdateHealthRecord(record: HealthRecord) = withContext(NonCancellable) {
        healthRecordDao.insertOrUpdate(record)
    }

    suspend fun clearAllHealthRecords() = withContext(NonCancellable) {
        healthRecordDao.clearAllHealthRecords()
    }

    suspend fun syncMissingHistoryLogs(myUsername: String, lastSyncTimestamp: Long) {
        val url = com.example.api.FirebaseConfig.getDatabaseUrl(context)
        val dbFirebase = com.google.firebase.database.FirebaseDatabase.getInstance(url)
        val ref = dbFirebase.getReference("users").child(myUsername).child("history_logs")
        
        // Query history logs by end time to get missing records since last sync
        // Query history logs: if full sync, query all; if delta, query by timestamp (since endTime is now a formatted string)
        val query = if (lastSyncTimestamp == 0L) {
            ref
        } else {
            ref.orderByChild("timestamp").startAt(lastSyncTimestamp.toDouble() + 1.0)
        }
        
        val existingRecords = focusRecordDao.getAllRecordsDirect()
        val existingKeys = existingRecords.map { "${it.timestamp}_${it.startTime}_${it.endTime}" }.toSet()

        withContext(Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                query.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        if (snapshot.exists()) {
                            val recordsToInsert = mutableListOf<FocusRecordEntity>()
                            val focusRecordsToLoad = mutableListOf<com.example.ui.FocusRecord>()
                            snapshot.children.forEach { child ->
                                try {
                                    val taskTitle = child.child("taskTitle").getValue(String::class.java) ?: ""
                                    val tag = child.child("tag").getValue(String::class.java) ?: ""
                                    val notes = child.child("notes").getValue(String::class.java) ?: ""
                                    val durationSeconds = child.child("durationSeconds").getValue(Int::class.java) ?: 0
                                    val durationMinutes = child.child("durationMinutes").getValue(Int::class.java) ?: (durationSeconds / 60)
                                    val recId = child.key ?: java.util.UUID.randomUUID().toString()
                                    
                                    // Parse timestamp with fallback to endTime for old records
                                    val timestamp = try {
                                        child.child("timestamp").getValue(Long::class.java)
                                    } catch (e: Exception) {
                                        null
                                    } ?: try {
                                        child.child("endTime").getValue(Long::class.java)
                                    } catch (e: Exception) {
                                        null
                                    } ?: System.currentTimeMillis()

                                    // Parse dateString with fallback to timestamp formatting for old records
                                    val dateString = try {
                                        child.child("dateString").getValue(String::class.java) ?: ""
                                    } catch (e: Exception) {
                                        ""
                                    }.takeIf { it.isNotEmpty() } ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

                                    // Parse startTime with fallback to formatting if it is stored as Long epoch ms
                                    val startTime = try {
                                        child.child("startTime").getValue(String::class.java) ?: ""
                                    } catch (e: Exception) {
                                        val startLong = child.child("startTime").getValue(Long::class.java) ?: 0L
                                        if (startLong > 0L) {
                                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(startLong))
                                        } else {
                                            ""
                                        }
                                    }

                                    // Parse endTime with fallback to formatting if it is stored as Long epoch ms
                                    val endTime = try {
                                        child.child("endTime").getValue(String::class.java) ?: ""
                                    } catch (e: Exception) {
                                        val endLong = child.child("endTime").getValue(Long::class.java) ?: 0L
                                        if (endLong > 0L) {
                                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(endLong))
                                        } else {
                                            ""
                                        }
                                    }
                                    
                                    val key = "${timestamp}_${startTime}_${endTime}"
                                    if (existingKeys.contains(key)) {
                                        return@forEach
                                    }

                                    recordsToInsert.add(
                                        FocusRecordEntity(
                                            taskTitle = taskTitle,
                                            tag = tag,
                                            notes = notes,
                                            durationSeconds = durationSeconds,
                                            durationMinutes = durationMinutes,
                                            dateString = dateString,
                                            startTime = startTime,
                                            endTime = endTime,
                                            timestamp = timestamp
                                        )
                                    )

                                    focusRecordsToLoad.add(
                                        com.example.ui.FocusRecord(
                                            startTime = startTime,
                                            endTime = endTime,
                                            taskTitle = taskTitle,
                                            durationMinutes = durationMinutes,
                                            dateString = dateString,
                                            notes = notes,
                                            durationSeconds = durationSeconds,
                                            tag = tag,
                                            id = recId,
                                            timestamp = timestamp
                                        )
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("LocalRepository", "Error parsing history log", e)
                                }
                            }
                            
                            if (recordsToInsert.isNotEmpty()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    recordsToInsert.forEach { entity ->
                                        focusRecordDao.insertRecord(entity)
                                    }
                                }
                            }

                            if (focusRecordsToLoad.isNotEmpty()) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    val currentList = com.example.util.FocusTimerManager.focusRecords.value
                                    val merged = (currentList + focusRecordsToLoad).distinctBy { it.id }
                                    com.example.util.FocusTimerManager.setFocusRecords(merged)
                                    com.example.util.FocusTimerManager.saveFocusRecords(context, merged)
                                    
                                    val totalMins = merged.sumOf { it.durationMinutes }
                                    com.example.util.FocusTimerManager.setTotalFocusMinutes(totalMins)
                                    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                    prefs.edit().putInt("total_focus_minutes", totalMins).apply()
                                }
                            }
                        }
                        continuation.resume(Unit, onCancellation = null)
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        continuation.resume(Unit, onCancellation = null)
                    }
                })
            }
        }
    }
}
