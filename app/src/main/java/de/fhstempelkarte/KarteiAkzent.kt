package de.fhstempelkarte

import android.app.BroadcastOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Akzentfarbe im eigenen „Kartei Launcher“ setzen (dessen offene Schnittstelle, Vertrag in
 * [KarteiSchnittstelle], Beschreibung `SCHNITTSTELLE.md` im Launcher-Repo). Die Stempelkarte
 * sendet nur die vom Nutzer gewählte Farbe zum Zustand – keinen Zustand, keine Arbeitszeit.
 */
object KarteiAkzent {
    /** Farbe für [state] oder null = zurücknehmen (aus, oder Zustand NEUTRAL). Rein, getestet. */
    fun farbeFuer(cfg: Config, state: StempelState): Int? = if (!cfg.karteiAkzent) null else when (state) {
        StempelState.NEUTRAL -> null
        StempelState.NICHT_EINGELOGGT -> cfg.karteiOut
        StempelState.EINGELOGGT -> cfg.karteiIn
    }

    /**
     * Expliziter Broadcast an den Launcher. Ist er nicht installiert oder fehlt die Berechtigung,
     * stellt Android nichts zu – Fehler nie weiterreichen. [immer] = false: bei ausgeschalteter
     * Option nichts senden (sonst würde jeder Zustandswechsel eine fremde Farbe zurücknehmen
     * lassen, obwohl die Stempelkarte nie eine gesetzt hat).
     */
    fun senden(ctx: Context, immer: Boolean = false) {
        val store = Store(ctx)
        val cfg = store.config
        if (!cfg.karteiAkzent && !immer) return
        val farbe = farbeFuer(cfg, store.state)
        val intent = Intent(
            if (farbe != null) KarteiSchnittstelle.AKTION_AKZENT_SETZEN else KarteiSchnittstelle.AKTION_AKZENT_ZURUECKNEHMEN,
        ).setPackage(KarteiSchnittstelle.PAKET)
            .putExtra(KarteiSchnittstelle.EXTRA_APP, "FH Stempelkarte")
        if (farbe != null) intent.putExtra(KarteiSchnittstelle.EXTRA_FARBE, farbe)
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Identität teilen: der Launcher zeigt dann „Akzent wurde geändert von: FH Stempelkarte“.
                ctx.sendBroadcast(intent, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
            } else {
                ctx.sendBroadcast(intent)
            }
        }.isSuccess
        EventLog.add(
            ctx,
            "Kartei Launcher: " + (if (farbe != null) "Akzent ${Farbraum.hex(farbe)}" else "Akzent zurückgenommen") +
                if (ok) "" else " (fehlgeschlagen)",
        )
    }
}

/** HSV ↔ ARGB für den Farbwähler (rein, ohne Android-Typen). */
object Farbraum {
    /** Farbton 0…360, Sättigung und Helligkeit 0…1 → ARGB (voll deckend). */
    fun hsvZuArgb(farbton: Float, saettigung: Float, helligkeit: Float): Int {
        val h = ((farbton % 360f) + 360f) % 360f
        val s = saettigung.coerceIn(0f, 1f)
        val v = helligkeit.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun k(t: Float) = ((t + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (k(r) shl 16) or (k(g) shl 8) or k(b)
    }

    /** ARGB → (Farbton 0…360, Sättigung 0…1, Helligkeit 0…1). */
    fun argbZuHsv(farbe: Int): Triple<Float, Float, Float> {
        val r = ((farbe shr 16) and 0xFF) / 255f
        val g = ((farbe shr 8) and 0xFF) / 255f
        val b = (farbe and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0) it + 360f else it }
        val s = if (max == 0f) 0f else d / max
        return Triple(h, s, max)
    }

    /** „#RRGGBB“ für die Anzeige. */
    fun hex(farbe: Int): String = "#%06X".format(farbe and 0xFFFFFF)
}
