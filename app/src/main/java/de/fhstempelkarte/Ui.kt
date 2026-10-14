package de.fhstempelkarte

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val OUT_COLORS = listOf(0xFFC62828, 0xFFEF6C00, 0xFFAD1457, 0xFF6A1B9A).map { it.toInt() }
private val IN_COLORS = listOf(0xFF2E7D32, 0xFF1B5E20, 0xFF1565C0, 0xFF00838F).map { it.toInt() }
private val DAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

private fun hm(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

@Composable
fun App() {
    var showChecks by rememberSaveable { mutableStateOf(false) }
    val tick by MainActivity.resumeTick.collectAsState()
    val ctx = LocalContext.current

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold { padding ->
        if (showChecks) {
            ReliabilityScreen(tick, onBack = { showChecks = false }, contentPadding = padding)
        } else {
            MainScreen(tick, padding, onChecks = { showChecks = true })
        }
    }
}

@Composable
private fun MainScreen(tick: Int, padding: PaddingValues, onChecks: () -> Unit) {
    val ctx = LocalContext.current
    val version by Store.version.collectAsState()
    val store = remember { Store(ctx) }
    val state = remember(version) { store.state }
    val cfg = remember(version) { store.config }
    val hasOriginal = remember(version, tick) { Wallpapers.hasOriginal(ctx) }
    val allOk = remember(tick, version) { Reliability.allOk(ctx) }
    var busy by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var pickZeus by remember { mutableStateOf(false) }

    fun change(newState: StempelState, clear: Boolean = false) {
        busy = true
        AppScope.launch {
            val r = Stempel.setState(ctx, newState, clear)
            withContext(Dispatchers.Main) {
                busy = false
                if (r == Wallpapers.Result.FAILED) {
                    Toast.makeText(ctx, "Hintergrund konnte nicht gesetzt werden.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun save(c: (Config) -> Config) {
        store.update(c)
        AppScope.launch { Scheduler.planAll(ctx) }
    }

    if (askClear) {
        LogoutNoOriginalDialog(
            onClear = { askClear = false; change(StempelState.NEUTRAL, clear = true) },
            onKeep = { askClear = false; change(StempelState.NEUTRAL) },
            onDismiss = { askClear = false },
        )
    }
    if (pickZeus) {
        ZeusPicker(
            onPick = { pkg -> pickZeus = false; save { it.copy(zeusPackage = pkg) } },
            onDismiss = { pickZeus = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("FH Stempelkarte", style = MaterialTheme.typography.headlineSmall) }

        item {
            val color = when (state) {
                StempelState.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
                StempelState.NICHT_EINGELOGGT -> Color(cfg.colorOut)
                StempelState.EINGELOGGT -> Color(cfg.colorIn)
            }
            val fg = if (state == StempelState.NEUTRAL) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
            Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.label, color = fg, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(statusLine(store, cfg), color = fg, style = MaterialTheme.typography.bodyMedium)
                    store.lastTriggerInfo?.let {
                        Text("Zuletzt ausgelöst: $it", color = fg, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { change(StempelState.EINGELOGGT) }) { Text("Anmelden") }
                        OutlinedButton(enabled = !busy, onClick = {
                            if (hasOriginal) change(StempelState.NEUTRAL) else askClear = true
                        }) { Text("Abmelden", color = fg) }
                    }
                }
            }
        }

        if (!hasOriginal) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Noch kein Originalhintergrund gewählt. Ohne ihn kann die App beim Abmelden " +
                            "deinen Hintergrund nicht wiederherstellen – bitte unten unter " +
                            "„Hintergrund“ wählen.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        item {
            Button(onClick = onChecks, modifier = Modifier.fillMaxWidth()) {
                Text(if (allOk) "Prüfliste & Test ✓" else "Prüfliste & Test – etwas fehlt ✗")
            }
        }

        item { WindowCard(cfg, ::save) }
        item { LocationCard(cfg, ::save) }
        item { WifiCard(cfg, ::save) }
        item {
            SectionCard("ZEUS-App") {
                val label = remember(cfg.zeusPackage) { Zeus.label(ctx, cfg.zeusPackage) }
                Text(
                    when {
                        cfg.zeusPackage == null -> "Nicht gewählt"
                        label == null -> "${cfg.zeusPackage} (nicht installiert)"
                        else -> "$label (${cfg.zeusPackage})"
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickZeus = true }) { Text("App wählen…") }
                    OutlinedButton(onClick = {
                        if (!Zeus.requestPinShortcut(ctx)) {
                            Toast.makeText(ctx, "Launcher unterstützt das nicht – Widget verwenden.", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Verknüpfung anlegen") }
                }
                Text(
                    "Widget: Startbildschirm lange drücken → Widgets → FH Stempelkarte. " +
                        "Die Verknüpfung gibt es auch beim langen Druck auf das App-Symbol.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { WallpaperCard(cfg, hasOriginal, ::save) }
    }
}

private fun statusLine(store: Store, cfg: Config): String {
    val zone = ZoneId.systemDefault()
    val until = store.detectingUntil
    if (until > System.currentTimeMillis()) {
        return "Erkennung läuft bis " + Instant.ofEpochMilli(until).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val next = if (TimeWindow.isValid(cfg.window)) TimeWindow.nextStart(ZonedDateTime.now(zone), cfg.window) else null
    return if (next == null) {
        "Kein Zeitfenster aktiv"
    } else {
        "Nächstes Zeitfenster: " + next.format(DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", Locale.GERMAN))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun WindowCard(cfg: Config, save: ((Config) -> Config) -> Unit) {
    val ctx = LocalContext.current
    val w = cfg.window
    fun pick(minute: Int, set: (Int) -> WindowConfig) {
        TimePickerDialog(ctx, { _, h, m ->
            val nw = set(h * 60 + m)
            if (TimeWindow.isValid(nw)) {
                save { it.copy(window = nw) }
            } else {
                Toast.makeText(ctx, "Ende muss nach dem Beginn liegen.", Toast.LENGTH_SHORT).show()
            }
        }, minute / 60, minute % 60, true).show()
    }
    SectionCard("Zeitfenster") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pick(w.startMinute) { w.copy(startMinute = it) } }) { Text("Von ${hm(w.startMinute)}") }
            OutlinedButton(onClick = { pick(w.endMinute) { w.copy(endMinute = it) } }) { Text("Bis ${hm(w.endMinute)}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            DAYS.forEachIndexed { i, d ->
                val on = w.weekdayMask and (1 shl i) != 0
                FilterChip(
                    selected = on,
                    onClick = { save { it.copy(window = w.copy(weekdayMask = w.weekdayMask xor (1 shl i))) } },
                    label = { Text(d, maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text("Nur in diesem Fenster wird erkannt, ob du an der FH bist.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LocationCard(cfg: Config, save: ((Config) -> Config) -> Unit) {
    val ctx = LocalContext.current
    var radius by remember { mutableStateOf(cfg.radiusM.toString()) }
    var locating by remember { mutableStateOf(false) }

    fun take() {
        locating = true
        Geo.currentLocation(ctx) { loc ->
            locating = false
            if (loc == null) {
                Toast.makeText(ctx, "Kein Standort – Standortdienst an?", Toast.LENGTH_LONG).show()
            } else {
                save { it.copy(lat = loc.latitude, lon = loc.longitude) }
                Toast.makeText(ctx, "Übernommen (±${loc.accuracy.toInt()} m)", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Detector.hasFineLocation(ctx)) take()
    }

    SectionCard("FH-Standort") {
        Text(
            if (cfg.hasLocation) "%.5f, %.5f".format(Locale.ROOT, cfg.lat, cfg.lon) else "Nicht gesetzt",
        )
        OutlinedButton(enabled = !locating, onClick = {
            if (Detector.hasFineLocation(ctx)) {
                take()
            } else {
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }) { Text(if (locating) "Suche Standort…" else "Aktuellen Standort übernehmen") }
        OutlinedTextField(
            value = radius,
            onValueChange = { v ->
                radius = v.filter(Char::isDigit).take(4)
                radius.toIntOrNull()?.takeIf { it in 50..5000 }?.let { r -> save { it.copy(radiusM = r) } }
            },
            label = { Text("Radius in Metern (50–5000)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun WifiCard(cfg: Config, save: ((Config) -> Config) -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(cfg.ssids.joinToString(", ")) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    SectionCard("FH-WLAN") {
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v
                save { it.copy(ssids = Ssid.parseList(v)) }
            },
            label = { Text("WLAN-Namen, mit Komma getrennt (z. B. eduroam)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = {
            if (!Detector.hasFineLocation(ctx)) {
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return@OutlinedButton
            }
            val ssid = Detector.currentSsid(ctx)
            if (ssid == null) {
                Toast.makeText(ctx, "Kein WLAN erkannt (Standortdienst an?)", Toast.LENGTH_LONG).show()
            } else {
                val list = (Ssid.parseList(text) + ssid).distinctBy { it.lowercase() }
                text = list.joinToString(", ")
                save { it.copy(ssids = list) }
            }
        }) { Text("Aktuelles WLAN übernehmen") }
    }
}

@Composable
private fun WallpaperCard(cfg: Config, hasOriginal: Boolean, save: ((Config) -> Config) -> Unit) {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf<java.io.File?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val t = target ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            AppScope.launch {
                val ok = Wallpapers.import(ctx, uri, t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, if (ok) "Bild gespeichert" else "Bild konnte nicht gelesen werden", Toast.LENGTH_SHORT).show()
                    Store.version.value = Store.version.value + 1
                    refresh++
                }
            }
        }
    }
    fun pick(f: java.io.File) {
        target = f
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    SectionCard("Hintergrund") {
        Text(if (hasOriginal) "Originalbild: gespeichert ✓" else "Originalbild: nicht gewählt")
        OutlinedButton(onClick = { pick(Wallpapers.originalFile(ctx)) }) {
            Text(if (hasOriginal) "Originalbild ändern" else "Originalbild wählen")
        }

        Text("Bildschirme", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(cfg.onHome, { v -> save { it.copy(onHome = v) } })
            Text("Startbildschirm")
            Checkbox(cfg.onLock, { v -> save { it.copy(onLock = v) } })
            Text("Sperrbildschirm")
        }

        StateImageRow("„Nicht eingeloggt“", OUT_COLORS, cfg.colorOut, Wallpapers.customFile(ctx, StempelState.NICHT_EINGELOGGT), refresh,
            onColor = { c -> save { it.copy(colorOut = c) } }, onPick = ::pick, onRemove = { it.delete(); refresh++ })
        StateImageRow("„Eingeloggt“", IN_COLORS, cfg.colorIn, Wallpapers.customFile(ctx, StempelState.EINGELOGGT), refresh,
            onColor = { c -> save { it.copy(colorIn = c) } }, onPick = ::pick, onRemove = { it.delete(); refresh++ })
    }
}

@Composable
private fun StateImageRow(
    title: String,
    colors: List<Int>,
    selected: Int,
    file: java.io.File,
    refresh: Int,
    onColor: (Int) -> Unit,
    onPick: (java.io.File) -> Unit,
    onRemove: (java.io.File) -> Unit,
) {
    val hasCustom = remember(refresh, file) { file.length() > 0 }
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (hasCustom) {
        Text("Eigenes Bild gewählt", style = MaterialTheme.typography.bodySmall)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { c ->
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Color(c), CircleShape)
                        .border(if (c == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onColor(c) },
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onPick(file) }) { Text("Eigenes Bild…") }
        if (hasCustom) TextButton(onClick = { onRemove(file) }) { Text("Entfernen") }
    }
}

@Composable
private fun ZeusPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { Zeus.launchableApps(ctx) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ZEUS-App wählen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Suchen") }, singleLine = true)
                val list = apps
                if (list == null) {
                    Text("Lade…")
                } else {
                    val shown = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(shown, key = { it.pkg }) { app ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(app.pkg) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    app.label + if (Zeus.looksLikeZeus(app)) "  ★" else "",
                                    fontWeight = if (Zeus.looksLikeZeus(app)) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(app.pkg, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
