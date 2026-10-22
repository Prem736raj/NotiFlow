package com.premraj.notiflow.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.AppThemeMode
import com.premraj.notiflow.data.AutomationAction
import com.premraj.notiflow.data.AutomationRule
import com.premraj.notiflow.data.ExpenseCategory
import com.premraj.notiflow.data.ExpenseSummary
import com.premraj.notiflow.data.ExpenseTimeRange
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.InsightsTimeRange
import com.premraj.notiflow.data.LearnedPreference
import com.premraj.notiflow.data.MetricsTracker
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationInsights
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.data.TransactionType
import com.premraj.notiflow.intelligence.InsightsAnalyzer
import com.premraj.notiflow.intelligence.LocalIntelligence
import com.premraj.notiflow.util.BackupExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

data class RestoreSummary(
    val notificationsImported: Int,
    val vipRulesImported: Int,
    val automationRulesImported: Int = 0
)

class NotiFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.appGraph
    val notifications: StateFlow<List<NotificationItem>> = graph.store.items
    val expenseTransactions: StateFlow<List<ExpenseTransaction>> = graph.store.expenses
    val initialLoadComplete: StateFlow<Boolean> = graph.store.initialLoadComplete

    private val _preferencesVersion = MutableStateFlow(0)
    val preferencesVersion: StateFlow<Int> = _preferencesVersion.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    private val _observedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val observedApps: StateFlow<List<Pair<String, String>>> = _observedApps.asStateFlow()

    private val _storageStats = MutableStateFlow(0 to 0L)
    val storageStats: StateFlow<Pair<Int, Long>> = _storageStats.asStateFlow()

    val preferences get() = graph.preferences

    init {
        refreshObservedApps()
        refreshStorageStats()
        viewModelScope.launch {
            graph.store.synchronizeVipFlags(graph.preferences.vipRules())
        }
    }

    fun completeOnboarding() {
        graph.preferences.onboardingComplete = true
        bumpPrefs()
    }

    fun setState(id: Long, state: NotificationState) = viewModelScope.launch {
        graph.store.setState(id, state)
        if (state == NotificationState.LATER) {
            MetricsTracker.recordEvent(getApplication(), MetricsTracker.EVENT_NOTIFICATION_MARKED_LATER)
        } else {
            graph.workScheduler.cancelReminder(id)
        }
        refreshStorageStats()
    }

    fun setReminder(id: Long, atMillis: Long?) = viewModelScope.launch {
        graph.store.setReminder(id, atMillis)
        if (atMillis == null) {
            graph.workScheduler.cancelReminder(id)
        } else {
            graph.workScheduler.scheduleReminder(id, atMillis)
            MetricsTracker.recordEvent(getApplication(), MetricsTracker.EVENT_REMINDER_SCHEDULED)
        }
    }

    fun setCategory(item: NotificationItem, category: NotificationCategory) = viewModelScope.launch {
        graph.store.setCategory(item.id, category)
        graph.preferences.upsertLearnedPreference(
            LearnedPreference(item.packageName, item.sender, category = category)
        )
        bumpPrefs()
    }

    fun setPriority(item: NotificationItem, priority: NotificationPriority) = viewModelScope.launch {
        graph.store.setPriority(item.id, priority)
        graph.preferences.upsertLearnedPreference(
            LearnedPreference(item.packageName, item.sender, priority = priority)
        )
        bumpPrefs()
    }

    fun setPinned(item: NotificationItem, pinned: Boolean) = viewModelScope.launch {
        graph.store.setPinned(item.id, pinned)
    }

    fun markRead(id: Long) = viewModelScope.launch { graph.store.setRead(id, true) }

    fun setVip(item: NotificationItem, enabled: Boolean) = viewModelScope.launch {
        graph.preferences.setVip(item.packageName, item.sender, enabled)
        graph.store.synchronizeVipFlags(graph.preferences.vipRules())
        if (!enabled && !item.priorityOverridden) {
            val learnedPriority = graph.preferences.matchingPreference(item.packageName, item.sender)?.priority
            val basePriority = learnedPriority ?: LocalIntelligence.classify(
                packageName = item.packageName,
                title = item.title,
                body = item.body,
                sender = item.sender
            ).priority
            graph.store.setPriority(item.id, basePriority, userOverride = false)
        }
        bumpPrefs()
    }

    fun delete(id: Long) = viewModelScope.launch {
        graph.workScheduler.cancelReminder(id)
        graph.store.delete(id)
        refreshStorageStats()
    }

    fun bulkSetState(ids: Set<Long>, state: NotificationState) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        graph.store.bulkSetState(ids, state)
        if (state != NotificationState.LATER) {
            ids.forEach { graph.workScheduler.cancelReminder(it) }
        }
        refreshStorageStats()
    }

    fun bulkSnooze(ids: Set<Long>, triggerAtMillis: Long) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        graph.store.bulkSetReminder(ids, triggerAtMillis)
        ids.forEach { graph.workScheduler.scheduleReminder(it, triggerAtMillis) }
        MetricsTracker.recordEvent(getApplication(), MetricsTracker.EVENT_REMINDER_SCHEDULED)
        refreshStorageStats()
    }

    fun bulkDelete(ids: Set<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        ids.forEach { graph.workScheduler.cancelReminder(it) }
        graph.store.bulkDelete(ids)
        refreshStorageStats()
    }

    fun clearAllHistory() = viewModelScope.launch {
        notifications.value.asSequence()
            .filter { it.remindAt != null }
            .forEach { graph.workScheduler.cancelReminder(it.id) }
        graph.workScheduler.cancelAllReminders()
        graph.store.clearAll()
        refreshStorageStats()
    }

    fun clearHistory() = clearAllHistory()

    fun removeExpenseForNotification(notificationId: Long) = viewModelScope.launch {
        graph.store.deleteExpenseByNotificationId(notificationId)
    }

    fun setDigestEnabled(enabled: Boolean) {
        graph.preferences.digestEnabled = enabled
        if (enabled) {
            graph.workScheduler.scheduleDigest(updateExisting = true)
        } else {
            graph.workScheduler.cancelDigest()
        }
        bumpPrefs()
    }

    fun setDigestTime(hour: Int, minute: Int) {
        graph.preferences.digestHour = hour
        graph.preferences.digestMinute = minute
        if (graph.preferences.digestEnabled) {
            graph.workScheduler.scheduleDigest(updateExisting = true)
        }
        bumpPrefs()
    }

    fun setAutoCleanup(enabled: Boolean) {
        graph.preferences.autoCleanupEnabled = enabled
        bumpPrefs()
    }

    fun setRetentionDays(days: Int) {
        graph.preferences.retentionDays = days
        bumpPrefs()
    }

    fun runCleanupNow() = viewModelScope.launch {
        graph.store.cleanup(graph.preferences.retentionDays)
        refreshStorageStats()
    }

    fun setHideSensitivePreviews(enabled: Boolean) {
        graph.preferences.hideSensitivePreviews = enabled
        bumpPrefs()
    }

    fun setAutoCopyOtp(enabled: Boolean) {
        graph.preferences.autoCopyOtp = enabled
        bumpPrefs()
    }

    fun setExpenseTrackerEnabled(enabled: Boolean) {
        graph.preferences.expenseTrackerEnabled = enabled
        bumpPrefs()
    }

    suspend fun getExpenseSummary(range: ExpenseTimeRange): ExpenseSummary {
        val cal = Calendar.getInstance()
        val (since, until) = when (range) {
            ExpenseTimeRange.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to Long.MAX_VALUE
            }
            ExpenseTimeRange.THIS_WEEK -> {
                val now = System.currentTimeMillis()
                (now - 7 * 86_400_000L) to Long.MAX_VALUE
            }
            ExpenseTimeRange.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to Long.MAX_VALUE
            }
            ExpenseTimeRange.ALL_TIME -> 0L to Long.MAX_VALUE
        }
        return graph.store.getExpenseSummary(since, until)
    }

    fun computeInsights(range: InsightsTimeRange): NotificationInsights {
        return InsightsAnalyzer.computeInsights(notifications.value, range)
    }

    fun setQuietPackage(packageName: String, quiet: Boolean) {
        graph.preferences.setQuietPackage(packageName, quiet)
        bumpPrefs()
    }

    fun setQuietCategory(category: NotificationCategory, quiet: Boolean) {
        graph.preferences.setQuietCategory(category, quiet)
        bumpPrefs()
    }

    fun setVipApp(packageName: String, enabled: Boolean) = viewModelScope.launch {
        graph.preferences.setVip(packageName, null, enabled)
        graph.store.synchronizeVipFlags(graph.preferences.vipRules())
        bumpPrefs()
    }

    fun removeVipRule(token: String) = viewModelScope.launch {
        val pkg = token.substringBefore('|')
        val sender = token.substringAfter('|').takeIf { it.isNotBlank() }
        graph.preferences.setVip(pkg, sender, false)
        graph.store.synchronizeVipFlags(graph.preferences.vipRules())
        bumpPrefs()
    }

    fun addAutomationRule(keyword: String, packageName: String?, action: AutomationAction) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        graph.preferences.addAutomationRule(
            AutomationRule(
                id = UUID.randomUUID().toString(),
                keyword = normalized,
                packageName = packageName?.takeIf { it.isNotBlank() },
                action = action
            )
        )
        bumpPrefs()
    }

    fun setAutomationRuleEnabled(id: String, enabled: Boolean) {
        graph.preferences.setAutomationRuleEnabled(id, enabled)
        bumpPrefs()
    }

    fun removeAutomationRule(id: String) {
        graph.preferences.removeAutomationRule(id)
        bumpPrefs()
    }

    fun removeLearnedPreference(token: String) {
        val pkg = token.substringBefore('|')
        val sender = token.substringAfter('|').takeIf { it.isNotBlank() }
        graph.preferences.removeLearnedPreference(pkg, sender)
        bumpPrefs()
    }

    fun clearLearnedPreferences() {
        graph.preferences.clearLearnedPreferences()
        bumpPrefs()
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        graph.preferences.appThemeMode = mode
        bumpPrefs()
    }

    fun setUseDynamicColors(enabled: Boolean) {
        graph.preferences.useDynamicColors = enabled
        bumpPrefs()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        graph.preferences.appLockEnabled = enabled
        bumpPrefs()
    }

    suspend fun createEncryptedArchive(password: String): String {
        val allNotifications = graph.store.allItems()
        return withContext(Dispatchers.Default) {
            BackupExporter.createEncryptedBackup(
                notifications = allNotifications,
                preferences = graph.preferences,
                password = password
            )
        }
    }

    suspend fun createEncryptedArchiveFile(context: Context, password: String): File {
        val allNotifications = graph.store.allItems()
        return withContext(Dispatchers.IO) {
            BackupExporter.createEncryptedBackupFile(
                context = context,
                notifications = allNotifications,
                preferences = graph.preferences,
                password = password
            )
        }
    }

    suspend fun restoreEncryptedArchive(context: Context, uri: Uri, password: String): RestoreSummary =
        withContext(Dispatchers.IO) {
            val payload = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw BackupExporter.CorruptArchiveException("Could not read the selected archive")

            val root = BackupExporter.decryptBackup(payload, password).getOrThrow()
            val notifications = BackupExporter.notificationItemsFromArchive(root)
            val imported = graph.store.importNotifications(notifications)

            val before = graph.preferences.vipRules()
            val archiveRules = BackupExporter.vipRulesFromArchive(root)
            archiveRules.forEach { token ->
                val packageName = token.substringBefore('|')
                if (packageName.isNotBlank()) {
                    val sender = token.substringAfter('|', "").takeIf { it.isNotBlank() }
                    graph.preferences.setVip(packageName, sender, true)
                }
            }

            val rulesBefore = graph.preferences.automationRules().mapTo(mutableSetOf()) { it.id }
            val restoredRules = BackupExporter.automationRulesFromArchive(root)
            graph.preferences.addAutomationRules(restoredRules)

            _observedApps.value = graph.store.observedApps()
            _storageStats.value = graph.store.storageStats()
            bumpPrefs()
            RestoreSummary(
                notificationsImported = imported,
                vipRulesImported = (graph.preferences.vipRules() - before).size,
                automationRulesImported = restoredRules.count { it.id !in rulesBefore }
            )
        }

    fun refreshObservedApps() = viewModelScope.launch {
        _observedApps.value = graph.store.observedApps()
    }

    fun refreshStorageStats() = viewModelScope.launch {
        _storageStats.value = graph.store.storageStats()
    }

    private fun bumpPrefs() {
        _preferencesVersion.value += 1
        refreshObservedApps()
    }
}

