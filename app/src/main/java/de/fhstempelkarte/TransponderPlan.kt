package de.fhstempelkarte

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Zeitrechnung für die Erinnerung „Transponder aufladen“ (rein, `TransponderPlanTest`).
 * Unabhängig vom Stempel-Zeitfenster: eigene Uhrzeit (Minuten ab Mitternacht) und eigene
 * Wochentage (Bit 0 = Montag, wie [WindowConfig.weekdayMask]).
 */
object TransponderPlan {

    const val SUNDAY = 1 shl 6

    fun isValid(minute: Int, mask: Int): Boolean = minute in 0 until 24 * 60 && mask and 0b1111111 != 0

    /** Nächste Erinnerung echt nach [now]; null, wenn kein Wochentag gewählt ist. */
    fun next(now: ZonedDateTime, minute: Int, mask: Int): ZonedDateTime? {
        if (!isValid(minute, mask)) return null
        for (i in 0L..7L) {
            val date = now.toLocalDate().plusDays(i)
            if (mask and (1 shl (date.dayOfWeek.value - 1)) == 0) continue
            // ZonedDateTime.of schiebt Uhrzeiten in einer Zeitumstellungs-Lücke nach vorn.
            val at = ZonedDateTime.of(date, LocalTime.of(minute / 60, minute % 60), now.zone)
            if (at.isAfter(now)) return at
        }
        return null
    }
}
