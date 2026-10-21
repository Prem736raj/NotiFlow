package com.premraj.notiflow

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.premraj.notiflow.ui.AppLockScreen
import com.premraj.notiflow.ui.NotiFlowApp
import com.premraj.notiflow.ui.NotiFlowTheme
import com.premraj.notiflow.ui.NotiFlowViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<NotiFlowViewModel>()
    private var requestedNotificationId by mutableStateOf<Long?>(null)
    private var requestedDigest by mutableStateOf(false)
    private var authInFlight by mutableStateOf(false)

    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authInFlight = false
        if (result.resultCode == RESULT_OK) viewModel.unlock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        if (!viewModel.preferences.appLockEnabled) viewModel.unlock()
        setContent {
            val prefVersion by viewModel.preferencesVersion.collectAsStateWithLifecycle()
            val themeMode = remember(prefVersion) { viewModel.preferences.appThemeMode }
            val useDynamicColors = remember(prefVersion) { viewModel.preferences.useDynamicColors }
            val lockEnabled = remember(prefVersion) { viewModel.preferences.appLockEnabled }
            val unlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner, lockEnabled) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && lockEnabled && !isChangingConfigurations) {
                        viewModel.lock()
                        authInFlight = false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            NotiFlowTheme(themeMode = themeMode, useDynamicColor = useDynamicColors) {
                if (lockEnabled && !unlocked) {
                    AppLockScreen(onUnlock = ::requestUnlock)
                } else {
                    NotiFlowApp(
                        viewModel = viewModel,
                        requestedNotificationId = requestedNotificationId,
                        requestedDigest = requestedDigest,
                        onNavigationRequestConsumed = {
                            requestedNotificationId = null
                            requestedDigest = false
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun requestUnlock() {
        if (authInFlight || viewModel.isUnlocked.value) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            viewModel.setAppLockEnabled(false)
            viewModel.unlock()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "Unlock NotiFlow",
            "Use your device screen lock to open your private notification inbox."
        )
        if (intent == null) {
            viewModel.setAppLockEnabled(false)
            viewModel.unlock()
            return
        }
        authInFlight = true
        unlockLauncher.launch(intent)
    }

    private fun readIntent(intent: Intent?) {
        requestedNotificationId = intent?.getLongExtra(EXTRA_OPEN_NOTIFICATION_ID, -1L)?.takeIf { it > 0L }
        requestedDigest = intent?.getBooleanExtra(EXTRA_OPEN_DIGEST, false) == true
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATION_ID = "open_notification_id"
        const val EXTRA_OPEN_DIGEST = "open_digest"
    }
}
