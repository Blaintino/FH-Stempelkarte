package de.fhstempelkarte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperLayoutTest {
    @Test
    fun passtInDieBreite() {
        val l = WallpaperLayout.compute(1080, 2400, Wallpapers.TEXT_OUT)
        assertEquals(listOf("NICHT", "EINGELOGGT"), l.lines)
        val longest = l.lines.maxOf { it.length }
        assertTrue(longest * WallpaperLayout.CHAR_WIDTH * l.textSize <= 1080 * 0.85f + 0.5f)
        assertEquals(540f, l.centerX)
    }

    @Test
    fun zeilenUntereinanderUndImBild() {
        val l = WallpaperLayout.compute(1080, 2400, Wallpapers.TEXT_OUT)
        assertTrue(l.baselines[1] > l.baselines[0])
        assertTrue(l.baselines.first() - l.textSize > 0)
        assertTrue(l.baselines.last() < 2400)
    }

    @Test
    fun einzeiligGroesser() {
        val one = WallpaperLayout.compute(1080, 2400, Wallpapers.TEXT_IN)
        val two = WallpaperLayout.compute(1080, 2400, "SEHR LANGER TEXT HIER")
        assertTrue(one.textSize > two.textSize)
    }
}
