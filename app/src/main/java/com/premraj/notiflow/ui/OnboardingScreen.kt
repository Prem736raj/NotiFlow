package com.premraj.notiflow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.systemGesturesPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.premraj.notiflow.R
import com.premraj.notiflow.data.MetricsTracker
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class OnboardingPage(
    val icon: ImageVector?,
    val accent: Color,
    val title: String,
    val body: String,
    val chips: List<String>
)

private val OnboardingPages = listOf(
    OnboardingPage(
        icon = null,
        accent = Color(0xFF6C8DFF),
        title = "Important now. Deal with some later.",
        body = "NotiFlow turns your noisy notification bar into a calm, private inbox. Alerts worth keeping are organized automatically — the rest waits quietly for you.",
        chips = listOf("Local-first inbox", "Rules-first intelligence", "No cloud AI")
    ),
    OnboardingPage(
        icon = Icons.Outlined.Notifications,
        accent = Color(0xFF7B9BFF),
        title = "Never lose an alert again",
        body = "Payment, OTP, VIP, and work notifications are sorted into categories the moment they arrive. Search anything you missed with one tap.",
        chips = listOf("Auto categories", "VIP priority", "Instant search")
    ),
    OnboardingPage(
        icon = Icons.Outlined.Schedule,
        accent = Color(0xFF5BC5FF),
        title = "Deal with it later",
        body = "Turn any notification into a reminder, snooze the noise, and get an optional daily digest. Your status bar stays clean; nothing is lost.",
        chips = listOf("One-tap reminders", "Daily digest", "Smart snooze")
    ),
    OnboardingPage(
        icon = Icons.Outlined.Security,
        accent = Color(0xFF64D2A8),
        title = "Private by design",
        body = "Everything runs on your device — no cloud AI, no ads, and no data sale. Encrypted, password-protected backups keep your archive in your hands.",
        chips = listOf("On-device only", "AES-GCM export", "No ads, no sale")
    )
)

private const val PAGE_PERMISSIONS = 4
private const val PAGE_COUNT = 5

@Composable
fun OnboardingScreen(
    listenerEnabled: Boolean,
    onOpenPrivacy: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        MetricsTracker.recordEvent(context, MetricsTracker.EVENT_DISCLOSURE_SHOWN)
    }

    var postGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        postGranted = it
    }

    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var detailsExpanded by remember { mutableStateOf(false) }

    // When the user returns from the system settings having enabled access,
    // immediately ask for alerts so the flow is: access -> alerts -> start.
    LaunchedEffect(listenerEnabled, pagerState.currentPage) {
        if (listenerEnabled && !postGranted && pagerState.currentPage == PAGE_PERMISSIONS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val targetAccent = when {
        pagerState.currentPage == PAGE_PERMISSIONS -> Color(0xFF8AA6FF)
        else -> OnboardingPages[pagerState.currentPage].accent
    }
    val accent by animateColorAsState(targetAccent, tween(350), label = "accent")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Soft animated glow blobs behind everything
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.08f),
                    radius = w * 0.9f
                ),
                center = Offset(w * 0.85f, h * 0.08f),
                radius = w * 0.9f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lerp(accent, Color(0xFF64D2A8), 0.35f).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * 0.05f, h * 0.95f),
                    radius = w * 1.1f
                ),
                center = Offset(w * 0.05f, h * 0.95f),
                radius = w * 1.1f
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .systemGesturesPadding()
        ) {
            // Top row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0 && pagerState.currentPage < PAGE_PERMISSIONS) {
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage < PAGE_PERMISSIONS) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(PAGE_PERMISSIONS) }
                    }) { Text("Get started") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val fraction = 1f - abs(pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                if (page < PAGE_PERMISSIONS) {
                    IntroPage(
                        page = OnboardingPages[page],
                        fraction = fraction
                    )
                } else {
                    PermissionsPage(
                        listenerEnabled = listenerEnabled,
                        postGranted = postGranted,
                        detailsExpanded = detailsExpanded,
                        onToggleDetails = { detailsExpanded = !detailsExpanded },
                        onOpenPrivacy = onOpenPrivacy,
                        onEnableListener = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        },
                        onAllowAlerts = {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onComplete = onComplete
                    )
                }
            }

            // Dots + CTA
            Column(Modifier.padding(horizontal = 28.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(PAGE_COUNT) { index ->
                        val selected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            if (selected) 26.dp else 8.dp,
                            tween(250),
                            label = "dot"
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .height(8.dp)
                                .clip(CircleShape)
                                .size(width = width, height = 8.dp)
                                .background(
                                    if (selected) accent
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                val onLast = pagerState.currentPage == PAGE_PERMISSIONS
                Button(
                    onClick = {
                        if (onLast) {
                            onComplete()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (onLast) accent else MaterialTheme.colorScheme.primary,
                        contentColor = if (onLast) Color.White else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        if (onLast) {
                            if (listenerEnabled && postGranted) "Start NotiFlow" else "Continue to NotiFlow"
                        } else if (pagerState.currentPage == PAGE_PERMISSIONS - 1) {
                            "Set up access"
                        } else {
                            "Next"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onLast) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can continue without access. Features that depend on permissions will clearly explain what is unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun IntroPage(page: OnboardingPage, fraction: Float) {
    val scale = 0.8f + 0.2f * fraction
    val alpha = 0.35f + 0.65f * fraction

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(page.accent, page.accent.copy(alpha = 0.55f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (page.icon != null) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "NotiFlow Logo",
                    modifier = Modifier.size(92.dp)
                )
            }
        }

        Spacer(Modifier.height(30.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            page.chips.forEach { chip ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
                ) {
                    Text(
                        chip,
                        style = MaterialTheme.typography.labelMedium,
                        color = page.accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsPage(
    listenerEnabled: Boolean,
    postGranted: Boolean,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onEnableListener: () -> Unit,
    onAllowAlerts: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Get NotiFlow ready",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Two quick steps. Takes about 30 seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        StepCard(
            stepNumber = 1,
            title = "Notification access",
            icon = Icons.Outlined.Notifications,
            accentColor = Color(0xFF7B9BFF),
            done = listenerEnabled
        ) {
            if (!listenerEnabled) {
                Text(
                    "NotiFlow reads notification titles, message text, and sender/app information from enabled apps to build your local inbox. Content stays on this device and is never sent to a cloud AI service by this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onToggleDetails, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(if (detailsExpanded) "Show less" else "More details")
                }
                if (detailsExpanded) {
                    Text(
                        "Depending on the apps you enable, this can include private messages, verification codes, and banking or payment alerts. Expense parsing and OTP auto-copy are separate opt-in features. You can revoke access at any time in Android settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onOpenPrivacy, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Read full Privacy Policy")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onEnableListener,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7B9BFF),
                        contentColor = Color.White
                    )
                ) {
                    Text("Enable notification access", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        StepCard(
            stepNumber = 2,
            title = "NotiFlow alerts",
            icon = if (postGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
            accentColor = Color(0xFF5BC5FF),
            done = postGranted,
            locked = !listenerEnabled && !postGranted
        ) {
            if (!postGranted) {
                if (!listenerEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Enable step 1 first — then this opens automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Needed for scheduled reminders and daily digest alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAllowAlerts,
                    enabled = listenerEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Allow NotiFlow alerts", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    icon: ImageVector,
    accentColor: Color,
    done: Boolean,
    locked: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (done) {
                                Brush.linearGradient(listOf(Color(0xFF4CC58F), Color(0xFF2E9E74)))
                            } else {
                                Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.6f)))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$stepNumber · $title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        when {
                            done -> if (stepNumber == 1) "Enabled — NotiFlow can build your inbox" else "Enabled — reminders and digests can reach you"
                            locked -> "Unlocks after step 1"
                            else -> "Not enabled yet"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) Color(0xFF4CC58F) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!done) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}
