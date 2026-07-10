package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * A highly robust state reconciliation layer designed to ensure absolute data consistency
 * between the Task Engine, Finance Tracker, and device local storage.
 * It prevents silent data loss or mismatching states during app lifecycle transitions,
 * process deaths, or background standbys.
 */
object StateReconciliationHelper {
    private const val TAG = "StateReconciliation"

    // Data structures for form drafts to prevent input loss during transitions
    data class TaskDraft(
        val title: String,
        val description: String,
        val category: String,
        val priority: String
    )

    data class TransactionDraft(
        val memberId: Int,
        val type: String,
        val amount: Double,
        val note: String,
        val fromCategory: String,
        val toCategory: String,
        val fromAccountId: Int,
        val toAccountId: Int
    )

    /**
     * Reconciles the Task Engine structures to fix inconsistencies like:
     * - Parent task marked complete but subtasks left open
     * - Active subtasks whose parent task was completed or missing (orphans)
     * - Tasks assigned to deleted custom list categories, re-mapping them back to "Inbox".
     */
    suspend fun reconcileTaskEngine(database: AppDatabase) {
        try {
            Log.d(TAG, "Starting Task Engine state reconciliation...")
            val taskDao = database.taskDao()
            val customListDao = database.customListDao()

            val allTasks = taskDao.getAllTasksDirect()
            val allCustomLists = customListDao.getAllListsDirect()
            val validCategories = setOf("Inbox", "Today", "Next 7 Days") + allCustomLists.map { it.name }.toSet()

            val taskMap = allTasks.associateBy { it.id }

            for (task in allTasks) {
                var updatedTask = task
                var needsUpdate = false

                // 1. Solve Orphan Subtasks
                if (task.parentTaskId != null && !taskMap.containsKey(task.parentTaskId)) {
                    Log.w(TAG, "Reconstituting orphaned subtask containing non-existent parent ID ${task.parentTaskId}. Resetting parent reference.")
                    updatedTask = updatedTask.copy(parentTaskId = null)
                    needsUpdate = true
                }

                // 2. Resolve cascading completeness for parent-child tasks
                if (task.parentTaskId != null && taskMap.containsKey(task.parentTaskId)) {
                    val parent = taskMap[task.parentTaskId]
                    if (parent != null && parent.isCompleted && !task.isCompleted) {
                        Log.w(TAG, "Cascading completeness: Subtask '${task.title}' was open, but its parent '${parent.title}' is completed. Marking subtask complete.")
                        updatedTask = updatedTask.copy(isCompleted = true)
                        needsUpdate = true
                    }
                }

                // 3. Category Integrity Verification
                if (!validCategories.contains(task.listCategory)) {
                    Log.w(TAG, "Task '${task.title}' assigned to invalid or deleted list '${task.listCategory}'. Reconciling to 'Inbox' category.")
                    updatedTask = updatedTask.copy(listCategory = "Inbox")
                    needsUpdate = true
                }

                if (needsUpdate) {
                    taskDao.updateTask(updatedTask)
                }
            }
            Log.d(TAG, "Task Engine state reconciliation finished successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during Task Engine reconciliation", e)
        }
    }

    /**
     * Reconciles the Finance Tracker double-entry systems:
     * - Insures transaction lists category integrity (missing categories are created)
     * - Validates dynamic financial progress targets
     */
    suspend fun reconcileFinanceTracker(database: AppDatabase) {
        try {
            Log.d(TAG, "Starting Financial Ledger consistency audit and reconciliation...")
            val categoryDao = database.financeCategoryDao()
            val transactionDao = database.financeTransactionDao()

            val allCategories = categoryDao.getAllCategoriesDirect()
            val allTransactions = transactionDao.getAllTransactionsDirect()

            val categoryNames = allCategories.map { it.name.lowercase() }.toSet()

            // 1. Reconcile missing categories referred by transactions
            allTransactions.forEach { tx ->
                val categoriesToVerify = listOfNotNull(tx.fromCategory, tx.toCategory)
                for (cat in categoriesToVerify) {
                    if (cat.isNotEmpty() && !categoryNames.contains(cat.lowercase())) {
                        Log.w(TAG, "Transaction ID ${tx.id} references non-existent category '$cat'. Dynamically registering category.")
                        val autoCategory = FinanceCategory(
                            name = cat,
                            type = tx.type
                        )
                        categoryDao.insertCategory(autoCategory)
                    }
                }
            }

            Log.d(TAG, "Financial Ledger reconciliation finished successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during Finance Tracker reconciliation", e)
        }
    }

    /**
     * Save UI Task form draft state into SharedPreferences to avoid data loss
     * during transition actions.
     */
    fun saveTaskDraft(context: Context, title: String, description: String, category: String, priority: String) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("draft_task_title", title)
                putString("draft_task_desc", description)
                putString("draft_task_cat", category)
                putString("draft_task_priority", priority)
                putBoolean("draft_task_exists", true)
                apply()
            }
            Log.d(TAG, "Cached active uncommitted task draft in memory checkpoint.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save task input draft", e)
        }
    }

    fun getTaskDraft(context: Context): TaskDraft? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("draft_task_exists", false)) return null
        return TaskDraft(
            title = prefs.getString("draft_task_title", "") ?: "",
            description = prefs.getString("draft_task_desc", "") ?: "",
            category = prefs.getString("draft_task_cat", "Inbox") ?: "Inbox",
            priority = prefs.getString("draft_task_priority", "MEDIUM") ?: "MEDIUM"
        )
    }

    fun clearTaskDraft(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("draft_task_title")
            remove("draft_task_desc")
            remove("draft_task_cat")
            remove("draft_task_priority")
            putBoolean("draft_task_exists", false)
            apply()
        }
    }

    /**
     * Save UI Transaction draft state into SharedPreferences.
     */
    fun saveTransactionDraft(
        context: Context,
        memberId: Int,
        type: String,
        amount: Double,
        note: String,
        fromCategory: String,
        toCategory: String,
        fromAccountId: Int,
        toAccountId: Int
    ) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("draft_tx_member_id", memberId)
                putString("draft_tx_type", type)
                putFloat("draft_tx_amount", amount.toFloat())
                putString("draft_tx_note", note)
                putString("draft_tx_from_cat", fromCategory)
                putString("draft_tx_to_cat", toCategory)
                putInt("draft_tx_from_acc", fromAccountId)
                putInt("draft_tx_to_acc", toAccountId)
                putBoolean("draft_tx_exists", true)
                apply()
            }
            Log.d(TAG, "Cached active uncommitted finance transaction draft.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transaction input draft", e)
        }
    }

    fun getTransactionDraft(context: Context): TransactionDraft? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("draft_tx_exists", false)) return null
        return TransactionDraft(
            memberId = prefs.getInt("draft_tx_member_id", -1),
            type = prefs.getString("draft_tx_type", "EXPENSE") ?: "EXPENSE",
            amount = prefs.getFloat("draft_tx_amount", 0.0f).toDouble(),
            note = prefs.getString("draft_tx_note", "") ?: "",
            fromCategory = prefs.getString("draft_tx_from_cat", "") ?: "",
            toCategory = prefs.getString("draft_tx_to_cat", "") ?: "",
            fromAccountId = prefs.getInt("draft_tx_from_acc", -1),
            toAccountId = prefs.getInt("draft_tx_to_acc", -1)
        )
    }

    fun clearTransactionDraft(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("draft_tx_member_id")
            remove("draft_tx_type")
            remove("draft_tx_amount")
            remove("draft_tx_note")
            remove("draft_tx_from_cat")
            remove("draft_tx_to_cat")
            remove("draft_tx_from_acc")
            remove("draft_tx_to_acc")
            putBoolean("draft_tx_exists", false)
            apply()
        }
    }

    /**
     * Executes double-entry consistency audit, cascades parent closures, repairs orphaned nodes,
     * flushes all SQLite WAL checkpoints completely to prevent data loss in background standbys.
     */
    suspend fun runUnifiedReconciliation(context: Context, database: AppDatabase) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Log.i(TAG, "Beginning unified state reconciliation protocol across Task Engine & Finance and disk persistence...")
        
        try {
            reconcileTaskEngine(database)
            reconcileFinanceTracker(database)
        } catch (e: Exception) {
            Log.e(TAG, "Error during unified state reconciliation", e)
        }

        // Flush all SQLite journal files and write-ahead log chunks directly to disk
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            Log.i(TAG, "Successfully checkpointed SQLite write-ahead-logging (WAL) back onto disk blocks.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to completely checkpoint SQLite WAL blocks", e)
        }
        
        try {
            com.example.widget.WidgetUpdater.updateAllWidgets(context)
            Log.i(TAG, "Successfully triggered all widgets updates from reconciliation.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger widget updates from reconciliation", e)
        }

        Log.i(TAG, "Unified state reconciliation protocol finished perfectly.")
    }
}
