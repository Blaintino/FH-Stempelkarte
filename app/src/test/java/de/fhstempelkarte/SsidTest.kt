package de.fhstempelkarte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsidTest {
    @Test
    fun anfuehrungszeichenWeg() = assertEquals("eduroam", Ssid.normalize("\"eduroam\""))

    @Test
    fun unbekannt() {
        assertNull(Ssid.normalize("<unknown ssid>"))
        assertNull(Ssid.normalize("\"\""))
        assertNull(Ssid.normalize(null))
        assertFalse(Ssid.matches("<unknown ssid>", listOf("eduroam")))
    }

    @Test
    fun vergleich() {
        assertTrue(Ssid.matches("\"eduroam\"", listOf("eduroam")))
        assertTrue(Ssid.matches("EDUROAM", listOf("eduroam")))
        assertFalse(Ssid.matches("eduroam-gast", listOf("eduroam")))
        assertFalse(Ssid.matches("eduroam", emptyList()))
    }

    @Test
    fun liste() = assertEquals(listOf("eduroam", "FH-Gast"), Ssid.parseList(" eduroam, FH-Gast;\nEduroam ,"))
}
