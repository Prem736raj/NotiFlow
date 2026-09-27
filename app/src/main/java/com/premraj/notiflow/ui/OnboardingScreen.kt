package com.premraj.notiflow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.premraj.notiflow.R
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun OnboardingScreen(
    listenerEnabled: Boolean,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var postGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        postGranted = it
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "NotiFlow Logo",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.height(16.dp))
            Text("NotiFlow", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Important now. Deal with some later. Digest the noise.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            FeatureCard(Icons.Outlined.Notifications, "Intelligent inbox", "Keep useful notification history and surface what deserves attention.")
            Spacer(Modifier.height(12.dp))
            FeatureCard(Icons.Outlined.Schedule, "Deal with it later", "Turn any notification into a reminder instead of leaving it in your status bar.")
            Spacer(Modifier.height(12.dp))
            FeatureCard(Icons.Outlined.AutoAwesome, "Local rules-first intelligence", "Fast deterministic rules work without a model, network download, or cloud AI service.")

            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, contentDescription = null)
                        Text(" Privacy by default", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Captured notification content is stored locally in NotiFlow's app data. Core classification does not need a cloud AI API, and no AI model is downloaded automatically. You can clear local history from Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("1 · Notification access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (listenerEnabled) "Enabled — NotiFlow can build your inbox."
                else "Notification Access lets NotiFlow read notification titles, message text, sender/app information, and other notification fields from enabled apps. This can include private messages, verification codes, and banking or payment alerts. NotiFlow uses that content to build your local inbox and rules-based features; it is not sent to a cloud AI service by this app. Expense parsing and OTP auto-copy are separate opt-in features.",
                color = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (listenerEnabled) "Review notification access" else "Enable notification access")
            }

            Spacer(Modifier.height(20.dp))
            Text("2 · NotiFlow alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (postGranted) "Enabled — reminders and digests can reach you."
                else "Optional, but needed for scheduled reminders and digest alerts.",
                color = if (postGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!postGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Allow NotiFlow alerts") }
            }

            Spacer(Modifier.height(28.dp))
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                Text("Continue to NotiFlow")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "You can continue without access. Features that depend on permissions will clearly explain what is unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, message: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null)
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
