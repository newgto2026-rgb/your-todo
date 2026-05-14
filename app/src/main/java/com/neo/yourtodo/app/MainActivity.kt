package com.neo.yourtodo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.core.designsystem.theme.YourTodoTheme
import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import com.neo.yourtodo.feature.auth.api.AuthGateEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var featureEntries: Set<@JvmSuppressWildcards AppFeatureEntry>

    @Inject
    lateinit var authGateEntry: AuthGateEntry

    private var navigationRequestId = 0L
    private val launchNavigationRequest = MutableStateFlow<AppLaunchNavigationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val showStartupSplashOnLaunch = savedInstanceState == null && !isRunningInstrumentationTest()
        val systemSplashStartedAt = SystemClock.uptimeMillis()
        val systemSplash = installSplashScreen()
        systemSplash.setKeepOnScreenCondition {
            showStartupSplashOnLaunch &&
                SystemClock.uptimeMillis() - systemSplashStartedAt < MIN_SYSTEM_SPLASH_DURATION_MS
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        launchNavigationRequest.value = parseNavigationRequest(intent)
        if (!isRunningInstrumentationTest()) {
            ensureNotificationPermission()
        }

        setContent {
            val navigationRequest by launchNavigationRequest.collectAsStateWithLifecycle()
            var showStartupSplash by remember { mutableStateOf(showStartupSplashOnLaunch) }
            YourTodoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    authGateEntry.Content {
                        AppNavHost(
                            entries = featureEntries,
                            launchNavigationRequest = navigationRequest
                        )
                    }
                    if (showStartupSplash) {
                        AppStartupSplash(
                            onFinished = { showStartupSplash = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    fun handleNavigationIntent(intent: Intent?) {
        val request = parseNavigationRequest(intent)
        launchNavigationRequest.value = request
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
        const val MIN_SYSTEM_SPLASH_DURATION_MS = 650L
    }

    private fun isRunningInstrumentationTest(): Boolean = runCatching {
        val registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry")
        registry
            .getMethod("getInstrumentation")
            .invoke(null)
        true
    }.getOrDefault(false)

    private fun parseNavigationRequest(intent: Intent?): AppLaunchNavigationRequest? =
        parseAppLaunchNavigationRequest(
            intent = intent,
            requestId = ++navigationRequestId
        )
}
