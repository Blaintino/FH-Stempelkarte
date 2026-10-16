package de.fhstempelkarte

/** Start- oder Sperrbildschirm. */
enum class Screen(val label: String) {
    HOME("Startbildschirm"),
    LOCK("Sperrbildschirm"),
}

/** Was auf einen Bildschirm kommt. [Image.screen]: wessen Bilddatei (Sperre kann die vom Start nehmen). */
sealed interface WallpaperSource {
    data class Image(val screen: Screen) : WallpaperSource
    data class Generated(val color: Int) : WallpaperSource
    data object Missing : WallpaperSource
}

/**
 * Welcher Hintergrund für welchen Bildschirm – ohne Android, testbar.
 * Der Sperrbildschirm übernimmt den Startbildschirm, solange er nichts Eigenes hat
 * (eigenes Bild oder, außer bei `NEUTRAL`, eigene Farbe).
 */
object WallpaperPlan {

    /**
     * [hasImage]: gibt es für (Zustand, Bildschirm) eine eigene Bilddatei?
     * Für `NEUTRAL` ist das das gewählte Original; ohne Original [WallpaperSource.Missing].
     */
    fun source(cfg: Config, state: StempelState, screen: Screen, hasImage: (Screen) -> Boolean): WallpaperSource {
        if (screen == Screen.LOCK && !hasOwnLock(cfg, state, hasImage(Screen.LOCK))) {
            return source(cfg, state, Screen.HOME, hasImage)
        }
        if (hasImage(screen)) return WallpaperSource.Image(screen)
        if (state == StempelState.NEUTRAL) return WallpaperSource.Missing
        return WallpaperSource.Generated(color(cfg, state, screen))
    }

    /** Hat der Sperrbildschirm für [state] etwas Eigenes? */
    fun hasOwnLock(cfg: Config, state: StempelState, hasLockImage: Boolean): Boolean =
        hasLockImage || lockColor(cfg, state) != null

    fun color(cfg: Config, state: StempelState, screen: Screen): Int {
        val home = if (state == StempelState.EINGELOGGT) cfg.colorIn else cfg.colorOut
        return if (screen == Screen.LOCK) lockColor(cfg, state) ?: home else home
    }

    private fun lockColor(cfg: Config, state: StempelState): Int? = when (state) {
        StempelState.NEUTRAL -> null
        StempelState.NICHT_EINGELOGGT -> cfg.colorOutLock
        StempelState.EINGELOGGT -> cfg.colorInLock
    }
}
