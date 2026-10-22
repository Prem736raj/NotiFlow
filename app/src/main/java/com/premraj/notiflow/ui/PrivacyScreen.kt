package com.premraj.notiflow.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    "NotiFlow Privacy Policy",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Effective October 22, 2026 · Developer: Prem Raj · com.premraj.notiflow",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text(
                    "NotiFlow is an on-device notification inbox. Notification capture, classification, search, reminders, and expense parsing run locally. NotiFlow has no internet permission and does not send notification content to a cloud service.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                PolicySection("1. Data NotiFlow can access") {
                    Text(
                        "After you grant Android Notification Access, NotiFlow can read notifications from apps you enable. Depending on those apps, this may include the app name, notification title and text, sender information, timestamps, verification codes, and transaction details."
                    )
                }
            }

            item {
                PolicySection("2. How data is used") {
                    Text(
                        "Captured content is used only to provide the features you choose: local history, rules-based categories and priority, keyword automation rules, search, reminders, optional OTP clipboard copying, optional expense tracking, and optional encrypted export. Automation rules run on-device and only act on notifications you configure. Notification content is never used for advertising or sold to third parties."
                    )
                }
            }

            item {
                PolicySection("3. Storage and security") {
                    Text(
                        "Notification records, automation rules, and derived expense rows are stored in NotiFlow's private SQLite database and preferences. Android cloud backup is disabled. Encrypted exports include notification records, VIP rules, and automation rules; they use AES-GCM with a key derived from your passphrase and are shared only through Android's file provider when you choose to export. Optional app lock uses your device's existing screen lock; NotiFlow does not store a separate password. The optional home-screen widget displays alert counts only and never notification titles or message content."
                    )
                }
            }

            item {
                PolicySection("4. Retention and your controls") {
                    Text(
                        "You can exclude apps, configure retention, hide sensitive previews, delete individual items, or delete all history and derived expenses at any time. Pinned items and active reminders are protected from automatic cleanup. You can revoke Notification Access in Android settings at any time."
                    )
                }
            }

            item {
                PolicySection("5. Sharing and children") {
                    Text(
                        "NotiFlow does not sell, rent, or transmit your notification content to advertisers, data brokers, or other third parties. It is a general-purpose utility not directed to children under 13."
                    )
                }
            }

            item {
                PolicySection("6. Contact") {
                    Text(
                        "Questions about this policy or your data can be sent to prem736raj@gmail.com."
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:prem736raj@gmail.com")
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Email the developer")
                    }
                }
            }

            item {
                Text(
                    "This policy is also embedded in the app so it remains available offline. If the policy changes, the effective date above will be updated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
