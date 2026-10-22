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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
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
import com.premraj.notiflow.data.AutomationAction
import com.premraj.notiflow.data.MediaSaver
import com.premraj.notiflow.data.NotificationCategory
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NotiFlowViewModel,
    listenerEnabled: Boolean,
    onOpenPrivacy: () -> Unit,
    onOpenSavedMedia: () -> Unit,
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
    var ruleDialog by remember { mutableStateOf(false) }
    var ruleKeyword by remember { mutableStateOf("") }
    var rulePackage by remember { mutableStateOf<String?>(null) }
    var ruleAction by remember { mutableStateOf(AutomationAction.HIGH_PRIORITY) }
    var ruleAppMenu by remember { mutableStateOf(false) }
    var mediaPermissionDenied by remember { mutableStateOf(false) }
    var mediaActionConfirmClear by remember { mutableStateOf(false) }
    var mediaActionScanBusy by remember { mutableStateOf(false) }
    var mediaStatusMessage by remember { mutableStateOf<String?>(null) }

    val appNames = remember(observedApps) { observedApps.toMap() }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Partial photo access ("select photos") denies the full permissions but grants
        // READ_MEDIA_VISUAL_USER_SELECTED, so only the shared access check decides.
        if (MediaSaver.hasMediaReadAccess(context)) {
            mediaPermissionDenied = false
            viewModel.setSaveMediaEnabled(true)
        } else {
            viewModel.setSaveMediaEnabled(false)
            mediaPermissionDenied = true
        }
    }

    LaunchedEffect(mediaActionScanBusy) {
        if (mediaActionScanBusy) {
            viewModel.runMediaScanNow()
            mediaActionScanBusy = false
        }
    }

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
                                restoreMessage = "Restored ${summary.notificationsImported} notifications, ${summary.vipRulesImported} VIP rules, and ${summary.automationRulesImported} automation rules."
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

    if (mediaActionConfirmClear) {
        AlertDialog(
            onDismissRequest = { mediaActionConfirmClear = false },
            title = { Text("Delete all saved media?") },
            text = {
                Text("This permanently removes every private photo/video copy inside NotiFlow. The originals in WhatsApp's folders are untouched.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSavedMedia()
                        mediaActionConfirmClear = false
                        mediaStatusMessage = "Saved media deleted."
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { mediaActionConfirmClear = false }) { Text("Cancel") }
            }
        )
    }

    if (ruleDialog) {
        AlertDialog(
            onDismissRequest = {
                ruleDialog = false
                ruleAppMenu = false
            },
            title = { Text("New automation rule") },
            text = {
                Column {
                    Text("Match new notifications by keyword or phrase, optionally limited to one app.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ruleKeyword,
                        onValueChange = { ruleKeyword = it },
                        label = { Text("Keyword or phrase") },
                        placeholder = { Text("e.g. invoice, server down") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(
                            onClick = { ruleAppMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(rulePackage?.let { appNames[it] ?: it } ?: "Any app")
                        }
                        DropdownMenu(
                            expanded = ruleAppMenu,
                            onDismissRequest = { ruleAppMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Any app") },
                                onClick = {
                                    rulePackage = null
                                    ruleAppMenu = false
                                }
                            )
                            observedApps.forEach { (packageName, appName) ->
                                DropdownMenuItem(
                                    text = { Text(appName) },
                                    onClick = {
                                        rulePackage = packageName
                                        ruleAppMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Action", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AutomationAction.entries) { action ->
                            FilterChip(
                                selected = ruleAction == action,
                                onClick = { ruleAction = action },
                                label = { Text(action.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = ruleAction.description + " Rules run on-device and apply to new alerts only. Auto-archive keeps the alert in History and never dismisses the source app's notification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = ruleKeyword.isNotBlank(),
                    onClick = {
                        viewModel.addAutomationRule(ruleKeyword, rulePackage, ruleAction)
                        ruleDialog = false
                        ruleKeyword = ""
                        rulePackage = null
                        ruleAction = AutomationAction.HIGH_PRIORITY
                        ruleAppMenu = false
                    }
                ) { Text("Create rule") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ruleDialog = false
                        ruleKeyword = ""
                        rulePackage = null
                        ruleAction = AutomationAction.HIGH_PRIORITY
                        ruleAppMenu = false
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Group 1: General & Theme
            item {
                SettingsGroup(icon = Icons.Outlined.Palette, title = "General & Theme") {
                    Column {
                        Text("Theme mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AppThemeMode.entries) { mode ->
                                FilterChip(
                                    selected = prefs.appThemeMode == mode,
                                    onClick = { viewModel.setAppThemeMode(mode) },
                                    label = { Text(mode.label, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingSwitch(
                        title = "Use dynamic colors",
                        subtitle = "Apply wallpaper-derived Material You colors on supported devices.",
                        checked = prefs.useDynamicColors,
                        onChecked = viewModel::setUseDynamicColors
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingSwitch(
                        title = "Daily scheduled digest",
                        subtitle = "Collect low-priority promotions and social updates into a daily digest.",
                        checked = prefs.digestEnabled,
                        onChecked = viewModel::setDigestEnabled
                    )

                    if (prefs.digestEnabled) {
                        Column {
                            Text("Delivery time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val presets = listOf(8 to "Morning (8 AM)", 15 to "Afternoon (3 PM)", 19 to "Evening (7 PM)")
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
                                        label = { Text("Custom: " + formatTime(prefs.digestHour, prefs.digestMinute)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Group 2: Capture & Focus
            item {
                SettingsGroup(icon = Icons.Outlined.Notifications, title = "Capture & Focus") {
                    SettingStatusRow(
                        title = "Notification capture access",
                        detail = if (listenerEnabled) "Active · NotiFlow is actively capturing notifications."
                        else "Off · Alerts cannot be captured until granted in Android settings.",
                        positive = listenerEnabled,
                        actionButton = {
                            if (!listenerEnabled) {
                                Button(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Grant Notification Access") }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Review System Access") }
                            }
                        }
                    )

                    if (!postGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        SettingStatusRow(
                            title = "Background alerts permission",
                            detail = "Required to deliver reminder alarms and scheduled digests.",
                            positive = false,
                            actionButton = {
                                OutlinedButton(
                                    onClick = { postPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Allow Alerts Permission") }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    Column {
                        Text("Quiet categories", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Auto-dismiss from Android status shade after NotiFlow captures them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // VIP Rules
                    Column {
                        val vipRules = prefs.vipRules().toList()
                        var vipAppMenu by remember { mutableStateOf(false) }
                        val unconfiguredApps = remember(observedApps, vipRules) {
                            observedApps.filter { (pkg, _) -> pkg !in vipRules && !vipRules.any { r -> r.startsWith("$pkg|") } }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VIP Rules (${vipRules.size})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "VIP contacts/apps stay high priority and are shielded from quiet dismissal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (vipRules.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            vipRules.forEach { rule ->
                                val pkg = rule.substringBefore('|')
                                val sender = rule.substringAfter('|', "").takeIf { it.isNotBlank() }
                                val appTitle = appNames[pkg] ?: pkg
                                val displayLabel = if (sender != null) "$sender · $appTitle" else appTitle
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        displayLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.removeVipRule(rule) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Remove VIP", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (unconfiguredApps.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { vipAppMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Add VIP App")
                                }
                                DropdownMenu(
                                    expanded = vipAppMenu,
                                    onDismissRequest = { vipAppMenu = false }
                                ) {
                                    unconfiguredApps.forEach { (pkg, appName) ->
                                        DropdownMenuItem(
                                            text = { Text(appName) },
                                            onClick = {
                                                viewModel.setVipApp(pkg, true)
                                                vipAppMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Automation Rules
                    Column {
                        val rules = prefs.automationRules()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Automation Rules (${rules.size})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Match incoming notifications by keyword to auto-archive, pin, or highlight.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (rules.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            rules.forEach { rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("“${rule.keyword}”", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            listOfNotNull(
                                                rule.action.label,
                                                rule.packageName?.let { appNames[it] ?: it } ?: "Any app"
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { viewModel.setAutomationRuleEnabled(rule.id, it) }
                                    )
                                    IconButton(onClick = { viewModel.removeAutomationRule(rule.id) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete rule", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { ruleDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Add Automation Rule") }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // WhatsApp Media Saver
                    Column {
                        val mediaStats = viewModel.savedMediaStats.collectAsStateWithLifecycle().value
                        val vmMediaMessage = viewModel.mediaScanMessage.collectAsStateWithLifecycle().value
                        val permissionReady = MediaSaver.hasMediaReadAccess(context)
                        val statusText = mediaStatusMessage ?: vmMediaMessage ?: run {
                            when {
                                prefs.saveMediaEnabled && !permissionReady -> "Media saving is enabled but photo/video permission is revoked."
                                mediaPermissionDenied -> "Photo/video access was denied. Grant permission in Android settings."
                                else -> null
                            }
                        }

                        SettingSwitch(
                            title = "Save WhatsApp Media",
                            subtitle = "Preserve offline copies of incoming photos and videos in private storage.",
                            checked = prefs.saveMediaEnabled,
                            onChecked = { requested ->
                                if (requested) {
                                    if (permissionReady) {
                                        mediaPermissionDenied = false
                                        viewModel.setSaveMediaEnabled(true)
                                    } else {
                                        mediaPermissionLauncher.launch(MediaSaver.requestedPermissions())
                                    }
                                } else {
                                    viewModel.setSaveMediaEnabled(false)
                                    mediaPermissionDenied = false
                                }
                            }
                        )

                        if (prefs.saveMediaEnabled) {
                            Spacer(Modifier.height(6.dp))
                            SettingStatusRow(
                                title = "Saved media files",
                                detail = if (mediaStats.first == 0) "0 files" else "${mediaStats.first} files · ${formatBytes(mediaStats.second)}",
                                positive = mediaStats.first > 0
                            )
                            if (statusText != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (statusText.startsWith("Media saving is enabled") || statusText.startsWith("Photo/video")) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            if (mediaPermissionDenied) {
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Open App Settings") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { mediaActionScanBusy = true },
                                    enabled = permissionReady && !mediaActionScanBusy,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(if (mediaActionScanBusy) "Scanning…" else "Scan Now") }

                                OutlinedButton(
                                    onClick = onOpenSavedMedia,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("View Media") }
                            }
                            if (mediaStats.first > 0) {
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = { mediaActionConfirmClear = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Clear All Saved Media") }
                            }
                        }
                    }
                }
            }

            // Group 3: Privacy & Security
            item {
                SettingsGroup(icon = Icons.Outlined.PrivacyTip, title = "Privacy & Security") {
                    SettingSwitch(
                        title = "Auto-copy verification codes",
                        subtitle = "Automatically copy 2FA OTP codes to clipboard upon notification arrival.",
                        checked = prefs.autoCopyOtp,
                        onChecked = viewModel::setAutoCopyOtp
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingSwitch(
                        title = "Hide sensitive previews",
                        subtitle = "Mask OTP verification codes and financial account details in alert lists.",
                        checked = prefs.hideSensitivePreviews,
                        onChecked = viewModel::setHideSensitivePreviews
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingSwitch(
                        title = "Smart Bank & UPI Expense Tracker",
                        subtitle = "Derive offline expense transaction summaries from bank SMS and UPI notifications.",
                        checked = prefs.expenseTrackerEnabled,
                        onChecked = viewModel::setExpenseTrackerEnabled
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingSwitch(
                        title = "App lock",
                        subtitle = if (deviceSecure) "Require your device screen lock or fingerprint when opening NotiFlow."
                        else "Set up an Android screen lock first to enable app lock.",
                        checked = prefs.appLockEnabled,
                        enabled = deviceSecure,
                        onChecked = viewModel::setAppLockEnabled
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    SettingSwitch(
                        title = "Automatic history cleanup",
                        subtitle = "Remove stale alerts older than retention period while preserving pinned and reminder items.",
                        checked = prefs.autoCleanupEnabled,
                        onChecked = viewModel::setAutoCleanup
                    )

                    if (prefs.autoCleanupEnabled) {
                        Column {
                            Text("Retention period", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(7, 30, 90, 180)) { days ->
                                    FilterChip(
                                        selected = prefs.retentionDays == days,
                                        onClick = { viewModel.setRetentionDays(days) },
                                        label = { Text("$days days") }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.runCleanupNow() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Clean Stale History Now") }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    OutlinedButton(
                        onClick = onOpenPrivacy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View Privacy Policy")
                    }
                }
            }

            // Group 4: Advanced & Diagnostics
            item {
                SettingsGroup(icon = Icons.Outlined.Storage, title = "Advanced & Diagnostics") {
                    SettingStatusRow(
                        title = "Encrypted Local Storage",
                        detail = "${storage.first} notifications stored · ${formatBytes(storage.second)}",
                        positive = storage.first > 0
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { backupDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Export Backup") }

                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Restore") }
                    }

                    restoreMessage?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Service connection & diagnostics
                    val connectionText = when {
                        !listenerEnabled -> "Permission Revoked"
                        isServiceConnected -> "Connected & Active"
                        else -> "Disconnected (Standby)"
                    }
                    val connectionPositive = listenerEnabled && isServiceConnected
                    SettingStatusRow(
                        title = "Listener service status",
                        detail = connectionText,
                        positive = connectionPositive
                    )

                    val lastCapturedText = remember(lastCapturedTimestamp) {
                        formatCapturedTimestamp(lastCapturedTimestamp)
                    }
                    Text(
                        "Last notification: $lastCapturedText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val rebindRequested = NotiFlowNotificationListener.requestRebindSafely(context)
                                rebindStatusMessage = if (rebindRequested) {
                                    "Service rebind requested."
                                } else {
                                    "Rebind throttled (cooldown active). Toggle notification access if issues persist."
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reconnect")
                        }

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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (exportingDiagnostics) "Exporting…" else "Diagnostics")
                        }
                    }

                    rebindStatusMessage?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    Text(
                        "Diagnostics contain 0% PII. Strictly on-device service health & metadata for troubleshooting.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Learned preferences
                    val learned = prefs.learnedPreferences()
                    if (learned.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        Column {
                            Text("Learned Corrections (${learned.size})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            learned.take(15).forEach { rule ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(rule.packageName, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            listOfNotNull(rule.sender, rule.category?.label, rule.priority?.label).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeLearnedPreference(rule.key) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete rule", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    // Danger zone
                    OutlinedButton(
                        onClick = { clearConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete all NotiFlow history")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onChecked(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun SettingStatusRow(
    title: String,
    detail: String,
    positive: Boolean,
    actionButton: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MetaPill(
                text = if (positive) "Ready" else "Needs attention",
                emphasized = positive,
                isWarning = !positive
            )
        }
        if (actionButton != null) {
            Spacer(Modifier.height(8.dp))
            actionButton()
        }
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
