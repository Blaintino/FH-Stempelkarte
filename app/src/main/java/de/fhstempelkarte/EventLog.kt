package de.fhstempelkarte

import android.content.Context
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Kleines Protokoll der Hintergrund-Ereignisse (Alarme, WLAN-/Standort-Meldungen, Auslösen),
 * sichtbar in der Prüfliste. Zur Fehlersuche ohne USB: der Nutzer installiert über Obtainium.
 */
object EventLog {
    private const val MAX = 300
    private val FORMAT = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss")

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "protokoll.txt")

    @Synchronized
    fun add(ctx: Context, msg: String) {
        runCatching {
            val f = file(ctx)
            val lines = (if (f.exists()) f.readLines() else emptyList()) +
                "${ZonedDateTime.now().format(FORMAT)}  $msg"
            f.writeText(lines.takeLast(MAX).joinToString("\n", postfix = "\n"))
        }
    }

    /** Neueste zuerst. */
    @Synchronized
    fun read(ctx: Context): List<String> =
        runCatching { file(ctx).readLines().filter { it.isNotBlank() }.reversed() }.getOrDefault(emptyList())

    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
