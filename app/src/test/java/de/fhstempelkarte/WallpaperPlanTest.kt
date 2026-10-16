package de.fhstempelkarte

import de.fhstempelkarte.WallpaperSource.Generated
import de.fhstempelkarte.WallpaperSource.Image
import de.fhstempelkarte.WallpaperSource.Missing
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperPlanTest {
    private val cfg = Config()
    private fun images(vararg s: Screen): (Screen) -> Boolean = { it in s }

    @Test
    fun sperreOhneEigenesWieStart() {
        assertEquals(Generated(cfg.colorOut), WallpaperPlan.source(cfg, StempelState.NICHT_EINGELOGGT, Screen.LOCK, images()))
        assertEquals(Image(Screen.HOME), WallpaperPlan.source(cfg, StempelState.EINGELOGGT, Screen.LOCK, images(Screen.HOME)))
        assertEquals(Image(Screen.HOME), WallpaperPlan.source(cfg, StempelState.NEUTRAL, Screen.LOCK, images(Screen.HOME)))
    }

    @Test
    fun sperreMitEigenemBild() {
        assertEquals(Image(Screen.LOCK), WallpaperPlan.source(cfg, StempelState.NEUTRAL, Screen.LOCK, images(Screen.HOME, Screen.LOCK)))
        assertEquals(Image(Screen.LOCK), WallpaperPlan.source(cfg, StempelState.EINGELOGGT, Screen.LOCK, images(Screen.LOCK)))
        // Start bleibt beim eigenen Bild bzw. bei der Farbe.
        assertEquals(Generated(cfg.colorIn), WallpaperPlan.source(cfg, StempelState.EINGELOGGT, Screen.HOME, images(Screen.LOCK)))
    }

    @Test
    fun sperreMitEigenerFarbeSchlaegtBildVomStart() {
        val c = cfg.copy(colorInLock = 0xFF1565C0.toInt())
        assertEquals(Generated(0xFF1565C0.toInt()), WallpaperPlan.source(c, StempelState.EINGELOGGT, Screen.LOCK, images(Screen.HOME)))
        assertEquals(Image(Screen.HOME), WallpaperPlan.source(c, StempelState.EINGELOGGT, Screen.HOME, images(Screen.HOME)))
        // Farbe für „Eingeloggt“ ändert „Nicht eingeloggt“ nicht.
        assertEquals(Generated(cfg.colorOut), WallpaperPlan.source(c, StempelState.NICHT_EINGELOGGT, Screen.LOCK, images()))
    }

    @Test
    fun normalOhneOriginalFehlt() {
        assertEquals(Missing, WallpaperPlan.source(cfg, StempelState.NEUTRAL, Screen.HOME, images()))
        assertEquals(Missing, WallpaperPlan.source(cfg, StempelState.NEUTRAL, Screen.LOCK, images()))
        // Nur für die Sperre gewählt: Start fehlt weiter.
        assertEquals(Missing, WallpaperPlan.source(cfg, StempelState.NEUTRAL, Screen.HOME, images(Screen.LOCK)))
    }
}
