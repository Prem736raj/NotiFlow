package com.premraj.notiflow.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.premraj.notiflow.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premraj.notiflow.data.ExpenseSummary
import com.premraj.notiflow.data.ExpenseTimeRange
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.HomeSection
import com.premraj.notiflow.data.InsightsTimeRange
import com.premraj.notiflow.data.NotificationInsights
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.intelligence.LocalIntelligence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    notifications: List<NotificationItem>,
    selectedSection: HomeSection,
    listenerEnabled: Boolean,
    hideSensitive: Boolean,
    expenses: List<ExpenseTransaction> = emptyList(),
    onGetExpenseSummary: suspend (ExpenseTimeRange) -> ExpenseSummary = { ExpenseSummary(0.0, 0.0, 0.0, 0, 0, emptyMap(), emptyList()) },
    onGetInsights: (InsightsTimeRange) -> NotificationInsights = {
        com.premraj.notiflow.intelligence.InsightsAnalyzer.computeInsights(notifications, it)
    },
    onToggleQuietPackage: (String, Boolean) -> Unit = { _, _ -> },
    onSetState: (Long, NotificationState) -> Unit = { _, _ -> },
    onSetReminder: (Long, Long?) -> Unit = { _, _ -> },
    onTogglePin: (NotificationItem) -> Unit = {},
    onBulkSetState: (Set<Long>, NotificationState) -> Unit = { _, _ -> },
    onBulkSnooze: (Set<Long>, Long) -> Unit = { _, _ -> },
    onBulkDelete: (Set<Long>) -> Unit = {},
    onSectionChange: (HomeSection) -> Unit,
    onOpenNotification: (NotificationItem) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val dark = LocalNotiFlowDarkTheme.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bulkDeleteConfirm by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
        bulkDeleteConfirm = false
    }

    fun showBulkResult(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
        exitSelection()
    }

    LaunchedEffect(selectedSection) {
        selectionMode = false
        selectedIds = emptySet()
        bulkDeleteConfirm = false
    }

    val nowItems = notifications.filter {
        it.state == NotificationState.ACTIVE &&
            (it.isActiveOnSystem || it.pinned) &&
            (LocalIntelligence.effectivePriority(it) != NotificationPriority.LOW || it.pinned)
    }.sortedWith(compareByDescending<NotificationItem> { it.pinned }
        .thenByDescending { LocalIntelligence.effectivePriority(it) == NotificationPriority.HIGH }
        .thenByDescending { it.postedAt })

    val laterItems = notifications.filter { it.state == NotificationState.LATER }.sortedBy { it.remindAt ?: Long.MAX_VALUE }
    val digestItems = notifications.filter {
        it.state == NotificationState.ACTIVE && LocalIntelligence.effectivePriority(it) == NotificationPriority.LOW
    }.sortedByDescending { it.postedAt }
    val historyItems = notifications.filter {
        it.state == NotificationState.DONE || it.state == NotificationState.ARCHIVED ||
            (!it.isActiveOnSystem && it.state == NotificationState.ACTIVE && !it.pinned)
    }.sortedByDescending { it.updatedAt }
    val deletedItems = notifications.filter { it.isRemovedBySource }.sortedByDescending { it.updatedAt }

    val visible = when (selectedSection) {
        HomeSection.NOW -> nowItems
        HomeSection.LATER -> laterItems
        HomeSection.DIGEST -> digestItems
        HomeSection.HISTORY -> historyItems
        HomeSection.DELETED -> deletedItems
        HomeSection.EXPENSES -> emptyList()
        HomeSection.INSIGHTS -> emptyList()
    }

    val highPriorityCount = nowItems.count { LocalIntelligence.effectivePriority(it) == NotificationPriority.HIGH }

    if (bulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { bulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} notifications?") },
            text = { Text("This permanently removes the selected entries from NotiFlow history. Source apps and their notifications are unaffected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selectedIds
                        onBulkDelete(ids)
                        showBulkResult("Deleted ${ids.size} notifications")
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectionMode) {
                BulkActionBar(
                    selectedCount = selectedIds.size,
                    onDone = {
                        val ids = selectedIds
                        onBulkSetState(ids, NotificationState.DONE)
                        showBulkResult("Marked ${ids.size} as Done")
                    },
                    onSnooze = {
                        val ids = selectedIds
                        onBulkSnooze(ids, System.currentTimeMillis() + 60 * 60 * 1000L)
                        showBulkResult("Snoozed ${ids.size} for 1 hour")
                    },
                    onArchive = {
                        val ids = selectedIds
                        onBulkSetState(ids, NotificationState.ARCHIVED)
                        showBulkResult("Archived ${ids.size} notifications")
                    },
                    onDelete = { bulkDeleteConfirm = true }
                )
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel selection")
                        }
                    }
                },
                title = {
                    Column {
                        if (selectionMode) {
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap alerts to add or remove them",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.app_logo),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Text(
                                    text = "NotiFlow",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                val liveColor = if (listenerEnabled) Color(0xFF10B981) else Color(0xFFF59E0B)
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = liveColor.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, liveColor.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(liveColor)
                                        )
                                        Text(
                                            text = if (listenerEnabled) "Active" else "Paused",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            color = liveColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            Text(
                                text = when (selectedSection) {
                                    HomeSection.NOW -> "Important alerts needing attention"
                                    HomeSection.LATER -> "Follow-up queue & scheduled reminders"
                                    HomeSection.DIGEST -> "Low-priority noise, contained"
                                    HomeSection.HISTORY -> "Completed, archived, and past alerts"
                                    HomeSection.DELETED -> "Removed by source apps, preserved in NotiFlow"
                                    HomeSection.EXPENSES -> "100% offline bank & UPI spending tracker"
                                    HomeSection.INSIGHTS -> "Notification activity & distraction insights"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                val allSelected = visible.isNotEmpty() && selectedIds.size == visible.size
                                selectedIds = if (allSelected) emptySet() else visible.map { it.id }.toSet()
                            }
                        ) {
                            Text(
                                if (selectedIds.size == visible.size && visible.isNotEmpty()) "Clear all" else "Select all"
                            )
                        }
                    } else {
                        if (visible.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(38.dp)
                            ) {
                                IconButton(onClick = { selectionMode = true }) {
                                    Icon(
                                        Icons.Outlined.CheckBox,
                                        contentDescription = "Select notifications",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(38.dp)
                        ) {
                            IconButton(onClick = onSearch) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(38.dp)
                        ) {
                            IconButton(onClick = onSettings) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Permission warning banner if notification listener is off
            if (!listenerEnabled) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (dark) Color(0xFF451A03) else Color(0xFFFEF3C7)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (dark) Color(0xFF78350F) else Color(0xFFFDE68A))
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.NotificationsOff,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Notification Access Needed",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dark) Color(0xFFFEF3C7) else Color(0xFF78350F)
                                    )
                                    Text(
                                        "Enable permission so NotiFlow can triage and preserve incoming notifications.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF451A03)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Grant Notification Access", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Unified primary navigation with refined Material 3 pill chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SectionChip(
                            label = "Important",
                            count = nowItems.size,
                            icon = Icons.Outlined.Notifications,
                            isSelected = selectedSection == HomeSection.NOW,
                            onClick = { onSectionChange(HomeSection.NOW) }
                        )
                    }
                    item {
                        SectionChip(
                            label = "Later",
                            count = laterItems.size,
                            icon = Icons.Outlined.AccessTime,
                            isSelected = selectedSection == HomeSection.LATER,
                            onClick = { onSectionChange(HomeSection.LATER) }
                        )
                    }
                    item {
                        SectionChip(
                            label = "Digest",
                            count = digestItems.size,
                            icon = Icons.AutoMirrored.Outlined.ViewList,
                            isSelected = selectedSection == HomeSection.DIGEST,
                            onClick = { onSectionChange(HomeSection.DIGEST) }
                        )
                    }
                    item {
                        SectionChip(
                            label = "History",
                            count = historyItems.size,
                            icon = Icons.Outlined.History,
                            isSelected = selectedSection == HomeSection.HISTORY,
                            onClick = { onSectionChange(HomeSection.HISTORY) }
                        )
                    }
                    if (deletedItems.isNotEmpty()) {
                        item {
                            SectionChip(
                                label = "Removed",
                                count = deletedItems.size,
                                icon = Icons.Outlined.Security,
                                isSelected = selectedSection == HomeSection.DELETED,
                                onClick = { onSectionChange(HomeSection.DELETED) }
                            )
                        }
                    }
                    item {
                        SectionChip(
                            label = "Expenses",
                            count = expenses.size,
                            icon = Icons.Outlined.AccountBalanceWallet,
                            isSelected = selectedSection == HomeSection.EXPENSES,
                            onClick = { onSectionChange(HomeSection.EXPENSES) }
                        )
                    }
                    item {
                        SectionChip(
                            label = "Insights",
                            count = null,
                            icon = Icons.Outlined.BarChart,
                            isSelected = selectedSection == HomeSection.INSIGHTS,
                            onClick = { onSectionChange(HomeSection.INSIGHTS) }
                        )
                    }
                }
            }

            if (selectedSection == HomeSection.NOW && highPriorityCount > 0) {
                item {
                    Text(
                        text = "$highPriorityCount important ${if (highPriorityCount == 1) "item" else "items"} need attention",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                }
            }

            // Empty state, expenses dashboard, insights dashboard, or notification list
            if (selectedSection == HomeSection.EXPENSES) {
                item {
                    ExpensesDashboard(
                        notifications = notifications,
                        expenses = expenses,
                        onGetExpenseSummary = onGetExpenseSummary,
                        onOpenNotification = onOpenNotification
                    )
                }
            } else if (selectedSection == HomeSection.INSIGHTS) {
                item {
                    InsightsDashboard(
                        notifications = notifications,
                        onGetInsights = onGetInsights,
                        onToggleQuietPackage = onToggleQuietPackage
                    )
                }
            } else if (visible.isEmpty()) {
                item {
                    val (title, body, icon) = when (selectedSection) {
                        HomeSection.NOW -> Triple(
                            "You’re all caught up",
                            "Important notifications and active alerts will appear here cleanly.",
                            Icons.Outlined.CheckCircle
                        )
                        HomeSection.LATER -> Triple(
                            "Nothing waiting",
                            "Save a notification for later and it will stay here until you're ready to revisit.",
                            Icons.Outlined.AccessTime
                        )
                        HomeSection.DIGEST -> Triple(
                            "No noise to digest",
                            "Low-priority promotions and social updates will gather here quietly.",
                            Icons.AutoMirrored.Outlined.ViewList
                        )
                        HomeSection.HISTORY -> Triple(
                            "History is clear",
                            "Completed, archived, dismissed, and source-removed alerts remain reviewable here.",
                            Icons.Outlined.History
                        )
                        HomeSection.DELETED -> Triple(
                            "No deleted notifications",
                            "Notifications canceled by their source apps can be preserved here. This does not prove a sender used “Delete for everyone”.",
                            Icons.Outlined.Security
                        )
                        HomeSection.EXPENSES -> Triple(
                            "No expenses",
                            "",
                            Icons.Outlined.AccountBalanceWallet
                        )
                        HomeSection.INSIGHTS -> Triple(
                            "No insights yet",
                            "Insights will calculate as notification history develops.",
                            Icons.Outlined.BarChart
                        )
                    }
                    EmptyState(title = title, message = body, icon = icon)
                }
            } else {
                items(visible, key = { it.id }) { item ->
                    NotificationCard(
                        item = item,
                        hideSensitive = hideSensitive,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (item.id in selectedIds) {
                                    selectedIds - item.id
                                } else {
                                    selectedIds + item.id
                                }
                            } else {
                                onOpenNotification(item)
                            }
                        },
                        onQuickRemind = { triggerAtMillis ->
                            val prevState = item.state
                            val prevReminder = item.remindAt
                            onSetReminder(item.id, triggerAtMillis)
                            onSetState(item.id, NotificationState.LATER)
                            coroutineScope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "Moved to Later (reminder set)",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onSetReminder(item.id, prevReminder)
                                    onSetState(item.id, prevState)
                                }
                            }
                        },
                        onQuickDone = {
                            val prevState = item.state
                            val prevReminder = item.remindAt
                            onSetState(item.id, NotificationState.DONE)
                            onSetReminder(item.id, null)
                            coroutineScope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "Marked as Done",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onSetState(item.id, prevState)
                                    onSetReminder(item.id, prevReminder)
                                }
                            }
                        },
                        onQuickTogglePin = {
                            onTogglePin(item)
                        },
                        selectionMode = selectionMode,
                        selected = item.id in selectedIds,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkActionBar(
    selectedCount: Int,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            BulkAction(
                icon = Icons.Outlined.CheckCircle,
                label = "Done",
                enabled = selectedCount > 0,
                onClick = onDone,
                modifier = Modifier.weight(1f)
            )
            BulkAction(
                icon = Icons.Outlined.AccessTime,
                label = "1 hour",
                enabled = selectedCount > 0,
                onClick = onSnooze,
                modifier = Modifier.weight(1f)
            )
            BulkAction(
                icon = Icons.Outlined.Archive,
                label = "Archive",
                enabled = selectedCount > 0,
                onClick = onArchive,
                modifier = Modifier.weight(1f)
            )
            BulkAction(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                enabled = selectedCount > 0,
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BulkAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SectionChip(
    label: String,
    count: Int?,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = containerColor,
        border = border,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )
            if (count != null && count > 0) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = contentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                    )
                }
            }
        }
    }
}

