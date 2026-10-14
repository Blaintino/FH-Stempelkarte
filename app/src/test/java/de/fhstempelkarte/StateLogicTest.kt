package de.fhstempelkarte

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StateLogicTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val cfg = WindowConfig()
    private val now = ZonedDateTime.of(2026, 10, 14, 7, 45, 0, 0, zone)

    @Test
    fun neutralImFensterLoestAus() =
        assertTrue(StateLogic.shouldTrigger(StempelState.NEUTRAL, null, now, cfg))

    @Test
    fun nichtZweimalAmSelbenTag() =
        assertFalse(StateLogic.shouldTrigger(StempelState.NEUTRAL, now.toLocalDate(), now, cfg))

    @Test
    fun amNaechstenTagWieder() =
        assertTrue(StateLogic.shouldTrigger(StempelState.NEUTRAL, now.toLocalDate().minusDays(1), now, cfg))

    @Test
    fun eingeloggtNie() =
        assertFalse(StateLogic.shouldTrigger(StempelState.EINGELOGGT, null, now, cfg))

    @Test
    fun schonAusgeloestNie() =
        assertFalse(StateLogic.shouldTrigger(StempelState.NICHT_EINGELOGGT, null, now, cfg))

    @Test
    fun ausserhalbDesFenstersNie() =
        assertFalse(StateLogic.shouldTrigger(StempelState.NEUTRAL, null, now.withHour(10), cfg))
}
