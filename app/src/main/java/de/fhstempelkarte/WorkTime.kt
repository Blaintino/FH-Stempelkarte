package de.fhstempelkarte

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Arbeitszeit pro Tag (Wunsch des Nutzers: im Chip-Widget anzeigen, mehrmaliges Ein- und Ausloggen
 * am Tag aufsummiert). Gezählt wird die Zeit in `EINGELOGGT`, nur der heutige Tag: Eine Sitzung über
 * Mitternacht zählt heute ab 0:00. Rein, getestet (`WorkTimeTest`).
 *
 * Pausen (Wunsch des Nutzers, nach § 4 ArbZG): mehr als 6 h Arbeit → 30 min, mehr als 9 h → 45 min
 * Pause insgesamt; angerechnet werden nur Unterbrechungen (ausgeloggt) von mindestens 15 min. Was an
 * Pause fehlt, zieht [net] ab, ohne Sprung: die Nettozeit bleibt bei 6:00 bzw. 9:00 stehen, bis die
 * fehlende Pause verstrichen ist.
 */
object WorkTime {
    const val MIN_BREAK_MS = 15 * 60_000L
    private const val H = 3_600_000L
    private const val M = 60_000L

    /**
     * Abgeschlossene Sitzungen eines Tages: [closedMs] Brutto-Summe, [breakMs] angerechnete Pausen
     * (Unterbrechungen ≥ 15 min zwischen Sitzungen), [lastEnd] Ende der letzten Sitzung (ms, 0 = keine).
     */
    data class Day(val day: LocalDate?, val closedMs: Long, val breakMs: Long = 0, val lastEnd: Long = 0)

    /** Angerechnete Pausen heute. */
    fun breaksToday(stored: Day, now: ZonedDateTime): Long =
        if (stored.day == now.toLocalDate()) stored.breakMs.coerceAtLeast(0) else 0

    /**
     * Nettoarbeitszeit aus Brutto-Anwesenheit [gross] und angerechneten Pausen [breaks]: fehlende
     * Pause bis 30 min ab 6 h und bis 45 min ab 9 h (netto) wird abgezogen, gleitend.
     */
    fun net(gross: Long, breaks: Long): Long {
        val missing1 = (30 * M - breaks).coerceAtLeast(0)
        val missing2 = (45 * M - breaks).coerceAtLeast(0) - missing1
        var n = gross.coerceAtLeast(0)
        if (n > 6 * H) n = maxOf(6 * H, n - missing1)
        if (n > 9 * H) n = maxOf(9 * H, n - missing2)
        return n
    }

    /** Beim Einloggen: Unterbrechung seit dem letzten Ausloggen heute als Pause anrechnen (ab 15 min). */
    fun open(stored: Day, now: ZonedDateTime): Day {
        if (stored.day != now.toLocalDate() || stored.lastEnd <= 0) return stored
        val gap = now.toInstant().toEpochMilli() - stored.lastEnd
        return if (gap >= MIN_BREAK_MS) stored.copy(breakMs = stored.breakMs + gap) else stored
    }

    fun startOfDay(now: ZonedDateTime): Long = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()

    /** Summe der abgeschlossenen Sitzungen heute (0, wenn der gespeicherte Tag nicht heute ist). */
    fun closedToday(stored: Day, now: ZonedDateTime): Long =
        if (stored.day == now.toLocalDate()) stored.closedMs.coerceAtLeast(0) else 0

    /**
     * Beginn der laufenden Sitzung, auf heute 0:00 begrenzt; 0 = keine laufende Sitzung
     * (nicht eingeloggt oder Beginn unbekannt, z. B. vor dem Update eingeloggt).
     */
    fun runningSince(state: StempelState, since: Long, now: ZonedDateTime): Long =
        if (state == StempelState.EINGELOGGT && since > 0) maxOf(since, startOfDay(now)) else 0

    /** Gesamte Arbeitszeit heute bis [now]. */
    fun totalToday(stored: Day, state: StempelState, since: Long, now: ZonedDateTime): Long {
        val running = runningSince(state, since, now)
        val nowMs = now.toInstant().toEpochMilli()
        return closedToday(stored, now) + if (running > 0) (nowMs - running).coerceAtLeast(0) else 0
    }

    /** Neuer gespeicherter Tag beim Verlassen von `EINGELOGGT` (laufende Sitzung abschließen). */
    fun close(stored: Day, since: Long, now: ZonedDateTime): Day = Day(
        now.toLocalDate(),
        totalToday(stored, StempelState.EINGELOGGT, since, now),
        breaksToday(stored, now),
        now.toInstant().toEpochMilli(),
    )
}
