package de.fhstempelkarte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeWindowTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val cfg = WindowConfig()

    private fun t(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test
    fun imFenster() {
        // 2026-10-14 ist ein Mittwoch
        assertTrue(TimeWindow.isInWindow(t(2026, 10, 14, 7, 0), cfg))
        assertTrue(TimeWindow.isInWindow(t(2026, 10, 14, 8, 59), cfg))
        assertFalse(TimeWindow.isInWindow(t(2026, 10, 14, 9, 0), cfg))
        assertFalse(TimeWindow.isInWindow(t(2026, 10, 14, 6, 59), cfg))
    }

    @Test
    fun wochenendeNicht() {
        assertFalse(TimeWindow.isInWindow(t(2026, 10, 17, 8), cfg)) // Samstag
        assertFalse(TimeWindow.isInWindow(t(2026, 10, 18, 8), cfg)) // Sonntag
    }

    @Test
    fun naechsterStartVorUndNachDemFenster() {
        assertEquals(t(2026, 10, 14, 7), TimeWindow.nextStart(t(2026, 10, 14, 6), cfg))
        assertEquals(t(2026, 10, 15, 7), TimeWindow.nextStart(t(2026, 10, 14, 10), cfg))
    }

    @Test
    fun startMittenImFenster() {
        val now = t(2026, 10, 14, 7, 30)
        assertEquals(t(2026, 10, 14, 9), TimeWindow.currentEnd(now, cfg))
        assertEquals(t(2026, 10, 14, 9), TimeWindow.nextEnd(now, cfg))
        assertEquals(t(2026, 10, 15, 7), TimeWindow.nextStart(now, cfg))
    }

    @Test
    fun freitagNachmittagSpringtAufMontag() {
        val fr = t(2026, 10, 16, 15)
        assertEquals(t(2026, 10, 19, 7), TimeWindow.nextStart(fr, cfg))
        assertEquals(t(2026, 10, 19, 9), TimeWindow.nextEnd(fr, cfg))
        assertNull(TimeWindow.currentEnd(fr, cfg))
    }

    @Test
    fun zeitumstellungWinterzeit() {
        // Sonntag 25.10.2026 Umstellung auf Winterzeit; Montag 7:00 muss MEZ (+01:00) sein.
        val start = TimeWindow.nextStart(t(2026, 10, 24, 12), cfg)!!
        assertEquals(t(2026, 10, 26, 7), start)
        assertEquals(1 * 3600, start.offset.totalSeconds)
    }

    @Test
    fun zeitumstellungSommerzeitLuecke() {
        // 29.03.2026 2:00 → 3:00. Fenster 2:30–4:00 täglich: Beginn rutscht auf 3:30 MESZ.
        val c = WindowConfig(150, 240, 0b1111111)
        assertEquals(t(2026, 3, 29, 3, 30), TimeWindow.nextStart(t(2026, 3, 29, 0), c))
    }

    @Test
    fun keineTageKeinStart() {
        assertNull(TimeWindow.nextStart(t(2026, 10, 14, 6), cfg.copy(weekdayMask = 0)))
    }

    @Test
    fun gueltigkeit() {
        assertTrue(TimeWindow.isValid(cfg))
        assertFalse(TimeWindow.isValid(WindowConfig(9 * 60, 7 * 60)))
        assertFalse(TimeWindow.isValid(WindowConfig(7 * 60, 7 * 60)))
    }
}
