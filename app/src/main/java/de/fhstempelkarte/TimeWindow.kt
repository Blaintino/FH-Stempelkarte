package de.fhstempelkarte

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Zeitfenster, in dem „an der FH“ erkannt wird. Minuten ab Mitternacht, Bit 0 = Montag. */
data class WindowConfig(
    val startMinute: Int = 7 * 60,
    val endMinute: Int = 9 * 60,
    val weekdayMask: Int = WORKDAYS,
) {
    companion object {
        const val WORKDAYS = 0b0011111
    }
}

/** Reine Zeitrechnung, ohne Android – testbar in `TimeWindowTest`. */
object TimeWindow {

    fun isActiveDay(cfg: WindowConfig, day: DayOfWeek): Boolean =
        cfg.weekdayMask and (1 shl (day.value - 1)) != 0

    private fun at(date: LocalDate, minute: Int, ref: ZonedDateTime): ZonedDateTime =
        // ZonedDateTime.of schiebt Uhrzeiten in einer Zeitumstellungs-Lücke nach vorn.
        ZonedDateTime.of(date, LocalTime.of(minute / 60, minute % 60), ref.zone)

    fun startOn(date: LocalDate, cfg: WindowConfig, ref: ZonedDateTime) = at(date, cfg.startMinute, ref)

    fun endOn(date: LocalDate, cfg: WindowConfig, ref: ZonedDateTime) = at(date, cfg.endMinute, ref)

    fun isInWindow(now: ZonedDateTime, cfg: WindowConfig): Boolean {
        if (!isActiveDay(cfg, now.dayOfWeek)) return false
        val day = now.toLocalDate()
        return !now.isBefore(startOn(day, cfg, now)) && now.isBefore(endOn(day, cfg, now))
    }

    /** Ende des laufenden Fensters, oder null, wenn gerade keins läuft. */
    fun currentEnd(now: ZonedDateTime, cfg: WindowConfig): ZonedDateTime? =
        if (isInWindow(now, cfg)) endOn(now.toLocalDate(), cfg, now) else null

    /** Nächster Fensterbeginn echt nach [now]; null, wenn kein Wochentag gewählt ist. */
    fun nextStart(now: ZonedDateTime, cfg: WindowConfig): ZonedDateTime? {
        for (i in 0L..7L) {
            val date = now.toLocalDate().plusDays(i)
            if (!isActiveDay(cfg, date.dayOfWeek)) continue
            val start = startOn(date, cfg, now)
            if (start.isAfter(now)) return start
        }
        return null
    }

    /** Ende des laufenden Fensters, sonst Ende des nächsten. */
    fun nextEnd(now: ZonedDateTime, cfg: WindowConfig): ZonedDateTime? =
        currentEnd(now, cfg) ?: nextStart(now, cfg)?.let { endOn(it.toLocalDate(), cfg, now) }

    fun isValid(cfg: WindowConfig): Boolean =
        cfg.startMinute in 0 until 24 * 60 && cfg.endMinute in 1 until 24 * 60 &&
            cfg.startMinute < cfg.endMinute
}
