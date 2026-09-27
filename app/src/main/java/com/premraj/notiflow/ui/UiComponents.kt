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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val accentColor: Color,
    val containerColor: Color,
    val onContainerColor: Color
)

@Composable
fun getCategoryStyle(category: NotificationCategory): CategoryStyle {
    val dark = isSystemInDarkTheme()
    return when (category) {
        NotificationCategory.OTP -> CategoryStyle(
            shortLabel = "OTP",
            icon = Icons.Outlined.Key,
            accentColor = Color(0xFF10B981),
            containerColor = if (dark) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFFD1FAE5),
            onContainerColor = if (dark) Color(0xFF6EE7B7) else Color(0xFF065F46)
        )
        NotificationCategory.PAYMENT -> CategoryStyle(
            shortLabel = "Payment",
            icon = Icons.Outlined.CreditCard,
            accentColor = Color(0xFF38BDF8),
            containerColor = if (dark) Color(0xFF0C4A6E).copy(alpha = 0.55f) else Color(0xFFE0F2FE),
            onContainerColor = if (dark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
        )
        NotificationCategory.DELIVERY -> CategoryStyle(
            shortLabel = "Delivery",
            icon = Icons.Outlined.LocalShipping,
            accentColor = Color(0xFFF59E0B),
            containerColor = if (dark) Color(0xFF78350F).copy(alpha = 0.55f) else Color(0xFFFEF3C7),
            onContainerColor = if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
        )
        NotificationCategory.MESSAGE -> CategoryStyle(
            shortLabel = "Message",
            icon = Icons.Outlined.ChatBubbleOutline,
            accentColor = Color(0xFFA78BFA),
            containerColor = if (dark) Color(0xFF4C1D95).copy(alpha = 0.55f) else Color(0xFFEDE9FE),
            onContainerColor = if (dark) Color(0xFFC4B5FD) else Color(0xFF5B21B6)
        )
        NotificationCategory.WORK_STUDY -> CategoryStyle(
            shortLabel = "Work",
            icon = Icons.Outlined.WorkOutline,
            accentColor = Color(0xFF2DD4BF),
            containerColor = if (dark) Color(0xFF134E4A).copy(alpha = 0.55f) else Color(0xFFCCFBF1),
            onContainerColor = if (dark) Color(0xFF5EEAD4) else Color(0xFF115E59)
        )
        NotificationCategory.REMINDER_EVENT -> CategoryStyle(
            shortLabel = "Event",
            icon = Icons.Outlined.Event,
            accentColor = Color(0xFFFB7185),
            containerColor = if (dark) Color(0xFF881337).copy(alpha = 0.55f) else Color(0xFFFFE4E6),
            onContainerColor = if (dark) Color(0xFFFDA4AF) else Color(0xFF9F1239)
        )
        NotificationCategory.SOCIAL -> CategoryStyle(
            shortLabel = "Social",
            icon = Icons.Outlined.FavoriteBorder,
            accentColor = Color(0xFFF472B6),
            containerColor = if (dark) Color(0xFF831843).copy(alpha = 0.55f) else Color(0xFFFCE7F3),
            onContainerColor = if (dark) Color(0xFFF9A8D4) else Color(0xFF9D174D)
        )
        NotificationCategory.PROMOTION -> CategoryStyle(
            shortLabel = "Promo",
            icon = Icons.Outlined.LocalOffer,
            accentColor = Color(0xFFC084FC),
            containerColor = if (dark) Color(0xFF581C87).copy(alpha = 0.55f) else Color(0xFFF3E8FF),
            onContainerColor = if (dark) Color(0xFFE9D5FF) else Color(0xFF7E22CE)
        )
        NotificationCategory.SPAM -> CategoryStyle(
            shortLabel = "Spam",
            icon = Icons.Outlined.Block,
            accentColor = Color(0xFFF87171),
            containerColor = if (dark) Color(0xFF7F1D1D).copy(alpha = 0.55f) else Color(0xFFFEE2E2),
            onContainerColor = if (dark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
        )
        NotificationCategory.OTHER -> CategoryStyle(
            shortLabel = "Alert",
            icon = Icons.Outlined.Notifications,
            accentColor = Color(0xFF94A3B8),
            containerColor = if (dark) Color(0xFF1E293B).copy(alpha = 0.55f) else Color(0xFFF1F5F9),
            onContainerColor = if (dark) Color(0xFFCBD5E1) else Color(0xFF475569)
        )
    }
}

@Composable
fun NotificationCard(
    item: NotificationItem,
    hideSensitive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    val cardBorder = if (isHighPriority) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    val containerColor = if (isHighPriority) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
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
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
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
                                color = if (isSystemInDarkTheme()) Color(0xFF6EE7B7) else Color(0xFF047857)
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
                                color = if (isSystemInDarkTheme()) Color(0xFF7DD3FC) else Color(0xFF0369A1)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
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
            }
        }
    }
}

@Composable
fun AppBadge(appName: String, modifier: Modifier = Modifier) {
    val initials = appName.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "N" }

    val colorIndex = kotlin.math.abs(appName.hashCode()) % 6
    val (bgGradient, textTint) = when (colorIndex) {
        0 -> listOf(Color(0xFF6366F1), Color(0xFF4338CA)) to Color(0xFFEEF2FF)
        1 -> listOf(Color(0xFF0284C7), Color(0xFF0369A1)) to Color(0xFFF0F9FF)
        2 -> listOf(Color(0xFF10B981), Color(0xFF047857)) to Color(0xFFECFDF5)
        3 -> listOf(Color(0xFFF59E0B), Color(0xFFB45309)) to Color(0xFFFFFBEB)
        4 -> listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)) to Color(0xFFF5F3FF)
        else -> listOf(Color(0xFFEC4899), Color(0xFFBE185D)) to Color(0xFFFDF2F8)
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(bgGradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = textTint
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
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary
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
    Toast.makeText(context, "OTP $code copied to clipboard", Toast.LENGTH_SHORT).show()
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

