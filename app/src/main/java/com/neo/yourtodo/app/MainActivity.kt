package com.neo.yourtodo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.neo.yourtodo.core.designsystem.theme.YourTodoTheme
import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import com.neo.yourtodo.feature.auth.api.AuthGateEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var featureEntries: Set<@JvmSuppressWildcards AppFeatureEntry>

    @Inject
    lateinit var authGateEntry: AuthGateEntry

    private var navigationRequestId = 0L
    private var launchNavigationRequest by mutableStateOf<AppLaunchNavigationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchNavigationRequest = parseNavigationRequest(intent)
        if (!isRunningInstrumentationTest()) {
            ensureNotificationPermission()
        }

        setContent {
            YourTodoTheme {
                authGateEntry.Content {
                    AppNavHost(
                        entries = featureEntries,
                        launchNavigationRequest = launchNavigationRequest
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchNavigationRequest = parseNavigationRequest(intent)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    private companion object {
        const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    private fun isRunningInstrumentationTest(): Boolean = runCatching {
        Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        true
    }.getOrDefault(false)

    private fun parseNavigationRequest(intent: Intent?): AppLaunchNavigationRequest? =
        parseAppLaunchNavigationRequest(
            intent = intent,
            requestId = ++navigationRequestId
        )
}
