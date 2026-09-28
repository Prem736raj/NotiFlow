package com.premraj.notiflow.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.data.TransactionType
import com.premraj.notiflow.intelligence.ActionExtractor
import com.premraj.notiflow.intelligence.SmartAction
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: NotificationItem,
    hideSensitive: Boolean,
    onBack: () -> Unit,
    onStateChange: (NotificationState) -> Unit,
    onSetReminder: (Long?) -> Unit,
    onCategoryChange: (NotificationCategory) -> Unit,
    onPriorityChange: (NotificationPriority) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onVipChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenOriginal: () -> Boolean
) {
    val context = LocalContext.current
    val platformLocale = LocalLocale.current.platformLocale
    val dark = isSystemInDarkTheme()
    var categoryMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val smartActions = remember(item.id, item.title, item.body, item.category, item.state) { ActionExtractor.extract(item) }
    val extractedOtp = remember(item.id, item.title, item.body) { ActionExtractor.extractOtpCode(item.title, item.body) }
    val categoryStyle = getCategoryStyle(item.category)

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete from NotiFlow history?", fontWeight = FontWeight.Bold) },
            text = { Text("This permanently removes NotiFlow’s local entry. The source app’s notifications are unaffected.") },
            confirmButton = {
                TextButton(
                    onClick = { deleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(item.appName, maxLines = 1, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = if (item.pinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(onClick = { onPinnedChange(!item.pinned) }) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = if (item.pinned) "Unpin" else "Pin",
                                tint = if (item.pinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (item.isVip) Color(0xFFF59E0B).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(onClick = { onVipChange(!item.isVip) }) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = if (item.isVip) "Remove VIP" else "Make VIP",
                                tint = if (item.isVip) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dedicated Hero OTP Card if OTP code is found
            if (extractedOtp != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (dark) Color(0xFF064E3B).copy(alpha = 0.6f) else Color(0xFFD1FAE5)
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "VERIFICATION CODE",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 1.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (dark) Color(0xFF6EE7B7) else Color(0xFF047857)
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = extractedOtp.chunked(3).joinToString("  "),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 3.sp
                                ),
                                color = if (dark) Color(0xFFECFDF5) else Color(0xFF064E3B)
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "✓ Automatically copied to clipboard on arrival",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dark) Color(0xFF6EE7B7).copy(alpha = 0.85f) else Color(0xFF047857)
                            )

                            Spacer(Modifier.height(14.dp))

                            Button(
                                onClick = { copyOtpToClipboard(context, extractedOtp) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Re-Copy Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Message Deleted By Sender Card
            if (item.isDeletedBySender) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = (if (dark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (dark) Color(0xFFEF4444) else Color(0xFFF87171))
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "MESSAGE DELETED BY SENDER",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (dark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "The sender deleted this message for everyone, but NotiFlow intercepted and preserved the original message text safely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Smart Expense Receipt Card
            if (item.expenseTransaction != null) {
                val exp = item.expenseTransaction
                val isDebit = exp.transactionType == TransactionType.DEBIT
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = (if (isDebit) Color(0xFFEF4444) else Color(0xFF10B981)).copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, (if (isDebit) Color(0xFFEF4444) else Color(0xFF10B981)).copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(exp.expenseCategory.emoji, fontSize = 24.sp)
                                    Column {
                                        Text(
                                            if (isDebit) "Debit Transaction" else "Credit Received",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDebit) (if (dark) Color(0xFFFCA5A5) else Color(0xFFB91C1C))
                                            else (if (dark) Color(0xFF6EE7B7) else Color(0xFF047857))
                                        )
                                        Text(
                                            exp.expenseCategory.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = (if (isDebit) "- " else "+ ") + String.format(platformLocale, "₹%,.2f", exp.amount),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    color = if (isDebit) (if (dark) Color(0xFFF87171) else Color(0xFFDC2626))
                                    else (if (dark) Color(0xFF34D399) else Color(0xFF059669))
                                )
                            }

                            if (!exp.merchantOrParty.isNullOrBlank() || !exp.accountRef.isNullOrBlank() || exp.balanceAfter != null) {
                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = (if (isDebit) Color(0xFFEF4444) else Color(0xFF10B981)).copy(alpha = 0.2f))
                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    exp.merchantOrParty?.let { party ->
                                        Column {
                                            Text("Party / Merchant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(party, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    exp.accountRef?.let { acc ->
                                        Column {
                                            Text("Account / Card", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(acc, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    exp.balanceAfter?.let { bal ->
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Balance After", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(String.format(platformLocale, "₹%,.2f", bal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Amount", exp.amount.toString()))
                                        Toast.makeText(context, "Amount copied: ₹${exp.amount}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDebit) Color(0xFFEF4444) else Color(0xFF10B981)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy Amount", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Main Notification Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppBadge(item.appName)
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(
                                    text = item.appName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.postedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Category pill badge
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = categoryStyle.containerColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        categoryStyle.icon,
                                        contentDescription = null,
                                        tint = categoryStyle.onContainerColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        categoryStyle.shortLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = categoryStyle.onContainerColor
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = visibleBody(item, hideSensitive),
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(16.dp))

                        // Shade status indicator pill
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (item.isActiveOnSystem) Color(0xFF10B981).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (item.isActiveOnSystem) Color(0xFF10B981)
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Text(
                                    text = if (item.isActiveOnSystem) "Active in Android status bar"
                                    else "Dismissed from Android status bar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (item.isActiveOnSystem) Color(0xFF10B981)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Useful Actions
            if (smartActions.isNotEmpty()) {
                item {
                    Text("Useful actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(smartActions, key = { it.label + it.hashCode() }) { action ->
                            val icon = when (action) {
                                is SmartAction.CopyCode -> Icons.Outlined.ContentCopy
                                is SmartAction.OpenUrl -> Icons.Outlined.OpenInNew
                                is SmartAction.Call -> Icons.Outlined.Call
                                is SmartAction.OpenMap -> Icons.Outlined.Place
                                is SmartAction.AddCalendar -> Icons.Outlined.Event
                                SmartAction.CreateReminder -> Icons.Outlined.Schedule
                            }
                            OutlinedButton(
                                onClick = {
                                    if (action is SmartAction.CreateReminder) {
                                        showDateTimePicker(context) { onSetReminder(it) }
                                    } else {
                                        executeSmartAction(context, item, action)
                                    }
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(action.label, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Deal with it (Triage)
            item {
                Text("Deal with it", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.state == NotificationState.DONE || item.state == NotificationState.ARCHIVED) {
                        item {
                            Button(
                                onClick = { onStateChange(NotificationState.ACTIVE) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore to inbox")
                            }
                        }
                    } else {
                        item {
                            Button(
                                onClick = { showDateTimePicker(context) { onSetReminder(it) } },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (item.state == NotificationState.LATER) "Reschedule" else "Remind later")
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { onStateChange(NotificationState.DONE) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Done")
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { onStateChange(NotificationState.ARCHIVED) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Archive")
                            }
                        }
                    }
                }

                if (item.remindAt != null) {
                    Spacer(Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Reminder: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.remindAt))}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onSetReminder(null) }) { Text("Cancel") }
                        }
                    }
                }
            }

            // Understand & Classify
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Classification & Priority", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box {
                                OutlinedButton(
                                    onClick = { categoryMenu = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(categoryStyle.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(item.category.label)
                                }
                                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                    NotificationCategory.entries.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category.label) },
                                            onClick = { categoryMenu = false; onCategoryChange(category) }
                                        )
                                    }
                                }
                            }
                            Box {
                                OutlinedButton(
                                    onClick = { priorityMenu = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("${item.priority.label} priority")
                                }
                                DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                                    NotificationPriority.entries.forEach { priority ->
                                        DropdownMenuItem(
                                            text = { Text(priority.label) },
                                            onClick = { priorityMenu = false; onPriorityChange(priority) }
                                        )
                                    }
                                }
                            }
                        }
                        if (item.categoryOverridden || item.priorityOverridden) {
                            Text(
                                "Your manual adjustments are saved and applied to future notifications from this app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }

            // Source & Local Data
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Source App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (!onOpenOriginal()) Toast.makeText(context, "Original notification has expired", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open original")
                            }
                            OutlinedButton(
                                onClick = { deleteConfirm = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Delete history")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${item.packageName}\n100% stored on device in encrypted local SQLite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun showDateTimePicker(context: Context, onSelected: (Long) -> Unit) {
    val initial = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selected = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (selected.timeInMillis <= System.currentTimeMillis()) selected.add(Calendar.DAY_OF_YEAR, 1)
                    onSelected(selected.timeInMillis)
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                false
            ).show()
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun executeSmartAction(context: Context, item: NotificationItem, action: SmartAction) {
    val intent = when (action) {
        is SmartAction.CopyCode -> {
            copyOtpToClipboard(context, action.code)
            return
        }
        is SmartAction.OpenUrl -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        is SmartAction.Call -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.phone}"))
        is SmartAction.OpenMap -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(action.address)}"))
        is SmartAction.AddCalendar -> Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, item.displayTitle)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.startAt)
        }
        SmartAction.CreateReminder -> return
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No compatible app found", Toast.LENGTH_SHORT).show() }
}

