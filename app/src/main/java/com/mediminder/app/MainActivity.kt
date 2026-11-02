package com.mediminder.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediminder.app.ui.navigation.MediNavHost
import com.mediminder.app.ui.theme.MediTheme
import com.mediminder.app.util.Permissions

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            MediTheme(dynamicColor = settings.dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    AskNotificationPermissionOnce()
                    MediNavHost()
                }
            }
        }
    }

    @Composable
    private fun AskNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            val settings = container.settings
            if (!settings.notificationPermissionAsked && !Permissions.notificationsGranted(this@MainActivity)) {
                settings.notificationPermissionAsked = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
