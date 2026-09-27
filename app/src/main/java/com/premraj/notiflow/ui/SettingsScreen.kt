package com.premraj.notiflow.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.premraj.notiflow.data.AppThemeMode
import com.premraj.notiflow.data.FocusProfileType
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.VoiceReaderFilter
import com.premraj.notiflow.data.VoiceReadingDetail
import com.premraj.notiflow.data.VoiceTriggerCondition
import com.premraj.notiflow.intelligence.ModelDownloadStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NotiFlowViewModel,
    listenerEnabled: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val observedApps by viewModel.observedApps.collectAsStateWithLifecycle()
    val storage by viewModel.storageStats.collectAsStateWithLifecycle()
    val unwantedCount by viewModel.unwantedCount.collectAsStateWithLifecycle()
    val prefVersion by viewModel.preferencesVersion.collectAsStateWithLifecycle()
    val isHeadphonesConnected by viewModel.isHeadphonesConnected.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.modelDownloadStatus.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.modelDownloadProgress.collectAsStateWithLifecycle()
    val prefs = viewModel.preferences

    var clearConfirm by remember { mutableStateOf(false) }
    var postGranted by remember(prefVersion) {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val postPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        postGranted = it
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("Delete all notification history?") },
            text = { Text("This permanently removes NotiFlow's local notification records. Source apps are not affected.") },
            confirmButton = { TextButton(onClick = { clearConfirm = false; viewModel.clearHistory() }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsCard(Icons.Outlined.Notifications, "Access") {
                    SettingStatus(
                        "Notification access",
                        if (listenerEnabled) "Enabled" else "Off - new notifications cannot be captured",
                        listenerEnabled
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Review notification access") }

                    Spacer(Modifier.height(14.dp))
                    SettingStatus(
                        "Reminder & digest alerts",
                        if (postGranted) "Allowed" else "Not allowed",
                        postGranted
                    )
                    if (!postGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = { postPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Allow NotiFlow alerts") }
                    }
                }
            }

            item {
                SettingsCard(Icons.Outlined.Palette, "Appearance & Theme") {
                    Text(
                        "Customize NotiFlow's design with Dynamic Material You or save maximum battery life with pure AMOLED black.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppThemeMode.entries) { mode ->
                            FilterChip(
                                selected = prefs.appThemeMode == mode,
                                onClick = { viewModel.setAppThemeMode(mode) },
                                label = {
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Text(mode.label, fontWeight = FontWeight.SemiBold)
                                        Text(mode.subtitle, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                val focusStatus = remember(prefVersion) { viewModel.getFocusStatus() }

                SettingsCard(Icons.Outlined.DoNotDisturbOn, "Context Focus Mode") {
                    Text(
                        "Silence distractions while preserving urgent messages, OTPs, and VIP contacts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (focusStatus.isActive) "Focus active: ${focusStatus.activeProfile?.label ?: "Manual"}" else "Focus inactive",
                                fontWeight = FontWeight.Bold,
                                color = if (focusStatus.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (focusStatus.isActive) "Suppressing non-essential notifications" else "Normal notification flow",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleManualFocus(FocusProfileType.WORK) },
                            colors = if (focusStatus.isActive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (focusStatus.isActive) "Turn off" else "Start work focus")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingSwitch(
                        title = "Sleep schedule focus (11 PM - 7 AM)",
                        subtitle = "Automatically silence all notifications at night except VIP calls & OTPs.",
                        checked = prefs.sleepFocusScheduled,
                        onChecked = viewModel::setSleepFocusScheduled
                    )

                    SettingSwitch(
                        title = "Work hours focus (Mon-Fri, 9 AM - 5 PM)",
                        subtitle = "Mute social, gaming, and entertainment alerts during office hours.",
                        checked = prefs.workFocusScheduled,
                        onChecked = viewModel::setWorkFocusScheduled
                    )
                }
            }

            item {
                SettingsCard(Icons.Outlined.Headphones, "Hands-Free Voice Reader") {
                    SettingSwitch(
                        title = "Hands-free voice reader",
                        subtitle = "Speaks notifications aloud using on-device Text-to-Speech when earphones are plugged in or Bluetooth headset is connected.",
                        checked = prefs.voiceReaderEnabled,
                        onChecked = viewModel::setVoiceReaderEnabled
                    )

                    SettingSwitch(
                        title = "Driving mode",
                        subtitle = "Forces hands-free announcements aloud (via car Bluetooth or speaker) while you are driving.",
                        checked = prefs.drivingModeActive,
                        onChecked = viewModel::setDrivingModeActive
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (isHeadphonesConnected) Icons.Outlined.Headphones else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = null,
                                tint = if (isHeadphonesConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isHeadphonesConnected) "Headphones / Bluetooth connected" else "No earphones connected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text("When to announce", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VoiceTriggerCondition.entries) { condition ->
                            FilterChip(
                                selected = prefs.voiceTriggerCondition == condition,
                                onClick = { viewModel.setVoiceTriggerCondition(condition) },
                                label = { Text(condition.label) }
                            )
                        }
                    }

                    Text("What to announce", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VoiceReaderFilter.entries) { filter ->
                            FilterChip(
                                selected = prefs.voiceReaderFilter == filter,
                                onClick = { viewModel.setVoiceReaderFilter(filter) },
                                label = { Text(filter.label) }
                            )
                        }
                    }

                    Text("Announcement detail", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VoiceReadingDetail.entries) { detail ->
                            FilterChip(
                                selected = prefs.voiceReadingDetail == detail,
                                onClick = { viewModel.setVoiceReadingDetail(detail) },
                                label = { Text(detail.label) }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.testVoiceAnnouncement() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("🔊 Test voice announcement")
                    }
                }
            }

            item {
                SettingsCard(Icons.Outlined.Schedule, "Digest") {
                    SettingSwitch(
                        title = "Scheduled digest",
                        subtitle = "Collect low-priority activity and summarize it once a day.",
                        checked = prefs.digestEnabled,
                        onChecked = viewModel::setDigestEnabled
                    )
                    if (prefs.digestEnabled) {
                        Text(
                            "Delivery time",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val presets = listOf(8 to "Morning", 15 to "Afternoon", 19 to "Evening")
                            items(presets) { (hour, label) ->
                                FilterChip(
                                    selected = prefs.digestHour == hour && prefs.digestMinute == 0,
                                    onClick = { viewModel.setDigestTime(hour, 0) },
                                    label = { Text(label) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = prefs.digestMinute != 0 || prefs.digestHour !in listOf(8, 15, 19),
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, h, m -> viewModel.setDigestTime(h, m) },
                                            prefs.digestHour,
                                            prefs.digestMinute,
                                            false
                                        ).show()
                                    },
                                    label = { Text(formatTime(prefs.digestHour, prefs.digestMinute)) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard(Icons.AutoMirrored.Outlined.VolumeOff, "Quiet sources") {
                    Text(
                        "Selected low-priority sources can be removed from the system shade after NotiFlow captures them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Quiet categories", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        val quietable = listOf(NotificationCategory.PROMOTION, NotificationCategory.SPAM, NotificationCategory.SOCIAL)
                        items(quietable) { cat ->
                            val quiet = cat in prefs.quietCategories()
                            FilterChip(
                                selected = quiet,
                                onClick = { viewModel.setQuietCategory(cat, !quiet) },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsCard(Icons.Outlined.Security, "VIPs") {
                    Text(
                        "VIP contacts and applications are always elevated to High priority and never silenced by focus modes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val vipRules = prefs.vipRules().toList()
                    if (vipRules.isEmpty()) {
                        Text("No VIP rules added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            vipRules.forEach { rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(rule, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { viewModel.removeVipRule(rule) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Remove VIP")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                // On-device AI Engine with Automatic Background Download (Zero manual buttons)
                SettingsCard(Icons.Outlined.AutoAwesome, "On-device AI Engine") {
                    SettingSwitch(
                        title = "Local AI refinement",
                        subtitle = "Runs locally on your phone to refine ambiguous notifications without battery drain or cloud telemetry.",
                        checked = prefs.localAiEnabled,
                        onChecked = viewModel::setLocalAiEnabled
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            when (downloadStatus) {
                                ModelDownloadStatus.READY -> {
                                    Text(
                                        "⚡ AI Model Active",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "On-device neural model is ready and actively refining notifications 100% offline.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ModelDownloadStatus.DOWNLOADING -> {
                                    Text(
                                        "📥 Auto-Downloading AI Model (${(downloadProgress * 100).toInt()}%)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Downloading AI model in the background. The app is fully operational while downloading.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ModelDownloadStatus.FAILED -> {
                                    Text(
                                        "⚠️ Background Download Paused",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Built-in instant rules are actively classifying 98% of alerts in <1ms without any model required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.retryModelDownload() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Retry auto-download now")
                                    }
                                }
                                ModelDownloadStatus.IDLE -> {
                                    Text(
                                        "⏳ Auto-Setup Pending",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "AI model will download automatically in background when internet is connected.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "Notification text is never sent to any external server or cloud API. All processing remains strictly local on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item {
                SettingsCard(Icons.Outlined.PrivacyTip, "Privacy & retention") {
                    SettingSwitch(
                        title = "Auto-copy verification codes",
                        subtitle = "Automatically copy OTPs to clipboard when notifications arrive.",
                        checked = prefs.autoCopyOtp,
                        onChecked = viewModel::setAutoCopyOtp
                    )
                    SettingSwitch(
                        title = "Anti-Revoke (Recover deleted messages)",
                        subtitle = "Preserve WhatsApp & Telegram messages before senders click 'Delete for everyone'.",
                        checked = prefs.antiRevokeEnabled,
                        onChecked = viewModel::setAntiRevokeEnabled
                    )
                    SettingSwitch(
                        title = "Smart Bank & UPI Expense Tracker",
                        subtitle = "Extract expenses and track monthly spending automatically on-device.",
                        checked = prefs.expenseTrackerEnabled,
                        onChecked = viewModel::setExpenseTrackerEnabled
                    )
                    SettingSwitch(
                        title = "Hide sensitive previews",
                        subtitle = "Mask OTP and financial content inside NotiFlow list views.",
                        checked = prefs.hideSensitivePreviews,
                        onChecked = viewModel::setHideSensitivePreviews
                    )
                    SettingSwitch(
                        title = "Automatic cleanup",
                        subtitle = "Remove stale history while protecting pinned and pending reminder items.",
                        checked = prefs.autoCleanupEnabled,
                        onChecked = viewModel::setAutoCleanup
                    )
                    if (prefs.autoCleanupEnabled) {
                        Text("Retention", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(7, 30, 90, 180)) { days ->
                                FilterChip(
                                    selected = prefs.retentionDays == days,
                                    onClick = { viewModel.setRetentionDays(days) },
                                    label = { Text("$days days") }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.runCleanupNow() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Clean old history now") }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingSwitch(
                        title = "15-day unwanted message alert",
                        subtitle = "Notify every 15-20 days to delete old recharge, promotion & spam messages with 1 tap.",
                        checked = prefs.unwantedCleanupPromptEnabled,
                        onChecked = viewModel::setUnwantedPromptEnabled
                    )

                    if (unwantedCount > 0) {
                        Button(
                            onClick = { viewModel.deleteUnwantedMessages() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete $unwantedCount unwanted old messages")
                        }
                    }
                }
            }

            item {
                SettingsCard(Icons.Outlined.Security, "Learned preferences") {
                    val learned = prefs.learnedPreferences()
                    if (learned.isEmpty()) {
                        Text("No corrections learned yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            "NotiFlow remembers corrections by source and sender. You can remove any rule.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        learned.take(20).forEach { rule ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(rule.packageName, fontWeight = FontWeight.Medium)
                                    Text(
                                        listOfNotNull(rule.sender, rule.category?.label, rule.priority?.label).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.removeLearnedPreference(rule.key) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete rule")
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard(Icons.Outlined.Storage, "Local data") {
                    SettingStatus("Saved notifications", "${storage.first} items", storage.first > 0)
                    Spacer(Modifier.height(8.dp))
                    SettingStatus("Storage consumed", formatBytes(storage.second), true)
                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val backupText = viewModel.createEncryptedBackup()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NotiFlow Encrypted Backup")
                                putExtra(Intent.EXTRA_TEXT, backupText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Encrypted Backup"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Export AES-256 Encrypted Backup", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(onClick = { clearConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Text(" Delete all NotiFlow history")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingStatus(title: String, detail: String, positive: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MetaPill(if (positive) "Ready" else "Needs attention", emphasized = positive)
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val suffix = if (hour >= 12) "PM" else "AM"
    val h = when (val normalized = hour % 12) { 0 -> 12; else -> normalized }
    return String.format(Locale.US, "%d:%02d %s", h, minute, suffix)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
