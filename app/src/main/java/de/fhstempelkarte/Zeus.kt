package de.fhstempelkarte

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
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

    /** Launcher-Verknüpfung (lange auf das App-Symbol drücken). Dynamisch, weil die Debug-App-ID abweicht. */
    fun publishShortcut(ctx: Context) {
        val shortcut = ShortcutInfoCompat.Builder(ctx, "zeus")
            .setShortLabel("ZEUS")
            .setLongLabel("Anmelden/Abmelden + ZEUS")
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(Intent(ctx, ZeusActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcut) }
    }

    /** Verknüpfung direkt auf den Startbildschirm legen (fragt der Launcher nach). */
    fun requestPinShortcut(ctx: Context): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
        val shortcut = ShortcutInfoCompat.Builder(ctx, "zeus")
            .setShortLabel("ZEUS")
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(Intent(ctx, ZeusActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(ctx, shortcut, null)
    }
}

/** Homescreen-Widget: Farbe zeigt den Zustand, Klick öffnet den ZEUS-Dialog. Klassische RemoteViews. */
class ZeusWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) = update(ctx, manager, ids)

    companion object {
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, ZeusWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, manager, ids)
        }

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val state = Store(ctx).state
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, ZeusActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val (bg, fg) = when (state) {
                StempelState.NEUTRAL -> R.drawable.widget_bg_neutral to ctx.getColor(R.color.widget_fg)
                StempelState.NICHT_EINGELOGGT -> R.drawable.widget_bg_out to android.graphics.Color.WHITE
                StempelState.EINGELOGGT -> R.drawable.widget_bg_in to android.graphics.Color.WHITE
            }
            val views = RemoteViews(ctx.packageName, R.layout.widget_zeus).apply {
                setOnClickPendingIntent(R.id.widget_root, open)
                setInt(R.id.widget_root, "setBackgroundResource", bg)
                setTextColor(R.id.widget_title, fg)
                setTextColor(R.id.widget_state, fg)
                setTextViewText(R.id.widget_state, state.label)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}

/** Durchsichtiger Dialog aus Widget, Verknüpfung und Meldung: Anmelden / Abmelden / Abbrechen, dann ZEUS. */
class ZeusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ZeusDialog(onClose = ::finish)
            }
        }
    }
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
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        title = { Text("ZEUS") },
        text = { Text("Status: ${state.label}\n\nDanach wird ZEUS geöffnet.") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onClose) { Text("Abbrechen") }
                TextButton(onClick = {
                    if (Wallpapers.hasOriginal(ctx)) finishWith(StempelState.NEUTRAL) else askClear = true
                }) { Text("Abmelden") }
                Button(onClick = { finishWith(StempelState.EINGELOGGT) }) { Text("Anmelden") }
            }
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
