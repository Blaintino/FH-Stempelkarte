package de.fhstempelkarte

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Einstellungen → „Akzentfarbe im Kartei Launcher“: an/aus und je Zustand eine frei gewählte Farbe. */
@Composable
fun KarteiCard(cfg: Config, save: ((Config) -> Config) -> Unit) {
    val ctx = LocalContext.current
    var waehle by remember { mutableStateOf<StempelState?>(null) }

    /** Speichern, dann den Launcher sofort informieren (beim Ausschalten: zurücknehmen). */
    fun aendern(c: (Config) -> Config) {
        save(c)
        AppScope.launch { KarteiAkzent.senden(ctx, immer = true) }
    }

    waehle?.let { st ->
        FarbWahlDialog(
            start = if (st == StempelState.EINGELOGGT) cfg.karteiIn else cfg.karteiOut,
            titel = "Akzent bei „${st.label}“",
            onFertig = { f ->
                waehle = null
                aendern { if (st == StempelState.EINGELOGGT) it.copy(karteiIn = f) else it.copy(karteiOut = f) }
            },
            onAbbrechen = { waehle = null },
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Akzentfarbe im Kartei Launcher", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Farbe je Zustand senden", modifier = Modifier.weight(1f))
                Switch(checked = cfg.karteiAkzent, onCheckedChange = { an -> aendern { it.copy(karteiAkzent = an) } })
            }
            Text(
                "Setzt die Akzentfarbe des eigenen Launchers. Bei „Neutral“ (ausgeloggt) gilt dort " +
                    "wieder die eigene Farbe. Der Launcher muss vor dieser App installiert sein.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (cfg.karteiAkzent) {
                FarbZeile("Nicht eingeloggt", cfg.karteiOut) { waehle = StempelState.NICHT_EINGELOGGT }
                FarbZeile("Eingeloggt", cfg.karteiIn) { waehle = StempelState.EINGELOGGT }
            }
        }
    }
}

@Composable
private fun FarbZeile(titel: String, farbe: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Box(Modifier.size(32.dp).background(Color(farbe), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(titel)
            Text("${Farbraum.hex(farbe)} · Tippen zum Ändern", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Freier Farbwähler: Farbton, Sättigung, Helligkeit als Verlaufsbalken (wie im Launcher). */
@Composable
private fun FarbWahlDialog(start: Int, titel: String, onFertig: (Int) -> Unit, onAbbrechen: () -> Unit) {
    val (h0, s0, v0) = remember(start) { Farbraum.argbZuHsv(start) }
    var h by remember { mutableFloatStateOf(h0) }
    var s by remember { mutableFloatStateOf(s0) }
    var v by remember { mutableFloatStateOf(v0) }
    val farbe = Farbraum.hsvZuArgb(h, s, v)
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp).background(Color(farbe), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    // Akzent-Punkt auf schwarzem Grund (Listen im Launcher sind schwarz).
                    Box(Modifier.size(52.dp, 30.dp).background(Color.Black, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).background(Color(farbe), CircleShape))
                    }
                    Text(Farbraum.hex(farbe), style = MaterialTheme.typography.bodyMedium)
                }
                VerlaufRegler("Farbton", h / 360f, Brush.horizontalGradient((0..6).map { Color(Farbraum.hsvZuArgb(it * 60f, 1f, 1f)) })) { h = it * 360f }
                VerlaufRegler("Sättigung", s, Brush.horizontalGradient(listOf(Color(Farbraum.hsvZuArgb(h, 0f, v)), Color(Farbraum.hsvZuArgb(h, 1f, v))))) { s = it }
                VerlaufRegler("Helligkeit", v, Brush.horizontalGradient(listOf(Color.Black, Color(Farbraum.hsvZuArgb(h, s, 1f))))) { v = it }
            }
        },
        confirmButton = { TextButton(onClick = { onFertig(farbe) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

/** Balken mit Farbverlauf; Tippen oder Ziehen setzt den Wert (0…1). */
@Composable
private fun VerlaufRegler(titel: String, wert: Float, verlauf: Brush, onWert: (Float) -> Unit) {
    val dichte = LocalDensity.current
    Column {
        Text(titel, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(30.dp)) {
            val breite = with(dichte) { maxWidth.toPx() }
            val knopfPx = with(dichte) { 26.dp.toPx() }
            fun setze(x: Float) = onWert((x / breite).coerceIn(0f, 1f))
            Box(
                Modifier.fillMaxWidth().height(30.dp).background(verlauf, RoundedCornerShape(15.dp))
                    .pointerInput(breite) { detectTapGestures { setze(it.x) } }
                    .pointerInput(breite) { detectDragGestures(onDragStart = { setze(it.x) }) { c, _ -> c.consume(); setze(c.position.x) } },
            )
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (wert * breite - knopfPx / 2).roundToInt().coerceIn(0, (breite - knopfPx).roundToInt().coerceAtLeast(0)),
                            2.dp.roundToPx(),
                        )
                    }
                    .size(26.dp).border(3.dp, Color.White, CircleShape).border(4.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
            )
        }
    }
}
