package com.premraj.notiflow.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.premraj.notiflow.MainActivity
import com.premraj.notiflow.R
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.ClassificationResult
import com.premraj.notiflow.data.IncomingNotification
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.intelligence.ActionExtractor
import com.premraj.notiflow.intelligence.LocalIntelligence
import com.premraj.notiflow.util.NotificationChannels
import com.premraj.notiflow.util.NotificationPosting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotiFlowNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return

        // Group summaries duplicate child notifications for an inbox/digest product.
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // Default product policy: do not persist ongoing/foreground-service status notifications.
        if (
            sbn.isOngoing ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        ) {
            return
        }

        val graph = appGraph

        // Privacy exclusion: do not capture or store notifications from excluded apps
        if (sbn.packageName in graph.preferences.excludedPackages()) return

        // Exit the main callback quickly.
        scope.launch {
            processPosted(sbn)
        }
    }

    private suspend fun processPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras

        val messagingStyle =
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

        val latestMessage = messagingStyle?.messages?.lastOrNull()

        val title = extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val sender = latestMessage
            ?.person
            ?.name
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: messagingStyle
                ?.conversationTitle
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: extras
                ?.getCharSequence(Notification.EXTRA_SUB_TEXT)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val body = latestMessage
            ?.text
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extras
                ?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: extras
                ?.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: extras
                ?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString("\n") { it.toString() }
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val appName = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse {
            friendlyPackageFallback(sbn.packageName)
        }

        val graph = appGraph

        val fast = LocalIntelligence.classify(
            packageName = sbn.packageName,
            title = title,
            body = body,
            sender = sender
        )

        val personalizedFast = applyPersonalization(
            classification = fast,
            packageName = sbn.packageName,
            sender = sender
        )

        val vip = graph.preferences.isVip(sbn.packageName, sender)

        val initial = if (vip) {
            personalizedFast.copy(
                priority = NotificationPriority.HIGH,
                confidence = maxOf(personalizedFast.confidence, 0.95f)
            )
        } else {
            personalizedFast
        }

        val incoming = IncomingNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            body = body,
            sender = sender,
            postedAt = sbn.postTime
        )

        val id = graph.store.upsertIncoming(incoming, initial, vip)

        // Automatic OTP copy feature
        val otpCode = ActionExtractor.extractOtpCode(title, body)
        if (otpCode != null && graph.preferences.autoCopyOtp &&
            (initial.category == NotificationCategory.OTP || fast.category == NotificationCategory.OTP)
        ) {
            handleAutoCopyOtp(otpCode, appName, id)
        }

        val refined = graph.gemma.refine(
            appName = appName,
            title = title,
            body = body,
            fallback = initial
        )

        if (refined != null) {
            val personalized = applyPersonalization(
                refined,
                sbn.packageName,
                sender
            )

            graph.store.updateClassification(
                id,
                if (vip) {
                    personalized.copy(priority = NotificationPriority.HIGH)
                } else {
                    personalized
                }
            )
        }

        val stored = graph.store.get(id) ?: return

        val quietBySource = sbn.packageName in graph.preferences.quietPackages()
        val quietByCategory = stored.category in graph.preferences.quietCategories()

        // Destructive quieting only after final stored classification.
        // Never quiet VIP/high/normal items. Require confidence >= 0.90 and safe low-value category.
        val safeLowValueCategory = stored.category in setOf(
            NotificationCategory.PROMOTION,
            NotificationCategory.SPAM,
            NotificationCategory.SOCIAL
        )

        val shouldQuiet =
            (quietBySource || quietByCategory) &&
            !stored.isVip &&
            LocalIntelligence.effectivePriority(stored) == NotificationPriority.LOW &&
            stored.confidence >= 0.90f &&
            safeLowValueCategory

        if (shouldQuiet) {
            runCatching { cancelNotification(sbn.key) }
        }
    }

    private fun handleAutoCopyOtp(code: String, appName: String, notificationId: Long) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Verification Code", code).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                }
                clipboard?.setPrimaryClip(clip)
            }
        }

        if (NotificationPosting.canPost(this, NotificationChannels.OTP_ALERTS)) {
            val tapIntent = PendingIntent.getActivity(
                this,
                notificationId.toInt(),
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("notification_id", notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alert = NotificationCompat.Builder(this, NotificationChannels.OTP_ALERTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("✓ OTP Auto-Copied: $code")
                .setContentText("Verification code from $appName is in your clipboard")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(30_000L)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify((notificationId + 90000L).toInt(), alert)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        scope.launch {
            appGraph.store.markRemoved(sbn.key)
        }
    }

    private fun applyPersonalization(
        classification: ClassificationResult,
        packageName: String,
        sender: String?
    ): ClassificationResult {
        val learned = appGraph.preferences.matchingPreference(
            packageName,
            sender
        ) ?: return classification

        return classification.copy(
            category = learned.category ?: classification.category,
            priority = learned.priority ?: classification.priority,
            confidence = maxOf(classification.confidence, 0.96f),
            reason = "Personal preference"
        )
    }

    private fun friendlyPackageFallback(packageName: String): String {
        return packageName
            .substringAfterLast('.')
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
            .ifBlank { packageName }
    }

    companion object {
        @Volatile
        private var instance: NotiFlowNotificationListener? = null

        fun openOriginal(
            context: Context,
            notificationKey: String,
            sourcePackage: String
        ): Boolean {
            val service = instance

            // Best option: original notification's content intent.
            val openedOriginal = service?.let {
                val sbn = runCatching {
                    it.activeNotifications.firstOrNull { n ->
                        n.key == notificationKey
                    }
                }.getOrNull()

                val pendingIntent = sbn?.notification?.contentIntent
                if (pendingIntent != null) {
                    try {
                        pendingIntent.send()
                        true
                    } catch (_: PendingIntent.CanceledException) {
                        false
                    }
                } else {
                    false
                }
            } == true

            if (openedOriginal) return true

            // Fallback: front-door source app.
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val sender = context.packageManager.getLaunchIntentSenderForPackage(sourcePackage)
                    sender.sendIntent(context, 0, null, null, null)
                } else {
                    val intent = context.packageManager.getLaunchIntentForPackage(sourcePackage)
                        ?: return@runCatching false
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                true
            }.getOrDefault(false)
        }
    }
}

