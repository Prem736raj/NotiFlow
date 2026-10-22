package com.premraj.notiflow.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WorkOutline
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.intelligence.ActionExtractor
import com.premraj.notiflow.intelligence.LocalIntelligence

data class CategoryStyle(
    val shortLabel: String,
    val icon: ImageVector,
    val containerColor: Color,
    val onContainerColor: Color
)

@Composable
fun getCategoryStyle(category: NotificationCategory): CategoryStyle {
    val (label, icon) = when (category) {
        NotificationCategory.OTP -> "OTP" to Icons.Outlined.Key
        NotificationCategory.PAYMENT -> "Payment" to Icons.Outlined.CreditCard
        NotificationCategory.DELIVERY -> "Delivery" to Icons.Outlined.LocalShipping
        NotificationCategory.MESSAGE -> "Message" to Icons.Outlined.ChatBubbleOutline
        NotificationCategory.WORK_STUDY -> "Work" to Icons.Outlined.WorkOutline
        NotificationCategory.REMINDER_EVENT -> "Event" to Icons.Outlined.Event
        NotificationCategory.SOCIAL -> "Social" to Icons.Outlined.FavoriteBorder
        NotificationCategory.PROMOTION -> "Promo" to Icons.Outlined.LocalOffer
        NotificationCategory.SPAM -> "Spam" to Icons.Outlined.Block
        NotificationCategory.OTHER -> "Alert" to Icons.Outlined.Notifications
    }
    val isWarning = category == NotificationCategory.SPAM
    return CategoryStyle(
        shortLabel = label,
        icon = icon,
        containerColor = if (isWarning) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        onContainerColor = if (isWarning) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
fun NotificationCard(
    item: NotificationItem,
    hideSensitive: Boolean,
    onClick: () -> Unit,
    onQuickRemind: ((Long) -> Unit)? = null,
    onQuickDone: (() -> Unit)? = null,
    onQuickTogglePin: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var remindMenuExpanded by remember { mutableStateOf(false) }
    val effectivePriority = LocalIntelligence.effectivePriority(item)
    val isHighPriority = effectivePriority == NotificationPriority.HIGH
    val categoryStyle = getCategoryStyle(item.category)

    val extractedOtp = remember(item.id, item.title, item.body) {
        ActionExtractor.extractOtpCode(item.title, item.body)
    }
    val paymentAmount = remember(item.id, item.title, item.body) {
        if (item.category == NotificationCategory.PAYMENT) {
            val text = "${item.title.orEmpty()} ${item.body.orEmpty()}"
            Regex("(?i)(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)").find(text)?.value
        } else null
    }

    val cardBorder = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else if (isHighPriority) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    } else null

    val containerColor = if (isHighPriority) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (isHighPriority) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 12.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppBadge(appName = item.appName)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.appName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = relativeTime(item.postedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = (-0.2).sp
                            ),
                            fontWeight = if (!item.read) FontWeight.Bold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!item.read) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = visibleBody(item, hideSensitive),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (extractedOtp != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        modifier = Modifier.clickable {
                            copyOtpToClipboard(context, extractedOtp)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy code",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = extractedOtp,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                ),
                                color = if (LocalNotiFlowDarkTheme.current) Color(0xFF6EE7B7) else Color(0xFF047857)
                            )
                            Text(
                                text = "• Auto-copied (tap to re-copy)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (paymentAmount != null && extractedOtp == null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = paymentAmount,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (LocalNotiFlowDarkTheme.current) Color(0xFF7DD3FC) else Color(0xFF0369A1)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
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
                                    imageVector = categoryStyle.icon,
                                    contentDescription = null,
                                    tint = categoryStyle.onContainerColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = categoryStyle.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = categoryStyle.onContainerColor,
                                    maxLines = 1
                                )
                            }
                        }

                        if (isHighPriority) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Important",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        if (item.isVip) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Star,
                                        contentDescription = "VIP",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "VIP",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }

                        if (item.pinned) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = "Pinned",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Pinned",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    if (!selectionMode && (onQuickRemind != null || onQuickDone != null || onQuickTogglePin != null)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (onQuickRemind != null) {
                                Box {
                                    IconButton(
                                        onClick = { remindMenuExpanded = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = "Remind me",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = remindMenuExpanded,
                                        onDismissRequest = { remindMenuExpanded = false }
                                    ) {
                                        val now = System.currentTimeMillis()
                                        val cal = Calendar.getInstance()

                                        val tonightCal = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, 20)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            if (timeInMillis <= now + 30 * 60 * 1000) {
                                                add(Calendar.HOUR_OF_DAY, 2)
                                            }
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Tonight (8:00 PM)") },
                                            onClick = {
                                                remindMenuExpanded = false
                                                onQuickRemind(tonightCal.timeInMillis)
                                            }
                                        )

                                        val tomorrowCal = Calendar.getInstance().apply {
                                            add(Calendar.DAY_OF_YEAR, 1)
                                            set(Calendar.HOUR_OF_DAY, 9)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Tomorrow (9:00 AM)") },
                                            onClick = {
                                                remindMenuExpanded = false
                                                onQuickRemind(tomorrowCal.timeInMillis)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("In 1 hour") },
                                            onClick = {
                                                remindMenuExpanded = false
                                                onQuickRemind(now + 1 * 3600 * 1000L)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("In 2 hours") },
                                            onClick = {
                                                remindMenuExpanded = false
                                                onQuickRemind(now + 2 * 3600 * 1000L)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Custom date & time...") },
                                            onClick = {
                                                remindMenuExpanded = false
                                                DatePickerDialog(
                                                    context,
                                                    { _, year, month, day ->
                                                        TimePickerDialog(
                                                            context,
                                                            { _, hour, minute ->
                                                                val customCal = Calendar.getInstance().apply {
                                                                    set(year, month, day, hour, minute, 0)
                                                                }
                                                                onQuickRemind(customCal.timeInMillis)
                                                            },
                                                            cal.get(Calendar.HOUR_OF_DAY),
                                                            cal.get(Calendar.MINUTE),
                                                            false
                                                        ).show()
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            }
                                        )
                                    }
                                }
                            }

                            if (onQuickTogglePin != null) {
                                IconButton(
                                    onClick = onQuickTogglePin,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PushPin,
                                        contentDescription = if (item.pinned) "Unpin" else "Pin",
                                        tint = if (item.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (onQuickDone != null && item.state != com.premraj.notiflow.data.NotificationState.DONE) {
                                IconButton(
                                    onClick = onQuickDone,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = "Mark Done",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppBadge(appName: String, modifier: Modifier = Modifier) {
    val initials = appName.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "N" }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MetaPill(text: String, emphasized: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Outlined.CheckCircle,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

fun copyOtpToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("OTP Code", code).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Verification code copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun visibleBody(item: NotificationItem, hideSensitive: Boolean): String {
    if (!hideSensitive) return item.displayBody
    return when (item.category) {
        NotificationCategory.OTP -> "Verification content hidden"
        NotificationCategory.PAYMENT -> "Financial notification preview hidden"
        else -> item.displayBody
    }
}

fun relativeTime(timestamp: Long): String = DateUtils.getRelativeTimeSpanString(
    timestamp,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE
).toString()

