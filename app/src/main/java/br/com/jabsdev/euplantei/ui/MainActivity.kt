package br.com.jabsdev.euplantei.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import br.com.jabsdev.euplantei.EuPlanteiApplication
import br.com.jabsdev.euplantei.data.prefs.AppSettings
import br.com.jabsdev.euplantei.domain.model.ThemeMode
import br.com.jabsdev.euplantei.service.GardenReminders
import br.com.jabsdev.euplantei.ui.navigation.EuPlanteiApp
import br.com.jabsdev.euplantei.ui.theme.EuPlanteiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val openGardenRequest = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        openGardenRequest.value =
            intent?.getBooleanExtra(GardenReminders.EXTRA_OPEN_GARDEN, false) == true

        val container = (application as EuPlanteiApplication).container
        val settingsFlow = container.settingsRepository.settings.map<AppSettings, AppSettings?> { it }

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val openGarden by openGardenRequest.collectAsStateWithLifecycle()
            val current = settings
            if (current == null) {
                EuPlanteiTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                return@setContent
            }

            val darkTheme = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            EuPlanteiTheme(darkTheme = darkTheme) {
                EuPlanteiApp(
                    container = container,
                    settings = current,
                    onOnboardingComplete = {
                        lifecycleScope.launch {
                            container.settingsRepository.setOnboardingCompleted(true)
                        }
                    },
                    openGardenInitially = openGarden,
                    onOpenGardenHandled = { openGardenRequest.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(GardenReminders.EXTRA_OPEN_GARDEN, false)) {
            openGardenRequest.value = true
        }
    }
}
