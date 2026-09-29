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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    onSectionChange: (HomeSection) -> Unit,
    onOpenNotification: (NotificationItem) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val dark = LocalNotiFlowDarkTheme.current

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
            (!it.isActiveOnSystem && it.state == NotificationState.ACTIVE && !it.pinned &&
                LocalIntelligence.effectivePriority(it) != NotificationPriority.LOW)
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

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Text(
                                text = "NotiFlow",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = when (selectedSection) {
                                HomeSection.NOW -> "What deserves attention"
                                HomeSection.LATER -> "Things you chose to revisit"
                                HomeSection.DIGEST -> "Low-priority noise, contained"
                                HomeSection.HISTORY -> "Done, archived and past alerts"
                                HomeSection.DELETED -> "Removed by source apps, preserved in NotiFlow"
                                HomeSection.EXPENSES -> "100% offline bank & UPI spending tracker"
                                HomeSection.INSIGHTS -> "Notification activity & distraction insights"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(onClick = onSearch) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
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
                                        "Enable permission so NotiFlow can triage and auto-copy incoming OTPs.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
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

            // Primary workflow navigation stays focused on notification states.
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SectionChip(
                            label = "Now",
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
                            icon = Icons.Outlined.ViewList,
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

            // Secondary tools remain available without competing with the inbox states.
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SectionChip(
                            label = "Expenses",
                            count = expenses.size,
                            icon = Icons.Outlined.AccountBalanceWallet,
                            isSelected = selectedSection == HomeSection.EXPENSES,
                            secondary = true,
                            onClick = { onSectionChange(HomeSection.EXPENSES) }
                        )
                    }
                    item {
                        SectionChip(
                            label = "Insights",
                            count = null,
                            icon = Icons.Outlined.BarChart,
                            isSelected = selectedSection == HomeSection.INSIGHTS,
                            secondary = true,
                            onClick = { onSectionChange(HomeSection.INSIGHTS) }
                        )
                    }
                    if (deletedItems.isNotEmpty()) {
                        item {
                            SectionChip(
                                label = "Source removed",
                                count = deletedItems.size,
                                icon = Icons.Outlined.Security,
                                isSelected = selectedSection == HomeSection.DELETED,
                                secondary = true,
                                onClick = { onSectionChange(HomeSection.DELETED) }
                            )
                        }
                    }
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
                            Icons.Outlined.ViewList
                        )
                        HomeSection.HISTORY -> Triple(
                            "History is clear",
                            "Completed, archived, and past system notifications remain reviewable here.",
                            Icons.Outlined.History
                        )
                        HomeSection.DELETED -> Triple(
                            "No source-removed notifications",
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
                        onClick = { onOpenNotification(item) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionChip(
    label: String,
    count: Int?,
    icon: ImageVector,
    isSelected: Boolean,
    secondary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            secondary -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            count?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

