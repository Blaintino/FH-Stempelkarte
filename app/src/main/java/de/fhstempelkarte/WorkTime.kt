package de.fhstempelkarte

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Arbeitszeit pro Tag (Wunsch des Nutzers: im Chip-Widget anzeigen, mehrmaliges Ein- und Ausloggen
 * am Tag aufsummiert). Gezählt wird die Zeit in `EINGELOGGT`, nur der heutige Tag: Eine Sitzung über
 * Mitternacht zählt heute ab 0:00. Rein, getestet (`WorkTimeTest`).
 */
object WorkTime {
    /** Abgeschlossene Sitzungen eines Tages. */
    data class Day(val day: LocalDate?, val closedMs: Long)

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
    fun close(stored: Day, since: Long, now: ZonedDateTime): Day =
        Day(now.toLocalDate(), totalToday(stored, StempelState.EINGELOGGT, since, now))
}
