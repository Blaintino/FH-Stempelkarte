package de.fhstempelkarte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        // Repariert verlorene Alarme (z. B. nach „Stopp erzwingen“).
        val ctx = applicationContext
        AppScope.launch { if (Scheduler.planAll(ctx)) Detector.checkNow(ctx) }
        setContent {
            AppTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Zurück aus den Systemeinstellungen: Prüfliste neu auswerten.
        resumeTick.value = resumeTick.value + 1
    }

    companion object {
        val resumeTick = MutableStateFlow(0)
    }
}
