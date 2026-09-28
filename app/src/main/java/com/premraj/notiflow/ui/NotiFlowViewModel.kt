package com.premraj.notiflow.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.AppThemeMode
import com.premraj.notiflow.data.ExpenseCategory
import com.premraj.notiflow.data.ExpenseSummary
import com.premraj.notiflow.data.ExpenseTimeRange
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.FocusProfileType
import com.premraj.notiflow.data.FocusStatus
import com.premraj.notiflow.data.InsightsTimeRange
import com.premraj.notiflow.data.LearnedPreference
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationInsights
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.data.TransactionType
import com.premraj.notiflow.data.VoiceReaderFilter
import com.premraj.notiflow.data.VoiceReadingDetail
import com.premraj.notiflow.data.VoiceTriggerCondition
import com.premraj.notiflow.intelligence.InsightsAnalyzer
import com.premraj.notiflow.intelligence.LocalIntelligence
import com.premraj.notiflow.intelligence.ModelDownloadStatus
import com.premraj.notiflow.util.BackupExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class NotiFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.appGraph
    val notifications: StateFlow<List<NotificationItem>> = graph.store.items
    val expenseTransactions: StateFlow<List<ExpenseTransaction>> = graph.store.expenses
    val initialLoadComplete: StateFlow<Boolean> = graph.store.initialLoadComplete

    private val _preferencesVersion = MutableStateFlow(0)
    val preferencesVersion: StateFlow<Int> = _preferencesVersion.asStateFlow()

    private val _observedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val observedApps: StateFlow<List<Pair<String, String>>> = _observedApps.asStateFlow()

    private val _storageStats = MutableStateFlow(0 to 0L)
    val storageStats: StateFlow<Pair<Int, Long>> = _storageStats.asStateFlow()

    private val _unwantedCount = MutableStateFlow(0)
    val unwantedCount: StateFlow<Int> = _unwantedCount.asStateFlow()

    val modelDownloadStatus: StateFlow<ModelDownloadStatus> = graph.modelDownloader.status
    val modelDownloadProgress: StateFlow<Float> = graph.modelDownloader.progress
    val isHeadphonesConnected: StateFlow<Boolean> = graph.voiceReader.isHeadphonesConnected

    val preferences get() = graph.preferences
    val modelAvailable get() = graph.gemma.isModelAvailable

    init {
        refreshObservedApps()
        refreshStorageStats()
        refreshUnwantedCount()
        viewModelScope.launch {
            graph.store.synchronizeVipFlags(graph.preferences.vipRules())
        }
        viewModelScope.launch {
            notifications.collect {
                refreshUnwantedCount()
            }
        }
    }

    fun completeOnboarding() {
        graph.preferences.onboardingComplete = true
        bumpPrefs()
    }

    fun setState(id: Long, state: NotificationState) = viewModelScope.launch {
        graph.store.setState(id, state)
        if (state != NotificationState.LATER) graph.workScheduler.cancelReminder(id)
        refreshStorageStats()
    }

    fun setReminder(id: Long, atMillis: Long?) = viewModelScope.launch {
        graph.store.setReminder(id, atMillis)
        if (atMillis == null) graph.workScheduler.cancelReminder(id)
        else graph.workScheduler.scheduleReminder(id, atMillis)
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

    fun clearAllHistory() = viewModelScope.launch {
        notifications.value.asSequence()
            .filter { it.remindAt != null }
            .forEach { graph.workScheduler.cancelReminder(it.id) }
        graph.workScheduler.cancelAllReminders()
        graph.store.clearAll()
        refreshStorageStats()
    }

    fun clearHistory() = clearAllHistory()

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

    fun setAntiRevokeEnabled(enabled: Boolean) {
        graph.preferences.antiRevokeEnabled = enabled
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

    fun setUnwantedPromptEnabled(enabled: Boolean) {
        graph.preferences.unwantedCleanupPromptEnabled = enabled
        bumpPrefs()
    }

    fun deleteUnwantedMessages() = viewModelScope.launch {
        val days = graph.preferences.unwantedCleanupDays
        graph.store.deleteUnwantedOldMessages(days)
        refreshStorageStats()
        refreshUnwantedCount()
    }

    fun refreshUnwantedCount() = viewModelScope.launch {
        val days = graph.preferences.unwantedCleanupDays
        _unwantedCount.value = graph.store.countUnwantedOldMessages(days)
    }

    fun setLocalAiEnabled(enabled: Boolean) {
        graph.preferences.localAiEnabled = enabled
        if (!enabled) viewModelScope.launch { graph.gemma.reset() }
        bumpPrefs()
    }

    fun retryModelDownload() {
        graph.modelDownloader.autoStartDownloadIfNeeded()
    }

    fun setVoiceReaderEnabled(enabled: Boolean) {
        graph.preferences.voiceReaderEnabled = enabled
        bumpPrefs()
    }

    fun setDrivingModeActive(active: Boolean) {
        graph.preferences.drivingModeActive = active
        bumpPrefs()
    }

    fun setVoiceTriggerCondition(condition: VoiceTriggerCondition) {
        graph.preferences.voiceTriggerCondition = condition
        bumpPrefs()
    }

    fun setVoiceReaderFilter(filter: VoiceReaderFilter) {
        graph.preferences.voiceReaderFilter = filter
        bumpPrefs()
    }

    fun setVoiceReadingDetail(detail: VoiceReadingDetail) {
        graph.preferences.voiceReadingDetail = detail
        bumpPrefs()
    }

    fun setVoiceSpeechRate(rate: Float) {
        graph.preferences.voiceSpeechRate = rate
        bumpPrefs()
    }

    fun testVoiceAnnouncement() {
        graph.voiceReader.testAnnouncement()
    }

    fun getFocusStatus(): FocusStatus = graph.focusEngine.currentStatus()

    fun toggleManualFocus(type: FocusProfileType = FocusProfileType.WORK) {
        graph.focusEngine.toggleManualFocus(type)
        bumpPrefs()
    }

    fun stopActiveFocus() {
        graph.focusEngine.stopActiveFocus()
        bumpPrefs()
    }

    fun setSleepFocusScheduled(scheduled: Boolean) {
        graph.preferences.sleepFocusScheduled = scheduled
        bumpPrefs()
    }

    fun setWorkFocusScheduled(scheduled: Boolean) {
        graph.preferences.workFocusScheduled = scheduled
        bumpPrefs()
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        graph.preferences.appThemeMode = mode
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

