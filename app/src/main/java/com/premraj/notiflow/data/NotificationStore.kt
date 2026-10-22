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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NotificationStore(context: Context) {
    private val helper = Db(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _expenses = MutableStateFlow<List<ExpenseTransaction>>(emptyList())
    val expenses: StateFlow<List<ExpenseTransaction>> = _expenses.asStateFlow()

    private val _savedMedia = MutableStateFlow<List<SavedMedia>>(emptyList())
    val savedMedia: StateFlow<List<SavedMedia>> = _savedMedia.asStateFlow()

    private val _initialLoadComplete = MutableStateFlow(false)
    val initialLoadComplete: StateFlow<Boolean> = _initialLoadComplete.asStateFlow()

    private val mutationMutex = Mutex()

    init {
        scope.launch {
            try {
                mutationMutex.withLock {
                    refreshFromDb()
                    refreshSavedMediaFromDb()
                }
            } finally {
                _initialLoadComplete.value = true
            }
        }
    }

    suspend fun upsertIncoming(
        incoming: IncomingNotification,
        classification: ClassificationResult,
        isVip: Boolean,
        expenseTrackingEnabled: Boolean
    ): Long = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            val id = try {
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
                    // A fresh post for the same notification key is active again, so any
                    // prior source-removal marker belongs to the previous lifecycle.
                    put("is_removed_by_source", 0)
                }

                val rowId = if (existing == null) {
                    db.insertOrThrow(TABLE, null, values)
                } else {
                    db.update(TABLE, values, "id=?", arrayOf(existing.id.toString()))
                    existing.id
                }

                if (expenseTrackingEnabled) {
                    val parsedExpense = ExpenseParser.parse(
                        title = incoming.title,
                        body = incoming.body,
                        packageName = incoming.packageName,
                        appName = incoming.appName,
                        notificationId = rowId,
                        timestamp = incoming.postedAt
                    )
                    if (parsedExpense != null) {
                        val expValues = ContentValues().apply {
                            put("notification_id", rowId)
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
                }
                db.setTransactionSuccessful()
                rowId
            } finally {
                db.endTransaction()
            }
            updateCacheFromDb(id)
            id
        }
    }

    suspend fun updateClassification(id: Long, classification: ClassificationResult) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val existing = getInternal(helper.readableDatabase, id) ?: return@withLock
            val values = ContentValues().apply {
                if (!existing.categoryOverridden) put("category", classification.category.name)
                if (!existing.priorityOverridden) put("priority", classification.priority.name)
                put("confidence", classification.confidence)
                classification.amountHint?.let { put("amount_hint", it) }
            }
            helper.writableDatabase.update(TABLE, values, "id=?", arrayOf(id.toString()))
            updateCacheFromDb(id)
        }
    }

    suspend fun markRemoved(notificationKey: String, removedBySource: Boolean = false) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val db = helper.writableDatabase
            val existing = findByKey(db, notificationKey) ?: return@withLock
            val values = ContentValues().apply {
                put("is_active", 0)
                put("removed_at", System.currentTimeMillis())
                put("updated_at", System.currentTimeMillis())
                if (removedBySource) put("is_removed_by_source", 1)
            }
            db.update(TABLE, values, "id=?", arrayOf(existing.id.toString()))
            updateCacheFromDb(existing.id)
        }
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
        mutationMutex.withLock {
            helper.writableDatabase.delete(TABLE_EXPENSES, "notification_id=?", arrayOf(id.toString()))
            helper.writableDatabase.delete(TABLE, "id=?", arrayOf(id.toString()))
            _items.value = _items.value.filterNot { it.id == id }
            _expenses.value = _expenses.value.filterNot { it.notificationId == id }
        }
    }

    suspend fun bulkSetState(ids: Collection<Long>, state: NotificationState) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        mutationMutex.withLock {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("state", state.name)
                if (state != NotificationState.LATER) putNull("remind_at")
                put("updated_at", System.currentTimeMillis())
            }
            db.beginTransaction()
            try {
                ids.distinct().forEach { id ->
                    db.update(TABLE, values, "id=?", arrayOf(id.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshFromDb()
        }
    }

    suspend fun bulkSetReminder(ids: Collection<Long>, remindAt: Long) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        mutationMutex.withLock {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("state", NotificationState.LATER.name)
                put("remind_at", remindAt)
                put("updated_at", System.currentTimeMillis())
            }
            db.beginTransaction()
            try {
                ids.distinct().forEach { id ->
                    db.update(TABLE, values, "id=?", arrayOf(id.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshFromDb()
        }
    }

    suspend fun bulkDelete(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        mutationMutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                ids.distinct().forEach { id ->
                    db.delete(TABLE_EXPENSES, "notification_id=?", arrayOf(id.toString()))
                    db.delete(TABLE, "id=?", arrayOf(id.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshFromDb()
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            helper.writableDatabase.delete(TABLE_EXPENSES, null, null)
            helper.writableDatabase.delete(TABLE, null, null)
            _items.value = emptyList()
            _expenses.value = emptyList()
        }
    }

    // ----- Saved media (photos/videos copied from WhatsApp's shared storage) -----

    suspend fun savedMediaCount(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_SAVED_MEDIA", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    suspend fun hasSavedMediaForUri(originalUri: String): Boolean = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_SAVED_MEDIA WHERE original_uri=? LIMIT 1",
            arrayOf(originalUri)
        ).use { it.moveToFirst() }
    }

    suspend fun hasSavedMediaForHash(contentHash: String): Boolean = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_SAVED_MEDIA WHERE content_hash=? LIMIT 1",
            arrayOf(contentHash)
        ).use { it.moveToFirst() }
    }

    suspend fun insertSavedMedia(media: SavedMedia): Long = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            helper.writableDatabase.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_SAVED_MEDIA WHERE content_hash=?",
                arrayOf(media.contentHash)
            ).use { if (it.moveToFirst() && it.getInt(0) > 0) return@withLock -1L }

            val db = helper.writableDatabase
            db.beginTransaction()
            val rowId = try {
                val values = ContentValues().apply {
                    put("package_name", media.packageName)
                    put("display_name", media.displayName)
                    put("kind", media.kind.name)
                    put("mime_type", media.mimeType)
                    put("size_bytes", media.sizeBytes)
                    put("content_hash", media.contentHash)
                    put("saved_path", media.savedPath)
                    put("original_uri", media.originalUri)
                    put("captured_at", media.capturedAt)
                    put("saved_at", media.savedAt)
                    if (media.notificationId != null) {
                        put("notification_id", media.notificationId)
                    } else {
                        putNull("notification_id")
                    }
                }
                db.insertWithOnConflict(TABLE_SAVED_MEDIA, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            } finally {
                db.endTransaction()
            }
            refreshSavedMediaFromDb()
            rowId
        }
    }

    suspend fun savedMediaItems(): List<SavedMedia> = withContext(Dispatchers.IO) {
        querySavedMedia()
    }

    suspend fun savedMediaStats(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(size_bytes),0) FROM $TABLE_SAVED_MEDIA",
            null
        ).use {
            if (it.moveToFirst()) it.getInt(0) to it.getLong(1) else 0 to 0L
        }
    }

    suspend fun findNotificationIdForMedia(packageName: String, capturedAtMillis: Long): Long? =
        withContext(Dispatchers.IO) {
            val window = 24 * 60 * 60_000L
            helper.readableDatabase.rawQuery(
                """
                SELECT id FROM $TABLE
                WHERE package_name=?
                  AND posted_at BETWEEN ? AND ?
                ORDER BY ABS(posted_at - ?) ASC
                LIMIT 1
                """.trimIndent(),
                arrayOf(
                    packageName,
                    (capturedAtMillis - window).toString(),
                    (capturedAtMillis + window).toString(),
                    capturedAtMillis.toString()
                )
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }

    suspend fun deleteSavedMedia(id: Long) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            helper.writableDatabase.delete(TABLE_SAVED_MEDIA, "id=?", arrayOf(id.toString()))
            refreshSavedMediaFromDb()
        }
    }

    suspend fun clearedSavedMediaRows(): List<SavedMedia> = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val rows = querySavedMedia()
            helper.writableDatabase.delete(TABLE_SAVED_MEDIA, null, null)
            refreshSavedMediaFromDb()
            rows
        }
    }

    private fun refreshSavedMediaFromDb() {
        _savedMedia.value = querySavedMedia()
    }

    private fun querySavedMedia(): List<SavedMedia> =
        helper.readableDatabase.query(
            TABLE_SAVED_MEDIA,
            null,
            null,
            null,
            null,
            null,
            "captured_at DESC"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toSavedMedia()) }
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

    suspend fun itemsSince(timestamp: Long): List<NotificationItem> =
        itemsBetween(timestamp, System.currentTimeMillis())

    suspend fun allItems(): List<NotificationItem> = withContext(Dispatchers.IO) {
        query()
    }

    suspend fun itemsBetween(
        sinceExclusive: Long,
        untilInclusive: Long,
        pageSize: Int = 500
    ): List<NotificationItem> = withContext(Dispatchers.IO) {
        require(pageSize > 0) { "pageSize must be positive" }
        val db = helper.readableDatabase
        val result = mutableListOf<NotificationItem>()
        var beforePostedAt: Long? = null
        var beforeId: Long? = null
        while (true) {
            val selection: String
            val args: Array<String>
            if (beforePostedAt == null || beforeId == null) {
                selection = "posted_at>? AND posted_at<=?"
                args = arrayOf(sinceExclusive.toString(), untilInclusive.toString())
            } else {
                selection = "posted_at>? AND posted_at<=? AND (posted_at<? OR (posted_at=? AND id<?))"
                args = arrayOf(sinceExclusive.toString(), untilInclusive.toString(), beforePostedAt.toString(), beforePostedAt.toString(), beforeId.toString())
            }
            val page = db.query(TABLE, null, selection, args, null, null, "posted_at DESC, id DESC", pageSize.toString()).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.toItem(null)) }
            }
            if (page.isEmpty()) break
            result += page
            val last = page.last()
            beforePostedAt = last.postedAt
            beforeId = last.id
            if (page.size < pageSize) break
        }
        result
    }

    suspend fun cleanup(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val now = System.currentTimeMillis()
            val retentionCutoff = now - retentionDays * 86_400_000L
            var count = helper.writableDatabase.delete(TABLE, "posted_at<? AND pinned=0 AND remind_at IS NULL AND is_active=0", arrayOf(retentionCutoff.toString()))
            val otpCutoff = now - 2 * 86_400_000L
            count += helper.writableDatabase.delete(TABLE, "posted_at<? AND pinned=0 AND remind_at IS NULL AND is_active=0 AND category=?", arrayOf(otpCutoff.toString(), NotificationCategory.OTP.name))
            val lowValueCutoff = now - minOf(retentionDays, 14) * 86_400_000L
            count += helper.writableDatabase.delete(TABLE, "posted_at<? AND pinned=0 AND remind_at IS NULL AND is_active=0 AND category IN (?,?)", arrayOf(lowValueCutoff.toString(), NotificationCategory.PROMOTION.name, NotificationCategory.SPAM.name))
            val deliveryCutoff = now - minOf(retentionDays, 21) * 86_400_000L
            count += helper.writableDatabase.delete(TABLE, "posted_at<? AND pinned=0 AND remind_at IS NULL AND category=? AND is_active=0", arrayOf(deliveryCutoff.toString(), NotificationCategory.DELIVERY.name))
            refreshFromDb()
            count
        }
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
            "package_name = ? AND posted_at >= ? AND body IS NOT NULL AND body != '' AND is_removed_by_source = 0"
        } else {
            "package_name = ? AND (sender = ? OR title = ?) AND posted_at >= ? AND body IS NOT NULL AND body != '' AND is_removed_by_source = 0"
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

    suspend fun importNotifications(items: List<NotificationItem>): Int = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val db = helper.writableDatabase
            var imported = 0
            db.beginTransaction()
            try {
                items.forEach { item ->
                    val values = ContentValues().apply {
                        put("notification_key", item.notificationKey)
                        put("package_name", item.packageName)
                        put("app_name", item.appName)
                        putNullable("title", item.title)
                        putNullable("body", item.body)
                        putNullable("sender", item.sender)
                        put("posted_at", item.postedAt)
                        put("updated_at", item.updatedAt)
                        item.removedAt?.let { put("removed_at", it) } ?: putNull("removed_at")
                        put("is_active", if (item.isActiveOnSystem) 1 else 0)
                        put("category", item.category.name)
                        put("priority", item.priority.name)
                        put("confidence", item.confidence)
                        put("category_overridden", if (item.categoryOverridden) 1 else 0)
                        put("priority_overridden", if (item.priorityOverridden) 1 else 0)
                        put("state", item.state.name)
                        item.remindAt?.let { put("remind_at", it) } ?: putNull("remind_at")
                        put("pinned", if (item.pinned) 1 else 0)
                        put("is_vip", if (item.isVip) 1 else 0)
                        put("read", if (item.read) 1 else 0)
                        item.amountHint?.let { put("amount_hint", it) } ?: putNull("amount_hint")
                        put("is_removed_by_source", if (item.isRemovedBySource) 1 else 0)
                    }
                    if (db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                        imported++
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshFromDb()
            imported
        }
    }

    suspend fun deleteExpenseByNotificationId(notificationId: Long) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            helper.writableDatabase.delete(TABLE_EXPENSES, "notification_id=?", arrayOf(notificationId.toString()))
            refreshFromDb()
        }
    }

    suspend fun getExpenseSummary(since: Long = 0L, until: Long = Long.MAX_VALUE): ExpenseSummary =
        withContext(Dispatchers.IO) {
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

            ExpenseSummary(
                totalDebit = totalDebit,
                totalCredit = totalCredit,
                netBalanceDiff = totalCredit - totalDebit,
                debitCount = debitCount,
                creditCount = creditCount,
                categoryBreakdown = categoryMap,
                transactions = list
            )
        }

    suspend fun synchronizeVipFlags(rules: Set<String>) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                db.query(TABLE, arrayOf("id", "package_name", "sender", "is_vip"), null, null, null, null, null).use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val packageIndex = cursor.getColumnIndexOrThrow("package_name")
                    val senderIndex = cursor.getColumnIndexOrThrow("sender")
                    val vipIndex = cursor.getColumnIndexOrThrow("is_vip")
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val packageName = cursor.getString(packageIndex)
                        val sender = if (cursor.isNull(senderIndex)) null else cursor.getString(senderIndex)
                        val normalizedSender = sender.orEmpty().trim().lowercase()
                        val shouldBeVip = "$packageName|" in rules || "$packageName|$normalizedSender" in rules
                        val isVip = cursor.getInt(vipIndex) == 1
                        if (isVip != shouldBeVip) {
                            val values = ContentValues().apply {
                                put("is_vip", if (shouldBeVip) 1 else 0)
                                put("updated_at", System.currentTimeMillis())
                            }
                            db.update(TABLE, values, "id=?", arrayOf(id.toString()))
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            refreshFromDb()
        }
    }

    private suspend fun update(id: Long, fill: ContentValues.() -> Unit) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val values = ContentValues().apply {
                fill()
                put("updated_at", System.currentTimeMillis())
            }
            helper.writableDatabase.update(TABLE, values, "id=?", arrayOf(id.toString()))
            updateCacheFromDb(id)
        }
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
        limit: Int? = null
    ): List<ExpenseTransaction> {
        return helper.readableDatabase.query(TABLE_EXPENSES, null, selection, args, null, null, order, limit?.toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toExpense())
            }
        }
    }

    private fun query(
        selection: String? = null,
        args: Array<String>? = null,
        order: String = "pinned DESC, posted_at DESC",
        limit: Int? = null
    ): List<NotificationItem> {
        val allExpenses = queryExpenses().associateBy { it.notificationId }
        return helper.readableDatabase.query(TABLE, null, selection, args, null, null, order, limit?.toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    add(cursor.toItem(allExpenses[id]))
                }
            }
        }
    }

    private fun refreshFromDb() {
        val expensesList = queryExpenses()
        _expenses.value = expensesList
        _items.value = query()
    }

    private fun updateCacheFromDb(id: Long): NotificationItem? {
        val item = getInternal(helper.readableDatabase, id) ?: run {
            _items.value = _items.value.filterNot { it.id == id }
            _expenses.value = _expenses.value.filterNot { it.notificationId == id }
            return null
        }
        _items.value = (_items.value.filterNot { it.id == id } + item)
            .sortedWith(compareByDescending<NotificationItem> { it.pinned }.thenByDescending { it.postedAt })
        val expense = item.expenseTransaction
        _expenses.value = if (expense == null) {
            _expenses.value.filterNot { it.notificationId == id }
        } else {
            (_expenses.value.filterNot { it.notificationId == id } + expense)
                .sortedByDescending { it.timestamp }
        }
        return item
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
        isRemovedBySource = getIntOrZero("is_removed_by_source") == 1,
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

    private fun Cursor.toSavedMedia(): SavedMedia {
        val id = getLong(col("id"))
        val kind = enumOrDefault(getNullableString("kind"), SavedMediaKind.IMAGE)
        return SavedMedia(
            id = id,
            packageName = getNullableString("package_name").orEmpty(),
            displayName = getNullableString("display_name").orEmpty(),
            kind = kind,
            mimeType = getNullableString("mime_type"),
            sizeBytes = getLongOrNull("size_bytes") ?: 0L,
            contentHash = getNullableString("content_hash").orEmpty(),
            savedPath = getNullableString("saved_path").orEmpty(),
            originalUri = getNullableString("original_uri").orEmpty(),
            capturedAt = getLongOrNull("captured_at") ?: 0L,
            savedAt = getLongOrNull("saved_at") ?: 0L,
            notificationId = getNullableLong("notification_id")
        )
    }

    private fun Cursor.getLongOrNull(name: String): Long? =
        getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

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
                    is_removed_by_source INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_notifications_posted ON $TABLE(posted_at DESC)")
            db.execSQL("CREATE INDEX idx_notifications_package ON $TABLE(package_name)")
            db.execSQL("CREATE INDEX idx_notifications_state ON $TABLE(state)")
            db.execSQL("CREATE INDEX idx_notifications_category ON $TABLE(category)")
            db.execSQL("CREATE INDEX idx_notifications_source_removed ON $TABLE(is_removed_by_source)")

            createExpenseTable(db)
            createSavedMediaTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN is_deleted_by_sender INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_deleted ON $TABLE(is_deleted_by_sender)")
            }
            if (oldVersion < 3) {
                createExpenseTable(db)
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN is_removed_by_source INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE $TABLE SET is_removed_by_source = is_deleted_by_sender WHERE is_deleted_by_sender = 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_source_removed ON $TABLE(is_removed_by_source)")
                db.execSQL("DROP INDEX IF EXISTS idx_notifications_deleted")
            }
            if (oldVersion < 5) {
                createSavedMediaTable(db)
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

        private fun createSavedMediaTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SAVED_MEDIA (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    package_name TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    mime_type TEXT,
                    size_bytes INTEGER NOT NULL DEFAULT 0,
                    content_hash TEXT NOT NULL UNIQUE,
                    saved_path TEXT NOT NULL,
                    original_uri TEXT NOT NULL UNIQUE,
                    captured_at INTEGER NOT NULL,
                    saved_at INTEGER NOT NULL,
                    notification_id INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_media_captured ON $TABLE_SAVED_MEDIA(captured_at DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_media_package ON $TABLE_SAVED_MEDIA(package_name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved_media_notification ON $TABLE_SAVED_MEDIA(notification_id)")
        }


    }

    private companion object {
        const val DB_NAME = "notiflow.db"
        const val DB_VERSION = 5
        const val TABLE = "notifications"
        const val TABLE_EXPENSES = "expense_transactions"
        const val TABLE_SAVED_MEDIA = "saved_media"
    }
}

