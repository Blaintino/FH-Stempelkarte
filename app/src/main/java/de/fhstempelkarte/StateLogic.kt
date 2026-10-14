package de.fhstempelkarte

import java.time.LocalDate
import java.time.ZonedDateTime

enum class StempelState(val label: String) {
    NEUTRAL("Neutral"),
    NICHT_EINGELOGGT("Nicht eingeloggt"),
    EINGELOGGT("Eingeloggt"),
}

object StateLogic {
    /**
     * Auslösen (und dafür überhaupt erkennen) nur im Zeitfenster, nur aus `NEUTRAL` und nur
     * einmal am Tag. Bei `EINGELOGGT` (z. B. über Nacht nicht abgemeldet) passiert nichts.
     */
    fun shouldTrigger(
        state: StempelState,
        lastTriggerDay: LocalDate?,
        now: ZonedDateTime,
        cfg: WindowConfig,
    ): Boolean =
        state == StempelState.NEUTRAL &&
            lastTriggerDay != now.toLocalDate() &&
            TimeWindow.isInWindow(now, cfg)
}
