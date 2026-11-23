package de.fhstempelkarte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TransponderPlanTest {
    private val zone = ZoneId.of("Europe/Berlin")

    /** Mittwoch, 14.10.2026, 7:45. */
    private val now = ZonedDateTime.of(2026, 10, 14, 7, 45, 0, 0, zone)

    @Test
    fun naechsterSonntagAbend() =
        assertEquals(
            ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, zone),
            TransponderPlan.next(now, 20 * 60, TransponderPlan.SUNDAY),
        )

    @Test
    fun heuteNochSpaeter() =
        assertEquals(
            ZonedDateTime.of(2026, 10, 14, 20, 0, 0, 0, zone),
            TransponderPlan.next(now, 20 * 60, WindowConfig.WORKDAYS),
        )

    @Test
    fun heuteSchonVorbeiDannNaechsteWoche() =
        // Nur mittwochs, 7:00 ist schon vorbei → Mittwoch in einer Woche.
        assertEquals(
            ZonedDateTime.of(2026, 10, 21, 7, 0, 0, 0, zone),
            TransponderPlan.next(now, 7 * 60, 1 shl 2),
        )

    @Test
    fun genauJetztZaehltNicht() =
        assertEquals(
            now.plusDays(7),
            TransponderPlan.next(now, 7 * 60 + 45, 1 shl 2),
        )

    @Test
    fun keinTagKeineErinnerung() = assertNull(TransponderPlan.next(now, 20 * 60, 0))

    @Test
    fun zeitumstellung() =
        // 29.03.2026, 2:30 gibt es nicht → 3:30 Sommerzeit.
        assertEquals(
            ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, zone),
            TransponderPlan.next(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, zone), 2 * 60 + 30, TransponderPlan.SUNDAY),
        )
}
