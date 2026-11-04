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

    private val min = 60_000L

    @Test
    fun bisSechsStundenKeinAbzug() = assertEquals(6 * hour, WorkTime.net(6 * hour, 0))

    @Test
    fun abSechsStundenBleibtDieZeitStehen() {
        assertEquals(6 * hour, WorkTime.net(6 * hour + 20 * min, 0))
        assertEquals(6 * hour, WorkTime.net(6 * hour + 30 * min, 0))
        assertEquals(6 * hour + 30 * min, WorkTime.net(7 * hour, 0))
    }

    @Test
    fun abNeunStundenNochmalFuenfzehn() {
        assertEquals(9 * hour, WorkTime.net(9 * hour + 30 * min, 0))
        assertEquals(9 * hour, WorkTime.net(9 * hour + 45 * min, 0))
        assertEquals(9 * hour + 15 * min, WorkTime.net(10 * hour, 0))
    }

    @Test
    fun genommenePausenWerdenAngerechnet() {
        assertEquals(7 * hour, WorkTime.net(7 * hour, 30 * min))
        // 20 min genommen: es fehlen nur 10.
        assertEquals(6 * hour + 20 * min, WorkTime.net(6 * hour + 30 * min, 20 * min))
        // 30 min genommen: ab 9 h fehlen nur noch 15.
        assertEquals(9 * hour + 15 * min, WorkTime.net(9 * hour + 30 * min, 30 * min))
        assertEquals(10 * hour, WorkTime.net(10 * hour, 45 * min))
    }

    @Test
    fun pauseErstAbFuenfzehnMinuten() {
        val out = WorkTime.close(empty, ms(at(8)), at(12))
        assertEquals(0L, WorkTime.open(out, at(12, 10)).breakMs)
        assertEquals(30 * min, WorkTime.open(out, at(12, 30)).breakMs)
    }

    @Test
    fun pausenSummierenSichUndBleibenBeimAbschluss() {
        var d = WorkTime.close(empty, ms(at(8)), at(10))
        d = WorkTime.open(d, at(10, 20)) // 20 min
        d = WorkTime.close(d, ms(at(10, 20)), at(12))
        d = WorkTime.open(d, at(12, 30)) // 30 min
        assertEquals(50 * min, d.breakMs)
        assertEquals(50 * min, WorkTime.breaksToday(d, at(13)))
    }

    @Test
    fun pausenVonGesternZaehlenNicht() {
        val yesterday = WorkTime.Day(at(9, day = 2).toLocalDate(), 8 * hour, 45 * min, ms(at(17, day = 2)))
        assertEquals(0L, WorkTime.breaksToday(yesterday, at(9)))
        // Erstes Einloggen am neuen Tag: keine Pause von gestern 17:00 bis heute anrechnen.
        assertEquals(0L, WorkTime.breaksToday(WorkTime.open(yesterday, at(8)), at(8)))
    }

    @Test
    fun unbekannterBeginnZaehltNicht() {
        assertEquals(0L, WorkTime.runningSince(StempelState.EINGELOGGT, 0, at(9)))
        assertEquals(0L, WorkTime.totalToday(empty, StempelState.EINGELOGGT, 0, at(9)))
    }
}
