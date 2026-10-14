package de.fhstempelkarte

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class AppEntry(val pkg: String, val label: String)

object Zeus {

    private val ZEUS_HINT = Regex("zeus|isgus", RegexOption.IGNORE_CASE)

    fun looksLikeZeus(app: AppEntry) = ZEUS_HINT.containsMatchIn(app.pkg) || ZEUS_HINT.containsMatchIn(app.label)

    /** Alle Apps mit Startsymbol, ZEUS-Kandidaten zuerst. */
    fun launchableApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<AppEntry> { !looksLikeZeus(it) }.thenBy { it.label.lowercase() })
    }

    fun label(ctx: Context, pkg: String?): String? = pkg?.let {
        runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(it, 0)).toString()
        }.getOrNull()
    }

    /** Startet die eingestellte ZEUS-App; false, wenn keine gewählt oder nicht installiert. */
    fun open(ctx: Context): Boolean {
        val pkg = Store(ctx).config.zeusPackage ?: return false
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/**
 * Startpunkt der App (Launcher) und Ziel der Meldung: durchsichtiger Dialog
 * Anmelden / Abmelden / Abbrechen, danach ZEUS. Einstellungen über einen eigenen Knopf;
 * ohne gewählte ZEUS-App (erster Start) direkt in die Einstellungen.
 */
class ZeusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        // Repariert verlorene Alarme (z. B. nach „Stopp erzwingen“) bei jedem App-Start.
        val app = applicationContext
        AppScope.launch { if (Scheduler.planAll(app)) Detector.checkNow(app) }
        if (Store(this).config.zeusPackage == null) {
            openSettings(this)
            finish()
            return
        }
        setContent {
            AppTheme {
                ZeusDialog(onClose = ::finish)
            }
        }
    }
}

fun openSettings(ctx: Context) {
    ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun ZeusDialog(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state = remember { Store(ctx).state }
    var askClear by remember { mutableStateOf(false) }

    fun finishWith(newState: StempelState, clear: Boolean = false) {
        // Zustand + Hintergrund im Hintergrund, ZEUS sofort starten.
        AppScope.launch { Stempel.setState(ctx, newState, clear) }
        if (!Zeus.open(ctx)) {
            Toast.makeText(ctx, "Keine ZEUS-App eingestellt – bitte in FH Stempelkarte wählen.", Toast.LENGTH_LONG).show()
            openSettings(ctx)
        }
        onClose()
    }

    if (askClear) {
        LogoutNoOriginalDialog(
            onClear = { finishWith(StempelState.NEUTRAL, clear = true) },
            onKeep = { finishWith(StempelState.NEUTRAL) },
            onDismiss = onClose,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Stempelkarte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status: ${state.label}\nDanach wird ZEUS geöffnet.")
                Button(onClick = { finishWith(StempelState.EINGELOGGT) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Einloggen")
                }
                OutlinedButton(onClick = {
                    if (Wallpapers.hasOriginal(ctx)) finishWith(StempelState.NEUTRAL) else askClear = true
                }, modifier = Modifier.fillMaxWidth()) { Text("Ausloggen") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
        dismissButton = {
            TextButton(onClick = {
                openSettings(ctx)
                onClose()
            }) { Text("Einstellungen") }
        },
    )
}

/** Rückfrage beim Abmelden ohne gespeicherten Originalhintergrund. */
@Composable
fun LogoutNoOriginalDialog(onClear: () -> Unit, onKeep: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kein Originalbild gewählt") },
        text = {
            Text(
                "Dein ursprünglicher Hintergrund ist nicht gespeichert. Auf den " +
                    "Standard-Hintergrund des Systems zurücksetzen oder den aktuellen lassen?",
            )
        },
        confirmButton = { TextButton(onClick = onClear) { Text("Zurücksetzen") } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Lassen") } },
    )
}
