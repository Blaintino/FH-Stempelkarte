package de.fhstempelkarte

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

/** Eine Voraussetzung. [ok] = null: lässt sich nicht prüfen (nur Anleitung). [permission]: im App-Dialog anfragen. */
data class Check(
    val title: String,
    val ok: Boolean?,
    val detail: String,
    val button: String? = null,
    val permission: Array<String>? = null,
    val intent: ((Context) -> Intent)? = null,
)

object Reliability {

    private fun pkgUri(ctx: Context) = Uri.fromParts("package", ctx.packageName, null)

    private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))

    @SuppressLint("BatteryLife") // private App, Ausnahme von der Akku-Optimierung ist gewollt
    fun checks(ctx: Context): List<Check> {
        val cfg = Store(ctx).config
        val list = mutableListOf<Check>()
        Notifier.ensureChannel(ctx)
        val channel = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(Notifier.CHANNEL)

        list += Check(
            "Benachrichtigungen erlaubt",
            NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE),
            "Für die Meldung „Einstempeln nicht vergessen“.",
            "Einstellungen öffnen",
        ) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName)
        }

        if (Build.VERSION.SDK_INT >= 31) {
            list += Check(
                "Exakte Wecker-Alarme",
                Scheduler.canScheduleExact(ctx),
                "Damit die Erkennung pünktlich zum Fensterbeginn startet, auch im Tiefschlaf.",
                "Erlauben",
            ) { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(it)) }
        }

        list += Check(
            "Standort: genau",
            Detector.hasFineLocation(ctx),
            "Für Geofence und um den WLAN-Namen lesen zu dürfen.",
            "Erlauben",
            permission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )

        if (Build.VERSION.SDK_INT >= 29) {
            list += Check(
                "Standort: „Immer erlauben“",
                Detector.hasBackgroundLocation(ctx),
                "Die Erkennung läuft morgens im Hintergrund. Erst „Standort: genau“ erlauben, " +
                    "dann hier „Immer erlauben“ wählen.",
                "Erlauben",
                permission = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            )
        }

        val lm = ctx.getSystemService(LocationManager::class.java)
        list += Check(
            "Standortdienst eingeschaltet",
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else null,
            "Ohne eingeschalteten Standort gibt Android weder Position noch WLAN-Namen heraus.",
            "Einstellungen öffnen",
        ) { Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }

        list += Check(
            "Erkennung eingerichtet",
            cfg.locationActive || cfg.wifiActive,
            "Mindestens eins eingeschaltet und eingerichtet: FH-Standort oder FH-WLAN (in den Einstellungen).",
        )

        list += Check(
            "ZEUS-App gewählt",
            cfg.zeusPackage != null && Zeus.label(ctx, cfg.zeusPackage) != null,
            "Auf dem Hauptbildschirm unter „ZEUS-App“.",
        )

        list += Check(
            "Hintergrund „Normal“ gewählt",
            Wallpapers.hasOriginal(ctx),
            "Einstellungen → Hintergründe → Normal. Damit Ausloggen deinen Hintergrund wiederherstellen kann.",
        )

        val pm = ctx.getSystemService(PowerManager::class.java)
        list += Check(
            "Akku-Optimierung aus",
            pm.isIgnoringBatteryOptimizations(ctx.packageName),
            "Sonst beendet das System die App im Hintergrund.",
            "Ausnahme erlauben",
        ) { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(it)) }

        if (Build.VERSION.SDK_INT >= 28) {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java)
            list += Check(
                "Hintergrundnutzung erlaubt",
                !am.isBackgroundRestricted,
                "In den App-Infos unter „Akku“ darf die Hintergrundnutzung nicht gesperrt sein.",
                "App-Infos öffnen",
                intent = ::appDetails,
            )
        }

        if (Build.VERSION.SDK_INT >= 30) {
            list += Check(
                "Bei Nichtnutzung nicht pausieren",
                ctx.packageManager.isAutoRevokeWhitelisted,
                "Sonst pausiert Android die App, wenn du sie länger nicht öffnest, und entzieht " +
                    "Berechtigungen. Schalter „App-Aktivität pausieren, wenn unbenutzt“ aus.",
                "Einstellung öffnen",
            ) { Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, pkgUri(it)) }
        }

        list += Check(
            "Samsung: „Nie schlafende Apps“",
            null,
            "Einstellungen → Akku → Hintergrundnutzungslimits → „Nie schlafende Apps“ → " +
                "FH Stempelkarte hinzufügen. Lässt sich von der App aus nicht prüfen.",
        )

        list += Check(
            "Sperrbildschirm",
            null,
            "Samsung kann den Sperrbildschirm-Hintergrund selbst wechseln (Dynamischer " +
                "Sperrbildschirm/Hintergrunddienste). Dann dort ein festes Bild einstellen.",
        )
        return list
    }

    fun allOk(ctx: Context): Boolean = checks(ctx).all { it.ok != false }

    fun open(ctx: Context, check: Check) {
        val intent = check.intent?.invoke(ctx) ?: return
        runCatching { ctx.startActivity(intent) }
            .onFailure { runCatching { ctx.startActivity(appDetails(ctx)) } }
    }
}

@Composable
fun ReliabilityScreen(tick: Int, onBack: () -> Unit, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    var permTick by remember { mutableIntStateOf(0) }
    val checks = remember(tick, permTick) { Reliability.checks(ctx) }
    var tested by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        AppScope.launch { Scheduler.planAll(ctx) }
    }

    Column(Modifier.padding(contentPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Prüfliste", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Test", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Löst sofort aus wie an der FH: Hintergrund „Nicht eingeloggt“ und Meldung. " +
                                "Zurück mit Anmelden oder Abmelden (Meldung antippen oder App öffnen).",
                        )
                        Button(onClick = {
                            AppScope.launch { Stempel.testTrigger(ctx) }
                            tested = true
                        }) { Text("Test auslösen") }
                        if (tested) Text("Ausgelöst.", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { LogCard(tick + permTick) }
            items(checks, key = { it.title }) { c ->
                CheckCard(c) { perms -> permission.launch(perms) }
            }
        }
    }
}

@Composable
private fun CheckCard(c: Check, request: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val (mark, color) = when (c.ok) {
        true -> "✓" to colors.primary
        false -> "✗" to colors.error
        null -> "ⓘ" to colors.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = if (c.ok == false) CardDefaults.cardColors(containerColor = colors.errorContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mark, color = color, style = MaterialTheme.typography.titleLarge)
                Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            Text(c.detail, style = MaterialTheme.typography.bodyMedium)
            if (c.button != null && (c.intent != null || c.permission != null) && c.ok != true) {
                val action = { if (c.permission != null) request(c.permission) else Reliability.open(ctx, c) }
                if (c.ok == false) Button(onClick = action) { Text(c.button) } else OutlinedButton(onClick = action) { Text(c.button) }
            }
        }
    }
}

/** Hintergrund-Protokoll (neueste zuerst), zum Kopieren für die Fehlersuche. */
@Composable
private fun LogCard(tick: Int) {
    val ctx = LocalContext.current
    var cleared by remember { mutableIntStateOf(0) }
    val version by Store.version.collectAsState()
    val lines = remember(tick, cleared, version) { EventLog.read(ctx) }
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Protokoll", style = MaterialTheme.typography.titleMedium)
            Text(
                "Was im Hintergrund passiert ist: Alarme, WLAN- und Standort-Meldungen, Auslösen.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (lines.isEmpty()) Text("Noch leer.")
            (if (expanded) lines else lines.take(15)).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lines.size > 15) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Alle (${lines.size})") }
                }
                TextButton(onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("Protokoll", lines.joinToString("\n")))
                    Toast.makeText(ctx, "Protokoll kopiert", Toast.LENGTH_SHORT).show()
                }) { Text("Kopieren") }
                TextButton(onClick = { EventLog.clear(ctx); cleared++ }) { Text("Leeren") }
            }
        }
    }
}
