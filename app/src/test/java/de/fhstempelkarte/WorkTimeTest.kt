package de.fhstempelkarte

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WorkTimeTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(h: Int, m: Int = 0, day: Int = 3) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)
    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()
    private val hour = 3_600_000L
    private val empty = WorkTime.Day(null, 0)

    @Test
    fun laufendeSitzungZaehlt() =
        assertEquals(2 * hour, WorkTime.totalToday(empty, StempelState.EINGELOGGT, ms(at(8)), at(10)))

    @Test
    fun mehrereSitzungenWerdenSummiert() {
        val first = WorkTime.close(empty, ms(at(8)), at(12)) // 4 h
        val second = WorkTime.close(first, ms(at(13)), at(15)) // + 2 h
        assertEquals(6 * hour, second.closedMs)
        // Danach wieder eingeloggt: 6 h + laufende 30 min.
        assertEquals(6 * hour + hour / 2, WorkTime.totalToday(second, StempelState.EINGELOGGT, ms(at(16)), at(16, 30)))
    }

    @Test
    fun ausgeloggtZaehltNurAbgeschlossenes() {
        val day = WorkTime.Day(at(9).toLocalDate(), 3 * hour)
        assertEquals(3 * hour, WorkTime.totalToday(day, StempelState.NEUTRAL, ms(at(8)), at(18)))
    }

    @Test
    fun neuerTagBeginntBeiNull() {
        val yesterday = WorkTime.Day(at(9, day = 2).toLocalDate(), 8 * hour)
        assertEquals(0L, WorkTime.totalToday(yesterday, StempelState.NEUTRAL, 0, at(9)))
    }

    @Test
    fun sitzungUeberMitternachtZaehltHeuteAbNull() =
        assertEquals(7 * hour, WorkTime.totalToday(empty, StempelState.EINGELOGGT, ms(at(17, day = 2)), at(7)))

    @Test
    fun unbekannterBeginnZaehltNicht() {
        assertEquals(0L, WorkTime.runningSince(StempelState.EINGELOGGT, 0, at(9)))
        assertEquals(0L, WorkTime.totalToday(empty, StempelState.EINGELOGGT, 0, at(9)))
    }
}
