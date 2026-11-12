package de.fhstempelkarte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class KarteiAkzentTest {
    private val an = Config(karteiAkzent = true, karteiOut = 0xFF112233.toInt(), karteiIn = 0xFF445566.toInt())

    @Test
    fun farbeJeZustand() {
        assertEquals(0xFF112233.toInt(), KarteiAkzent.farbeFuer(an, StempelState.NICHT_EINGELOGGT))
        assertEquals(0xFF445566.toInt(), KarteiAkzent.farbeFuer(an, StempelState.EINGELOGGT))
        assertNull(KarteiAkzent.farbeFuer(an, StempelState.NEUTRAL))
    }

    @Test
    fun ausgeschaltetNimmtZurueck() {
        StempelState.entries.forEach { assertNull(KarteiAkzent.farbeFuer(an.copy(karteiAkzent = false), it)) }
    }

    @Test
    fun hsvHinUndZurueck() {
        listOf(0xFFFF7043.toInt(), 0xFF66BB6A.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF3F51B5.toInt()).forEach { f ->
            val (h, s, v) = Farbraum.argbZuHsv(f)
            val z = Farbraum.hsvZuArgb(h, s, v)
            for (k in listOf(0, 8, 16)) assert(abs(((f shr k) and 0xFF) - ((z shr k) and 0xFF)) <= 1) { Farbraum.hex(f) }
            assertEquals(0xFF, (z ushr 24) and 0xFF)
        }
        assertEquals("#FF7043", Farbraum.hex(0xFFFF7043.toInt()))
    }

    @Test
    fun vertragWieImLauncher() {
        assertEquals("de.blaintino.kartei.permission.AKZENT", KarteiSchnittstelle.BERECHTIGUNG)
        assertEquals("de.blaintino.kartei.action.AKZENT_SETZEN", KarteiSchnittstelle.AKTION_AKZENT_SETZEN)
        assertEquals("de.blaintino.kartei.action.AKZENT_ZURUECKNEHMEN", KarteiSchnittstelle.AKTION_AKZENT_ZURUECKNEHMEN)
        assertEquals("farbe", KarteiSchnittstelle.EXTRA_FARBE)
    }
}
