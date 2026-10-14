package de.fhstempelkarte

import android.content.Context
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Zentrale Stelle für Zustandswechsel. Blockiert (Hintergrundbild) – nicht im Hauptthread. */
object Stempel {

    /**
     * Der einzige Weg, den Zustand zu ändern: speichern, Meldung, Widget, Erkennung,
     * Hintergrund. Hier später den Broadcast an Quälgeist ergänzen (siehe CLAUDE.md).
     * [clearIfNoOriginal]: bei `NEUTRAL` ohne gespeichertes Original den Systemhintergrund
     * wiederherstellen – nur nach Rückfrage beim Nutzer.
     */
    fun setState(ctx: Context, state: StempelState, clearIfNoOriginal: Boolean = false): Wallpapers.Result {
        val store = Store(ctx)
        store.state = state
        if (state == StempelState.NICHT_EINGELOGGT) {
            runCatching { Notifier.show(ctx) }
        } else {
            Notifier.cancel(ctx)
        }
        runCatching { ZeusWidget.updateAll(ctx) }
        runCatching { Scheduler.planAll(ctx) }
        return Wallpapers.apply(ctx, state, clearIfNoOriginal)
    }

    /** Aus einem Auslöser (WLAN, Standort): nur, wenn [StateLogic.shouldTrigger] es erlaubt. */
    @Synchronized
    fun maybeTrigger(ctx: Context, reason: String): Boolean {
        val store = Store(ctx)
        val now = ZonedDateTime.now()
        if (!StateLogic.shouldTrigger(store.state, store.lastTriggerDay, now, store.config.window)) {
            return false
        }
        store.lastTriggerDay = now.toLocalDate()
        store.lastTriggerInfo = "${now.format(INFO_FORMAT)} · $reason"
        setState(ctx, StempelState.NICHT_EINGELOGGT)
        return true
    }

    /** „Test auslösen“ in der Prüfliste: ohne Zeitfenster und ohne Tagessperre. */
    fun testTrigger(ctx: Context) {
        Store(ctx).lastTriggerInfo = "${ZonedDateTime.now().format(INFO_FORMAT)} · Test"
        setState(ctx, StempelState.NICHT_EINGELOGGT)
    }

    private val INFO_FORMAT = DateTimeFormatter.ofPattern("EE dd.MM. HH:mm")
}
