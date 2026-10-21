package com.premraj.notiflow.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import com.premraj.notiflow.service.NotiFlowNotificationListener
import com.premraj.notiflow.util.DiagnosticsExporter
import java.text.SimpleDateFormat
import java.util.Date
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.premraj.notiflow.data.AppThemeMode
import com.premraj.notiflow.data.NotificationCategory
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NotiFlowViewModel,
    listenerEnabled: Boolean,
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val observedApps by viewModel.observedApps.collectAsStateWithLifecycle()
    val storage by viewModel.storageStats.collectAsStateWithLifecycle()
    val prefVersion by viewModel.preferencesVersion.collectAsStateWithLifecycle()
    val isServiceConnected by NotiFlowNotificationListener.isServiceConnected.collectAsStateWithLifecycle()
    val lastCapturedTimestamp by NotiFlowNotificationListener.lastCapturedTimestamp.collectAsStateWithLifecycle()
    val prefs = viewModel.preferences
    val deviceSecure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    val scope = rememberCoroutineScope()

    var rebindStatusMessage by remember { mutableStateOf<String?>(null) }
    var exportingDiagnostics by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var backupDialog by remember { mutableStateOf(false) }
    var backupPassphrase by remember { mutableStateOf("") }
    var archiveCreating by remember { mutableStateOf(false) }
    var archiveError by remember { mutableStateOf<String?>(null) }
    var restoreDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }
    var restoring by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            restoreUri = uri
            restorePassphrase = ""
            restoreError = null
            restoreDialog = true
        }
    }
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
            text = { Text("This permanently removes NotiFlow's local notification records and derived expense rows. Source apps are not affected.") },
            confirmButton = { TextButton(onClick = { clearConfirm = false; viewModel.clearHistory() }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancel") } }
        )
    }

    if (backupDialog) {
        AlertDialog(
            onDismissRequest = {
                if (archiveCreating) return@AlertDialog
                backupDialog = false
                backupPassphrase = ""
                archiveError = null
            },
            title = { Text("Protect encrypted archive") },
            text = {
                Column {
                    Text("Create a passphrase with at least 12 characters. You will need the same passphrase to decrypt this exported archive.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Archive passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    archiveError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = backupPassphrase.length >= 12 && !archiveCreating,
                    onClick = {
                        val password = backupPassphrase
                        archiveCreating = true
                        archiveError = null
                        scope.launch {
                            runCatching { viewModel.createEncryptedArchiveFile(context, password) }
                                .onSuccess { backupFile ->
                                    backupPassphrase = ""
                                    backupDialog = false
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        backupFile
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_SUBJECT, "NotiFlow Encrypted Archive")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri("NotiFlow Encrypted Archive", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(sendIntent, "Export Encrypted Archive").apply {
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(chooser)
                                }
                                .onFailure { error ->
                                    archiveError = error.message ?: "Could not create encrypted archive."
                                }
                            archiveCreating = false
                        }
                    }
                ) { Text(if (archiveCreating) "Creating…" else "Create archive") }
            },
            dismissButton = {
                TextButton(
                    enabled = !archiveCreating,
                    onClick = {
                        backupDialog = false
                        backupPassphrase = ""
                        archiveError = null
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (restoreDialog) {
        AlertDialog(
            onDismissRequest = {
                if (restoring) return@AlertDialog
                restoreDialog = false
                restorePassphrase = ""
                restoreError = null
            },
            title = { Text("Restore encrypted archive") },
            text = {
                Column {
                    Text("Enter the passphrase used when this archive was exported. Existing history is kept and duplicate notification keys are skipped. Reminder schedules are not recreated.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("Archive passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    restoreError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restorePassphrase.isNotBlank() && !restoring,
                    onClick = {
                        val uri = restoreUri ?: return@TextButton
                        val password = restorePassphrase
                        restoring = true
                        restoreError = null
                        scope.launch {
                            runCatching {
                                viewModel.restoreEncryptedArchive(context, uri, password)
                            }.onSuccess { summary ->
                                restorePassphrase = ""
                                restoreDialog = false
                                restoreMessage = "Restored ${summary.notificationsImported} notifications and ${summary.vipRulesImported} VIP rules."
                            }.onFailure { error ->
                                restoreError = error.message ?: "Could not restore this archive."
                            }
                            restoring = false
                        }
                    }
                ) { Text(if (restoring) "Restoring…" else "Restore") }
            },
            dismissButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = {
                        restoreDialog = false
                        restorePassphrase = ""
                        restoreError = null
                    }
                ) { Text("Cancel") }
            }
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
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        },
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
                SettingsCard(Icons.Outlined.Security, "System & Capture Health") {
                    val connectionStatusText = when {
                        !listenerEnabled -> "Permission Revoked"
                        isServiceConnected -> "Connected & Active"
                        else -> "Disconnected (Standby)"
                    }
                    val connectionPositive = listenerEnabled && isServiceConnected

                    SettingStatus(
                        "Listener connection status",
                        connectionStatusText,
                        connectionPositive
                    )

                    Spacer(Modifier.height(10.dp))

                    val lastCapturedText = remember(lastCapturedTimestamp) {
                        formatCapturedTimestamp(lastCapturedTimestamp)
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Last notification captured", fontWeight = FontWeight.Medium)
                            Text(lastCapturedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val rebindRequested = NotiFlowNotificationListener.requestRebindSafely(context)
                            rebindStatusMessage = if (rebindRequested) {
                                "Service rebind requested."
                            } else {
                                "Rebind throttled (cooldown active). Toggle notification access if issues persist."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reconnect Service")
                    }

                    rebindStatusMessage?.let { msg ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            exportingDiagnostics = true
                            scope.launch {
                                runCatching {
                                    val file = DiagnosticsExporter.exportDiagnosticsToFile(context)
                                    DiagnosticsExporter.shareDiagnostics(context, file)
                                }
                                exportingDiagnostics = false
                            }
                        },
                        enabled = !exportingDiagnostics,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (exportingDiagnostics) "Generating report…" else "Export & Share Diagnostic Report (0% PII)")
                    }

                    Text(
                        text = "Consent-based diagnostic report contains zero notification titles, message bodies, senders, or OTP codes. Strictly on-device health & error metadata.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item {
                SettingsCard(Icons.Outlined.Palette, "Appearance & Theme") {
                    Text(
                        "Choose how NotiFlow itself should look. Device wallpaper colors are optional so the app keeps a consistent identity by default.",
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
                    SettingSwitch(
                        title = "Use device colors",
                        subtitle = "Apply Android's wallpaper-derived Material You palette. AMOLED keeps pure black backgrounds.",
                        checked = prefs.useDynamicColors,
                        onChecked = viewModel::setUseDynamicColors
                    )
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
                        "VIP contacts and applications are elevated to High priority and protected from NotiFlow's current quiet-source cancellation policy.",
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
                SettingsCard(Icons.Outlined.PrivacyTip, "Privacy & retention") {
                    SettingSwitch(
                        title = "Auto-copy verification codes",
                        subtitle = "Automatically copy OTPs to clipboard when notifications arrive.",
                        checked = prefs.autoCopyOtp,
                        onChecked = viewModel::setAutoCopyOtp
                    )
                    SettingSwitch(
                        title = "App lock",
                        subtitle = if (deviceSecure) {
                            "Require your device screen lock whenever you return to NotiFlow."
                        } else {
                            "Set a device screen lock first, then enable app lock here."
                        },
                        checked = prefs.appLockEnabled,
                        enabled = deviceSecure,
                        onChecked = viewModel::setAppLockEnabled
                    )
                    Text(
                        "Captured notification history remains available after a source notification disappears. Android removal callbacks do not reliably prove that a sender used “Delete for everyone”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                    SettingSwitch(
                        title = "Smart Bank & UPI Expense Tracker",
                        subtitle = "When enabled, derive supported transaction records from new notifications on-device. Turning this off stops new parsing; existing derived rows remain until you delete history.",
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

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenPrivacy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Privacy Policy")
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
                        onClick = { backupDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Export password-protected archive", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore encrypted archive")
                    }

                    restoreMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
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
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
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

private fun formatCapturedTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "No notifications captured in this session yet"
    return SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.US).format(Date(timestamp))
}
