package de.fhstempelkarte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteropContractTest {
    private val out = 0xFFC62828.toInt()
    private val inn = 0xFF2E7D32.toInt()

    private fun row(state: StempelState) = InteropContract.statusRow(state, 1234L, "Test", 99L, out, inn)
    private fun col(name: String) = InteropContract.COLUMNS.indexOf(name)

    @Test
    fun zeileHatAlleSpalten() =
        assertEquals(InteropContract.COLUMNS.size, row(StempelState.NEUTRAL).size)

    @Test
    fun zeileEnthaeltWerteAnRichtigerStelle() {
        val r = row(StempelState.NICHT_EINGELOGGT)
        assertEquals(InteropContract.VERSION, r[col(InteropContract.COL_VERSION)])
        assertEquals("NICHT_EINGELOGGT", r[col(InteropContract.COL_STATE)])
        assertEquals("Nicht eingeloggt", r[col(InteropContract.COL_LABEL)])
        assertEquals(1234L, r[col(InteropContract.COL_SINCE)])
        assertEquals("Test", r[col(InteropContract.COL_TRIGGER_INFO)])
        assertEquals(99L, r[col(InteropContract.COL_DETECTING_UNTIL)])
    }

    @Test
    fun farbeJeZustand() {
        assertNull(row(StempelState.NEUTRAL)[col(InteropContract.COL_COLOR)])
        assertEquals(out, row(StempelState.NICHT_EINGELOGGT)[col(InteropContract.COL_COLOR)])
        assertEquals(inn, row(StempelState.EINGELOGGT)[col(InteropContract.COL_COLOR)])
    }

    @Test
    fun aktionenWerdenZuDirektwerten() {
        assertEquals(ZeusActivity.DIRECT_IN, InteropContract.directFor(InteropContract.ACTION_LOGIN))
        assertEquals(ZeusActivity.DIRECT_OUT, InteropContract.directFor(InteropContract.ACTION_LOGOUT))
        assertNull(InteropContract.directFor(InteropContract.ACTION_CHOOSE))
        assertNull(InteropContract.directFor("fremd"))
    }

    /** Namen sind öffentlich (Chip liest sie) – Änderung hier heißt: Chip mitändern. */
    @Test
    fun oeffentlicheNamenStabil() {
        assertEquals("de.fhstempelkarte.permission.INTEROP", InteropContract.PERMISSION)
        assertEquals("content://de.fhstempelkarte.status/status", InteropContract.STATUS_URI)
        assertEquals("de.fhstempelkarte.action.ZUSTAND_GEAENDERT", InteropContract.ACTION_STATE_CHANGED)
        assertEquals("de.fhstempelkarte.action.EINLOGGEN", InteropContract.ACTION_LOGIN)
        assertEquals("de.fhstempelkarte.action.AUSLOGGEN", InteropContract.ACTION_LOGOUT)
        assertEquals("de.fhstempelkarte.action.AUSWAHL", InteropContract.ACTION_CHOOSE)
    }
}
