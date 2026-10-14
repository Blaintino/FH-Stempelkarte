package de.fhstempelkarte

/** Textanordnung für die erzeugten Hintergründe, ohne Android – testbar. */
data class TextLayout(val textSize: Float, val lines: List<String>, val baselines: List<Float>, val centerX: Float)

object WallpaperLayout {
    /** Mittlere Breite eines fetten Großbuchstabens im Verhältnis zur Schriftgröße (Schätzung). */
    const val CHAR_WIDTH = 0.66f
    const val LINE_HEIGHT = 1.2f

    fun compute(width: Int, height: Int, text: String): TextLayout {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
        val longest = lines.maxOf { it.length }.coerceAtLeast(1)
        val byWidth = width * 0.85f / (longest * CHAR_WIDTH)
        val byHeight = height * 0.4f / (lines.size * LINE_HEIGHT)
        val size = minOf(byWidth, byHeight, width * 0.2f)
        val lineH = size * LINE_HEIGHT
        // Etwas unter der Mitte: oben stehen auf dem Sperrbildschirm Uhr und Datum.
        val top = height * 0.55f - lineH * lines.size / 2f
        val baselines = lines.indices.map { top + lineH * it + size }
        return TextLayout(size, lines, baselines, width / 2f)
    }
}
