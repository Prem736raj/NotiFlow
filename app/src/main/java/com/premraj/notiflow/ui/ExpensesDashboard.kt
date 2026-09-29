package com.premraj.notiflow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premraj.notiflow.data.ExpenseCategory
import com.premraj.notiflow.data.ExpenseSummary
import com.premraj.notiflow.data.ExpenseTimeRange
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.TransactionType
import java.util.Locale

@Composable
fun ExpensesDashboard(
    notifications: List<NotificationItem>,
    expenses: List<ExpenseTransaction>,
    onGetExpenseSummary: suspend (ExpenseTimeRange) -> ExpenseSummary,
    onOpenNotification: (NotificationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf(ExpenseTimeRange.THIS_MONTH) }
    var transactionFilter by remember { mutableStateOf<TransactionType?>(null) }
    val dark = LocalNotiFlowDarkTheme.current

    val summary by produceState(
        initialValue = ExpenseSummary(0.0, 0.0, 0.0, 0, 0, emptyMap(), emptyList()),
        key1 = selectedRange,
        key2 = expenses
    ) {
        value = onGetExpenseSummary(selectedRange)
    }

    val filteredTransactions = remember(summary.transactions, transactionFilter) {
        if (transactionFilter == null) summary.transactions
        else summary.transactions.filter { it.transactionType == transactionFilter }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Time range filter row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ExpenseTimeRange.values()) { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { selectedRange = range },
                    label = { Text(range.label, fontWeight = if (selectedRange == range) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Hero Expense Overview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "TOTAL SPENT (${selectedRange.label.uppercase(Locale.ROOT)})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatCurrency(summary.totalDebit),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif
                            ),
                            color = if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = (if (dark) Color(0xFFEF4444) else Color(0xFFDC2626)).copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = if (dark) Color(0xFFF87171) else Color(0xFFDC2626),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Received / Credited
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = (if (dark) Color(0xFF10B981) else Color(0xFF059669)).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, (if (dark) Color(0xFF10B981) else Color(0xFF059669)).copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowUpward,
                                    contentDescription = null,
                                    tint = if (dark) Color(0xFF34D399) else Color(0xFF059669),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Received",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dark) Color(0xFF34D399) else Color(0xFF059669)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatCurrency(summary.totalCredit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (dark) Color(0xFF6EE7B7) else Color(0xFF047857)
                            )
                        }
                    }

                    // Transactions Count
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Transactions",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${summary.transactions.size} records",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Category breakdown card
        if (summary.categoryBreakdown.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Spending by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(14.dp))

                    val sortedCategories = summary.categoryBreakdown.entries.sortedByDescending { it.value }
                    for ((category, amount) in sortedCategories) {
                        val ratio = if (summary.totalDebit > 0) (amount / summary.totalDebit).toFloat() else 0f
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = formatCurrency(amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            }
        }

        // Transactions list section header & filter chips
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionTypeFilterChip(
                        label = "All",
                        selected = transactionFilter == null,
                        onClick = { transactionFilter = null }
                    )
                    TransactionTypeFilterChip(
                        label = "Spent",
                        selected = transactionFilter == TransactionType.DEBIT,
                        onClick = { transactionFilter = TransactionType.DEBIT }
                    )
                    TransactionTypeFilterChip(
                        label = "Received",
                        selected = transactionFilter == TransactionType.CREDIT,
                        onClick = { transactionFilter = TransactionType.CREDIT }
                    )
                }
            }
        }

        // Transaction items or empty state
        if (filteredTransactions.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "No transactions found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "As your bank and UPI apps (Google Pay, PhonePe, Paytm, SMS) send payment alerts, NotiFlow will parse and log your expenses 100% offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (tx in filteredTransactions) {
                    val matchingNotification = notifications.firstOrNull { it.id == tx.notificationId }
                    ExpenseTransactionCard(
                        transaction = tx,
                        onClick = {
                            if (matchingNotification != null) {
                                onOpenNotification(matchingNotification)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionTypeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ExpenseTransactionCard(
    transaction: ExpenseTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDebit = transaction.transactionType == TransactionType.DEBIT
    val dark = LocalNotiFlowDarkTheme.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDebit) (if (dark) Color(0xFFEF4444) else Color(0xFFDC2626)).copy(alpha = 0.12f)
                        else (if (dark) Color(0xFF10B981) else Color(0xFF059669)).copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    tint = if (isDebit) {
                        if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
                    } else {
                        if (dark) Color(0xFF34D399) else Color(0xFF059669)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchantOrParty ?: if (isDebit) "Debit Transaction" else "Credit Received",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = transaction.expenseCategory.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    transaction.accountRef?.let { acc ->
                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = acc,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (transaction.balanceAfter != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Bal: ${formatCurrency(transaction.balanceAfter)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Amount badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isDebit) "- " else "+ ") + formatCurrency(transaction.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    ),
                    color = if (isDebit) {
                        if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
                    } else {
                        if (dark) Color(0xFF34D399) else Color(0xFF059669)
                    }
                )
                Text(
                    text = relativeExpenseTime(transaction.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    return String.format(Locale.getDefault(), "₹%,.2f", amount)
}

private fun relativeExpenseTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val mins = diff / 60_000L
    val hours = mins / 60L
    val days = hours / 24L

    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> java.text.SimpleDateFormat("dd MMM", Locale.getDefault()).format(java.util.Date(timestamp))
    }
}
