package com.premraj.notiflow.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.premraj.notiflow.intelligence.ExpenseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationStore(context: Context) {
    private val helper = Db(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _expenses = MutableStateFlow<List<ExpenseTransaction>>(emptyList())
    val expenses: StateFlow<List<ExpenseTransaction>> = _expenses.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    suspend fun upsertIncoming(
        incoming: IncomingNotification,
        classification: ClassificationResult,
        isVip: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val existing = findByKey(db, incoming.key)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("notification_key", incoming.key)
            put("package_name", incoming.packageName)
            put("app_name", incoming.appName)
            putNullable("title", incoming.title)
            putNullable("body", incoming.body)
            putNullable("sender", incoming.sender)
            put("posted_at", existing?.postedAt ?: incoming.postedAt)
            put("updated_at", now)
            putNull("removed_at")
            put("is_active", 1)
            put("category", if (existing?.categoryOverridden == true) existing.category.name else classification.category.name)
            put("priority", if (existing?.priorityOverridden == true) existing.priority.name else classification.priority.name)
            put("confidence", classification.confidence)
            put("category_overridden", if (existing?.categoryOverridden == true) 1 else 0)
            put("priority_overridden", if (existing?.priorityOverridden == true) 1 else 0)
            put("state", existing?.state?.name ?: NotificationState.ACTIVE.name)
            if (existing?.remindAt != null) put("remind_at", existing.remindAt) else putNull("remind_at")
            put("pinned", if (existing?.pinned == true) 1 else 0)
            put("is_vip", if (isVip) 1 else 0)
            put("read", if (existing?.read == true) 1 else 0)
            classification.amountHint?.let { put("amount_hint", it) } ?: existing?.amountHint?.let { put("amount_hint", it) }
            put("is_deleted_by_sender", if (existing?.isDeletedBySender == true) 1 else 0)
        }

        val id = if (existing == null) {
            db.insertOrThrow(TABLE, null, values)
        } else {
            db.update(TABLE, values, "id=?", arrayOf(existing.id.toString()))
            existing.id
        }

        // On-device financial parsing for UPI & Bank transactions
        val parsedExpense = ExpenseParser.parse(
            title = incoming.title,
            body = incoming.body,
            packageName = incoming.packageName,
            appName = incoming.appName,
            notificationId = id,
            timestamp = incoming.postedAt
        )

        if (parsedExpense != null) {
            val expValues = ContentValues().apply {
                put("notification_id", id)
                put("amount", parsedExpense.amount)
                put("transaction_type", parsedExpense.transactionType.name)
                putNullable("merchant_or_party", parsedExpense.merchantOrParty)
                putNullable("account_ref", parsedExpense.accountRef)
                if (parsedExpense.balanceAfter != null) put("balance_after", parsedExpense.balanceAfter) else putNull("balance_after")
                put("expense_category", parsedExpense.expenseCategory.name)
                put("timestamp", incoming.postedAt)
            }
            db.insertWithOnConflict(TABLE_EXPENSES, null, expValues, SQLiteDatabase.CONFLICT_REPLACE)
        }

        refresh()
        id
    }

    suspend fun updateClassification(id: Long, classification: ClassificationResult) = withContext(Dispatchers.IO) {
        val existing = getInternal(helper.readableDatabase, id) ?: return@withContext
        val values = ContentValues().apply {
            if (!existing.categoryOverridden) put("category", classification.category.name)
            if (!existing.priorityOverridden) put("priority", classification.priority.name)
            put("confidence", classification.confidence)
            classification.amountHint?.let { put("amount_hint", it) }
        }
        helper.writableDatabase.update(TABLE, values, "id=?", arrayOf(id.toString()))
        refresh()
    }

    suspend fun markRemoved(notificationKey: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("is_active", 0)
            put("removed_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update(TABLE, values, "notification_key=?", arrayOf(notificationKey))
        refresh()
    }

    suspend fun setState(id: Long, state: NotificationState) = update(id) {
        put("state", state.name)
        if (state != NotificationState.LATER) putNull("remind_at")
    }

    suspend fun setReminder(id: Long, remindAt: Long?) = update(id) {
        if (remindAt == null) {
            putNull("remind_at")
            put("state", NotificationState.ACTIVE.name)
        } else {
            put("remind_at", remindAt)
            put("state", NotificationState.LATER.name)
        }
    }

    suspend fun setCategory(id: Long, category: NotificationCategory, userOverride: Boolean = true) = update(id) {
        put("category", category.name)
        put("category_overridden", if (userOverride) 1 else 0)
    }

    suspend fun setPriority(id: Long, priority: NotificationPriority, userOverride: Boolean = true) = update(id) {
        put("priority", priority.name)
        put("priority_overridden", if (userOverride) 1 else 0)
    }

    suspend fun setVip(id: Long, vip: Boolean) = update(id) { put("is_vip", if (vip) 1 else 0) }
    suspend fun setRead(id: Long, read: Boolean) = update(id) { put("read", if (read) 1 else 0) }
    suspend fun setPinned(id: Long, pinned: Boolean) = update(id) { put("pinned", if (pinned) 1 else 0) }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, "id=?", arrayOf(id.toString()))
        helper.writableDatabase.delete(TABLE_EXPENSES, "notification_id=?", arrayOf(id.toString()))
        refresh()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, null, null)
        helper.writableDatabase.delete(TABLE_EXPENSES, null, null)
        refresh()
    }

    suspend fun get(id: Long): NotificationItem? = withContext(Dispatchers.IO) {
        getInternal(helper.readableDatabase, id)
    }

    suspend fun observedApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT package_name, MAX(app_name) FROM $TABLE GROUP BY package_name ORDER BY MAX(app_name) COLLATE NOCASE",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
            }
        }
    }

    suspend fun itemsSince(timestamp: Long): List<NotificationItem> = withContext(Dispatchers.IO) {
        query("posted_at>=?", arrayOf(timestamp.toString()), "posted_at DESC", limit = 1000)
    }

    suspend fun cleanup(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val retentionCutoff = now - retentionDays * 86_400_000L
        var count = helper.writableDatabase.delete(
            TABLE,
            "posted_at<? AND pinned=0 AND state NOT IN (?,?)",
            arrayOf(retentionCutoff.toString(), NotificationState.LATER.name, NotificationState.ACTIVE.name)
        )

        val otpCutoff = now - 2 * 86_400_000L
        count += helper.writableDatabase.delete(
            TABLE,
            "posted_at<? AND pinned=0 AND remind_at IS NULL AND category=?",
            arrayOf(otpCutoff.toString(), NotificationCategory.OTP.name)
        )
        val lowValueCutoff = now - minOf(retentionDays, 14) * 86_400_000L
        count += helper.writableDatabase.delete(
            TABLE,
            "posted_at<? AND pinned=0 AND remind_at IS NULL AND category IN (?,?)",
            arrayOf(lowValueCutoff.toString(), NotificationCategory.PROMOTION.name, NotificationCategory.SPAM.name)
        )
        val deliveryCutoff = now - minOf(retentionDays, 21) * 86_400_000L
        count += helper.writableDatabase.delete(
            TABLE,
            "posted_at<? AND pinned=0 AND remind_at IS NULL AND category=? AND is_active=0",
            arrayOf(deliveryCutoff.toString(), NotificationCategory.DELIVERY.name)
        )

        // Clean orphaned expenses
        helper.writableDatabase.execSQL("DELETE FROM $TABLE_EXPENSES WHERE notification_id NOT IN (SELECT id FROM $TABLE)")

        refresh()
        count
    }

    suspend fun storageStats(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val count = helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        count to helper.databaseName.let { _ ->
            runCatching { helper.writableDatabase.path?.let { java.io.File(it).length() } ?: 0L }.getOrDefault(0L)
        }
    }

    suspend fun findRecentMessage(
        packageName: String,
        sender: String?,
        withinMillis: Long = 12 * 60 * 60 * 1000L
    ): NotificationItem? = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        val since = System.currentTimeMillis() - withinMillis
        val selection = if (sender.isNullOrBlank()) {
            "package_name = ? AND posted_at >= ? AND body IS NOT NULL AND body != '' AND is_deleted_by_sender = 0"
        } else {
            "package_name = ? AND (sender = ? OR title = ?) AND posted_at >= ? AND body IS NOT NULL AND body != '' AND is_deleted_by_sender = 0"
        }
        val args = if (sender.isNullOrBlank()) {
            arrayOf(packageName, since.toString())
        } else {
            arrayOf(packageName, sender, sender, since.toString())
        }
        db.query(
            TABLE,
            null,
            selection,
            args,
            null,
            null,
            "posted_at DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toItem(null) else null
        }
    }

    suspend fun markDeletedBySender(id: Long): NotificationItem? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("is_deleted_by_sender", 1)
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update(TABLE, values, "id=?", arrayOf(id.toString()))
        refresh()
        getInternal(helper.readableDatabase, id)
    }

    suspend fun countDeletedMessages(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE is_deleted_by_sender = 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun countUnwantedOldMessages(days: Int = 15): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val cursor = helper.readableDatabase.rawQuery(
            """
            SELECT COUNT(*) FROM $TABLE 
            WHERE posted_at < ? 
              AND pinned = 0 
              AND is_vip = 0 
              AND remind_at IS NULL 
              AND (category IN ('${NotificationCategory.PROMOTION.name}', '${NotificationCategory.SPAM.name}') 
                   OR (body LIKE '%recharge%' OR body LIKE '%cashback%' OR body LIKE '%discount%' OR body LIKE '%off%'))
            """.trimIndent(),
            arrayOf(cutoff.toString())
        )
        cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    suspend fun deleteUnwantedOldMessages(days: Int = 15): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val count = helper.writableDatabase.delete(
            TABLE,
            """
            posted_at < ? 
              AND pinned = 0 
              AND is_vip = 0 
              AND remind_at IS NULL 
              AND (category IN ('${NotificationCategory.PROMOTION.name}', '${NotificationCategory.SPAM.name}') 
                   OR (body LIKE '%recharge%' OR body LIKE '%cashback%' OR body LIKE '%discount%' OR body LIKE '%off%'))
            """.trimIndent(),
            arrayOf(cutoff.toString())
        )
        refresh()
        count
    }

    fun getExpenseSummary(since: Long = 0L, until: Long = Long.MAX_VALUE): ExpenseSummary {
        val list = queryExpenses("timestamp >= ? AND timestamp <= ?", arrayOf(since.toString(), until.toString()))
        var totalDebit = 0.0
        var totalCredit = 0.0
        var debitCount = 0
        var creditCount = 0
        val categoryMap = mutableMapOf<ExpenseCategory, Double>()

        for (tx in list) {
            if (tx.transactionType == TransactionType.DEBIT) {
                totalDebit += tx.amount
                debitCount++
                val curr = categoryMap.getOrDefault(tx.expenseCategory, 0.0)
                categoryMap[tx.expenseCategory] = curr + tx.amount
            } else {
                totalCredit += tx.amount
                creditCount++
            }
        }

        return ExpenseSummary(
            totalDebit = totalDebit,
            totalCredit = totalCredit,
            netBalanceDiff = totalCredit - totalDebit,
            debitCount = debitCount,
            creditCount = creditCount,
            categoryBreakdown = categoryMap,
            transactions = list
        )
    }

    private suspend fun update(id: Long, fill: ContentValues.() -> Unit) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            fill()
            put("updated_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update(TABLE, values, "id=?", arrayOf(id.toString()))
        refresh()
    }

    private fun findByKey(db: SQLiteDatabase, key: String): NotificationItem? {
        return db.query(TABLE, null, "notification_key=?", arrayOf(key), null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) cursor.toItem(null) else null
        }
    }

    private fun getInternal(db: SQLiteDatabase, id: Long): NotificationItem? {
        val expense = queryExpenseByNotifId(db, id)
        return db.query(TABLE, null, "id=?", arrayOf(id.toString()), null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) cursor.toItem(expense) else null
        }
    }

    private fun queryExpenseByNotifId(db: SQLiteDatabase, notifId: Long): ExpenseTransaction? {
        return db.query(TABLE_EXPENSES, null, "notification_id=?", arrayOf(notifId.toString()), null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) cursor.toExpense() else null
        }
    }

    private fun queryExpenses(
        selection: String? = null,
        args: Array<String>? = null,
        order: String = "timestamp DESC",
        limit: Int = 5000
    ): List<ExpenseTransaction> {
        return helper.readableDatabase.query(TABLE_EXPENSES, null, selection, args, null, null, order, limit.toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toExpense())
            }
        }
    }

    private fun query(
        selection: String? = null,
        args: Array<String>? = null,
        order: String = "pinned DESC, posted_at DESC",
        limit: Int = 5000
    ): List<NotificationItem> {
        val allExpenses = queryExpenses().associateBy { it.notificationId }
        return helper.readableDatabase.query(TABLE, null, selection, args, null, null, order, limit.toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    add(cursor.toItem(allExpenses[id]))
                }
            }
        }
    }

    private suspend fun refresh() {
        val expensesList = queryExpenses()
        _expenses.value = expensesList
        _items.value = query()
    }

    private fun Cursor.toExpense() = ExpenseTransaction(
        id = getLong(col("id")),
        notificationId = getLong(col("notification_id")),
        amount = getDouble(col("amount")),
        transactionType = enumOrDefault(getString(col("transaction_type")), TransactionType.DEBIT),
        merchantOrParty = getNullableString("merchant_or_party"),
        accountRef = getNullableString("account_ref"),
        balanceAfter = getNullableDouble("balance_after"),
        expenseCategory = enumOrDefault(getString(col("expense_category")), ExpenseCategory.GENERAL),
        timestamp = getLong(col("timestamp"))
    )

    private fun Cursor.toItem(expense: ExpenseTransaction?) = NotificationItem(
        id = getLong(col("id")),
        notificationKey = getString(col("notification_key")),
        packageName = getString(col("package_name")),
        appName = getString(col("app_name")),
        title = getNullableString("title"),
        body = getNullableString("body"),
        sender = getNullableString("sender"),
        postedAt = getLong(col("posted_at")),
        updatedAt = getLong(col("updated_at")),
        removedAt = getNullableLong("removed_at"),
        isActiveOnSystem = getInt(col("is_active")) == 1,
        category = enumOrDefault(getString(col("category")), NotificationCategory.OTHER),
        priority = enumOrDefault(getString(col("priority")), NotificationPriority.NORMAL),
        confidence = getFloat(col("confidence")),
        categoryOverridden = getInt(col("category_overridden")) == 1,
        priorityOverridden = getInt(col("priority_overridden")) == 1,
        state = enumOrDefault(getString(col("state")), NotificationState.ACTIVE),
        remindAt = getNullableLong("remind_at"),
        pinned = getInt(col("pinned")) == 1,
        isVip = getInt(col("is_vip")) == 1,
        read = getInt(col("read")) == 1,
        amountHint = getNullableDouble("amount_hint"),
        isDeletedBySender = getIntOrZero("is_deleted_by_sender") == 1,
        expenseTransaction = expense
    )

    private fun Cursor.col(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.getIntOrZero(name: String): Int = getColumnIndex(name).let { if (it >= 0) getInt(it) else 0 }
    private fun Cursor.getNullableString(name: String): String? = col(name).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.getNullableLong(name: String): Long? = col(name).let { if (isNull(it)) null else getLong(it) }
    private fun Cursor.getNullableDouble(name: String): Double? = col(name).let { if (isNull(it)) null else getDouble(it) }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private class Db(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    notification_key TEXT NOT NULL UNIQUE,
                    package_name TEXT NOT NULL,
                    app_name TEXT NOT NULL,
                    title TEXT,
                    body TEXT,
                    sender TEXT,
                    posted_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    removed_at INTEGER,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    category TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    confidence REAL NOT NULL DEFAULT 0,
                    category_overridden INTEGER NOT NULL DEFAULT 0,
                    priority_overridden INTEGER NOT NULL DEFAULT 0,
                    state TEXT NOT NULL DEFAULT 'ACTIVE',
                    remind_at INTEGER,
                    pinned INTEGER NOT NULL DEFAULT 0,
                    is_vip INTEGER NOT NULL DEFAULT 0,
                    read INTEGER NOT NULL DEFAULT 0,
                    amount_hint REAL,
                    is_deleted_by_sender INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_notifications_posted ON $TABLE(posted_at DESC)")
            db.execSQL("CREATE INDEX idx_notifications_package ON $TABLE(package_name)")
            db.execSQL("CREATE INDEX idx_notifications_state ON $TABLE(state)")
            db.execSQL("CREATE INDEX idx_notifications_category ON $TABLE(category)")
            db.execSQL("CREATE INDEX idx_notifications_deleted ON $TABLE(is_deleted_by_sender)")

            createExpenseTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN is_deleted_by_sender INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_deleted ON $TABLE(is_deleted_by_sender)")
            }
            if (oldVersion < 3) {
                createExpenseTable(db)
                retroactivelyParseExpenses(db)
            }
        }

        private fun createExpenseTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_EXPENSES (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    notification_id INTEGER NOT NULL UNIQUE,
                    amount REAL NOT NULL,
                    transaction_type TEXT NOT NULL,
                    merchant_or_party TEXT,
                    account_ref TEXT,
                    balance_after REAL,
                    expense_category TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_timestamp ON $TABLE_EXPENSES(timestamp DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_type ON $TABLE_EXPENSES(transaction_type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_category ON $TABLE_EXPENSES(expense_category)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_notif_id ON $TABLE_EXPENSES(notification_id)")
        }

        private fun retroactivelyParseExpenses(db: SQLiteDatabase) {
            runCatching {
                db.rawQuery("SELECT id, title, body, package_name, app_name, posted_at FROM $TABLE", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val title = if (cursor.isNull(1)) null else cursor.getString(1)
                        val body = if (cursor.isNull(2)) null else cursor.getString(2)
                        val pkg = cursor.getString(3)
                        val appName = cursor.getString(4)
                        val postedAt = cursor.getLong(5)

                        val parsed = ExpenseParser.parse(title, body, pkg, appName, id, postedAt)
                        if (parsed != null) {
                            val values = ContentValues().apply {
                                put("notification_id", id)
                                put("amount", parsed.amount)
                                put("transaction_type", parsed.transactionType.name)
                                if (parsed.merchantOrParty != null) put("merchant_or_party", parsed.merchantOrParty) else putNull("merchant_or_party")
                                if (parsed.accountRef != null) put("account_ref", parsed.accountRef) else putNull("account_ref")
                                if (parsed.balanceAfter != null) put("balance_after", parsed.balanceAfter) else putNull("balance_after")
                                put("expense_category", parsed.expenseCategory.name)
                                put("timestamp", postedAt)
                            }
                            db.insertWithOnConflict(TABLE_EXPENSES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val DB_NAME = "notiflow.db"
        const val DB_VERSION = 3
        const val TABLE = "notifications"
        const val TABLE_EXPENSES = "expense_transactions"
    }
}

