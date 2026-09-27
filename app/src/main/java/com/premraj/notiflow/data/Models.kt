package com.premraj.notiflow.data

enum class NotificationCategory(val label: String) {
    OTP("OTP / verification"),
    PAYMENT("Payment / banking"),
    DELIVERY("Delivery / order"),
    MESSAGE("Direct message"),
    WORK_STUDY("Work / study"),
    REMINDER_EVENT("Reminder / event"),
    SOCIAL("Social"),
    PROMOTION("Promotion"),
    SPAM("Spam / low value"),
    OTHER("Other")
}

enum class NotificationPriority(val label: String) {
    HIGH("High"),
    NORMAL("Normal"),
    LOW("Low")
}

enum class NotificationState {
    ACTIVE,
    LATER,
    DONE,
    ARCHIVED
}

enum class TransactionType(val label: String) {
    DEBIT("Debited"),
    CREDIT("Credited")
}

enum class ExpenseCategory(val label: String, val emoji: String) {
    FOOD("Food & Dining", "🍔"),
    SHOPPING("Shopping", "🛍️"),
    GROCERY("Groceries", "🥦"),
    TRAVEL("Travel & Commute", "🚗"),
    BILLS("Bills & Utilities", "💡"),
    INVESTMENT("Investments", "📈"),
    TRANSFER("Transfer & Personal", "💸"),
    HEALTH("Health & Medical", "💊"),
    ENTERTAINMENT("Entertainment", "🎬"),
    GENERAL("General Expense", "💳")
}

data class ExpenseTransaction(
    val id: Long = 0,
    val notificationId: Long,
    val amount: Double,
    val transactionType: TransactionType,
    val merchantOrParty: String?,
    val accountRef: String?,
    val balanceAfter: Double?,
    val expenseCategory: ExpenseCategory,
    val timestamp: Long
)

data class ExpenseSummary(
    val totalDebit: Double,
    val totalCredit: Double,
    val netBalanceDiff: Double,
    val debitCount: Int,
    val creditCount: Int,
    val categoryBreakdown: Map<ExpenseCategory, Double>,
    val transactions: List<ExpenseTransaction>
)

data class NotificationItem(
    val id: Long,
    val notificationKey: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val body: String?,
    val sender: String?,
    val postedAt: Long,
    val updatedAt: Long,
    val removedAt: Long?,
    val isActiveOnSystem: Boolean,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val confidence: Float,
    val categoryOverridden: Boolean,
    val priorityOverridden: Boolean,
    val state: NotificationState,
    val remindAt: Long?,
    val pinned: Boolean,
    val isVip: Boolean,
    val read: Boolean,
    val amountHint: Double?,
    val isDeletedBySender: Boolean = false,
    val expenseTransaction: ExpenseTransaction? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: sender?.takeIf { it.isNotBlank() } ?: appName

    val displayBody: String
        get() = body?.takeIf { it.isNotBlank() } ?: "No preview available"
}

data class IncomingNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val body: String?,
    val sender: String?,
    val postedAt: Long
)

data class ClassificationResult(
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val confidence: Float,
    val amountHint: Double? = null,
    val reason: String = ""
)

data class LearnedPreference(
    val packageName: String,
    val sender: String?,
    val category: NotificationCategory? = null,
    val priority: NotificationPriority? = null
) {
    val key: String get() = "$packageName|${sender.orEmpty().trim().lowercase()}"
}

data class DigestSnapshot(
    val since: Long,
    val until: Long,
    val total: Int,
    val important: List<NotificationItem>,
    val groupedCounts: Map<NotificationCategory, Int>,
    val items: List<NotificationItem>
)

