package com.premraj.notiflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.premraj.notiflow.ui.NotiFlowApp
import com.premraj.notiflow.ui.NotiFlowTheme
import com.premraj.notiflow.ui.NotiFlowViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<NotiFlowViewModel>()
    private var requestedNotificationId by mutableStateOf<Long?>(null)
    private var requestedDigest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            val prefVersion by viewModel.preferencesVersion.collectAsStateWithLifecycle()
            val themeMode = remember(prefVersion) { viewModel.preferences.appThemeMode }
            NotiFlowTheme(themeMode = themeMode) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
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
