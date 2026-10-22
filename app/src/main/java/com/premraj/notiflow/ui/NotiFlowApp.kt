package com.premraj.notiflow.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.premraj.notiflow.data.HomeSection
import com.premraj.notiflow.data.MetricsTracker
import com.premraj.notiflow.service.NotiFlowNotificationListener

private enum class AppScreen { HOME, SEARCH, SETTINGS, DETAIL }

@Composable
fun NotiFlowApp(
    viewModel: NotiFlowViewModel,
    requestedNotificationId: Long?,
    requestedDigest: Boolean,
    onNavigationRequestConsumed: () -> Unit
) {
    val context = LocalContext.current
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val expenses by viewModel.expenseTransactions.collectAsStateWithLifecycle()
    val initialLoadComplete by viewModel.initialLoadComplete.collectAsStateWithLifecycle()
    val prefVersion by viewModel.preferencesVersion.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var section by rememberSaveable { mutableStateOf(HomeSection.NOW.name) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var savedMediaOpen by rememberSaveable { mutableStateOf(false) }

    val listenerEnabled = rememberNotificationListenerState(context)
    @Suppress("UNUSED_VARIABLE") val recomposePrefs = prefVersion
    val onboardingComplete = viewModel.preferences.onboardingComplete

    BackHandler(enabled = privacyOpen) { privacyOpen = false }
    BackHandler(enabled = savedMediaOpen) { savedMediaOpen = false }

    if (privacyOpen) {
        PrivacyScreen(onBack = { privacyOpen = false })
        return
    }

    if (savedMediaOpen) {
        SavedMediaScreen(
            viewModel = viewModel,
            onBack = { savedMediaOpen = false }
        )
        return
    }

    LaunchedEffect(requestedNotificationId, requestedDigest, notifications, initialLoadComplete) {
        when {
            requestedNotificationId != null && initialLoadComplete -> {
                if (notifications.any { it.id == requestedNotificationId }) {
                    detailId = requestedNotificationId
                    screen = AppScreen.DETAIL.name
                    viewModel.markRead(requestedNotificationId)
                    MetricsTracker.recordEvent(context, MetricsTracker.EVENT_NOTIFICATION_RETRIEVED)
                }
                onNavigationRequestConsumed()
            }
            requestedNotificationId != null -> Unit
            requestedDigest -> {
                section = HomeSection.DIGEST.name
                screen = AppScreen.HOME.name
                onNavigationRequestConsumed()
            }
        }
    }

    if (!onboardingComplete) {
        OnboardingScreen(
            listenerEnabled = listenerEnabled,
            onOpenPrivacy = { privacyOpen = true },
            onComplete = viewModel::completeOnboarding
        )
        return
    }

    val currentScreen = runCatching { AppScreen.valueOf(screen) }.getOrDefault(AppScreen.HOME)
    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        screen = AppScreen.HOME.name
        detailId = null
    }

    when (currentScreen) {
        AppScreen.HOME -> HomeScreen(
            notifications = notifications,
            selectedSection = runCatching { HomeSection.valueOf(section) }.getOrDefault(HomeSection.NOW),
            listenerEnabled = listenerEnabled,
            hideSensitive = viewModel.preferences.hideSensitivePreviews,
            expenses = expenses,
            onGetExpenseSummary = viewModel::getExpenseSummary,
            onToggleQuietPackage = viewModel::setQuietPackage,
            onSetState = viewModel::setState,
            onSetReminder = viewModel::setReminder,
            onTogglePin = { viewModel.setPinned(it, !it.pinned) },
            onBulkSetState = viewModel::bulkSetState,
            onBulkSnooze = viewModel::bulkSnooze,
            onBulkDelete = viewModel::bulkDelete,
            onSectionChange = { section = it.name },
            onOpenNotification = {
                detailId = it.id
                viewModel.markRead(it.id)
                MetricsTracker.recordEvent(context, MetricsTracker.EVENT_NOTIFICATION_RETRIEVED)
                screen = AppScreen.DETAIL.name
            },
            onSearch = { screen = AppScreen.SEARCH.name },
            onSettings = {
                viewModel.refreshObservedApps()
                viewModel.refreshStorageStats()
                screen = AppScreen.SETTINGS.name
            }
        )
        AppScreen.SEARCH -> SearchScreen(
            notifications = notifications,
            hideSensitive = viewModel.preferences.hideSensitivePreviews,
            onBack = { screen = AppScreen.HOME.name },
            onOpenNotification = {
                detailId = it.id
                viewModel.markRead(it.id)
                MetricsTracker.recordEvent(context, MetricsTracker.EVENT_NOTIFICATION_RETRIEVED)
                screen = AppScreen.DETAIL.name
            }
        )
        AppScreen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            listenerEnabled = listenerEnabled,
            onOpenPrivacy = { privacyOpen = true },
            onOpenSavedMedia = { savedMediaOpen = true },
            onBack = { screen = AppScreen.HOME.name }
        )
        AppScreen.DETAIL -> {
            val item = notifications.firstOrNull { it.id == detailId }
            if (item == null) {
                if (initialLoadComplete) {
                    LaunchedEffect(detailId) { screen = AppScreen.HOME.name }
                }
            } else {
                DetailScreen(
                    item = item,
                    hideSensitive = viewModel.preferences.hideSensitivePreviews,
                    autoCopyOtp = viewModel.preferences.autoCopyOtp,
                    onBack = { screen = AppScreen.HOME.name },
                    onStateChange = { viewModel.setState(item.id, it) },
                    onSetReminder = { viewModel.setReminder(item.id, it) },
                    onCategoryChange = { viewModel.setCategory(item, it) },
                    onPriorityChange = { viewModel.setPriority(item, it) },
                    onPinnedChange = { viewModel.setPinned(item, it) },
                    onVipChange = { viewModel.setVip(item, it) },
                    onDelete = {
                        viewModel.delete(item.id)
                        screen = AppScreen.HOME.name
                    },
                    onRemoveExpense = {
                        viewModel.removeExpenseForNotification(item.id)
                    },
                    onOpenOriginal = {
                        NotiFlowNotificationListener.openOriginal(
                            context = context,
                            notificationKey = item.notificationKey,
                            sourcePackage = item.packageName
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun rememberNotificationListenerState(context: Context): Boolean {
    var enabled by remember { mutableStateOf(NotiFlowNotificationListener.isAccessGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newEnabled = NotiFlowNotificationListener.isAccessGranted(context)
                if (enabled && !newEnabled) {
                    MetricsTracker.recordEvent(context, MetricsTracker.EVENT_PERMISSION_REVOKED)
                }
                enabled = newEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return enabled
}
