package de.fhstempelkarte

/**
 * Öffentlicher Vertrag für andere eigene Apps (zuerst das Widget der Kalender-App Chip, später
 * Quälgeist), siehe `INTEROP.md`. Reines Kotlin ohne Android, damit er getestet und in andere
 * Apps kopiert werden kann. **Namen und Bedeutung nie still ändern** – nur ergänzen; wer etwas
 * Unverträgliches ändert, erhöht [VERSION].
 *
 * Alles ist durch die Berechtigung `<applicationId>.permission.INTEROP` (Schutzstufe
 * `signature`) geschützt: nur Apps mit demselben Signaturschlüssel kommen heran.
 */
object InteropContract {
    /** Version der Schnittstelle, steht auch in jeder Status-Zeile ([COL_VERSION]). */
    const val VERSION = 1

    const val PACKAGE = "de.fhstempelkarte"

    /** Berechtigung der Release-App (Debug: `de.fhstempelkarte.debug.permission.INTEROP`). */
    const val PERMISSION = "$PACKAGE.permission.INTEROP"

    /** Authority des Status-Providers: `<applicationId>.status`. */
    const val AUTHORITY = "$PACKAGE.status"
    const val PATH_STATUS = "status"

    /** `content://de.fhstempelkarte.status/status` – genau eine Zeile. */
    const val STATUS_URI = "content://$AUTHORITY/$PATH_STATUS"

    // Spalten der Status-Zeile.
    /** Int: [VERSION]. */
    const val COL_VERSION = "schnittstelle"

    /** String: `NEUTRAL`, `NICHT_EINGELOGGT` oder `EINGELOGGT` (Namen von [StempelState]). */
    const val COL_STATE = "zustand"

    /** String: Anzeigetext, z. B. „Nicht eingeloggt“. */
    const val COL_LABEL = "bezeichnung"

    /** Long: Zeitpunkt (ms seit 1970) des letzten Zustandswechsels, 0 = unbekannt. */
    const val COL_SINCE = "seit"

    /** String oder null: zuletzt ausgelöst, z. B. „Mo 03.11. 07:42 · WLAN eduroam“. */
    const val COL_TRIGGER_INFO = "ausgeloest"

    /** Long: Ende der laufenden Erkennung (ms seit 1970), 0 = keine. */
    const val COL_DETECTING_UNTIL = "erkennung_bis"

    /** Int oder null: Farbe des Stempelkarten-Widgets (ARGB); null = eigene neutrale Farbe nehmen. */
    const val COL_COLOR = "farbe"

    val COLUMNS = arrayOf(
        COL_VERSION, COL_STATE, COL_LABEL, COL_SINCE, COL_TRIGGER_INFO, COL_DETECTING_UNTIL, COL_COLOR,
    )

    /**
     * Expliziter Broadcast bei jedem Zustandswechsel (und nach Boot/Update), nur an
     * [CONSUMERS] und nur an Empfänger mit [PERMISSION]. Extras: [COL_STATE], [COL_SINCE].
     */
    const val ACTION_STATE_CHANGED = "$PACKAGE.action.ZUSTAND_GEAENDERT"

    // Aktionen zum Starten (Activity, ebenfalls nur mit [PERMISSION]); danach öffnet sich ZEUS.
    /** Sofort einloggen (wie Widget-Knopf „Einloggen“). */
    const val ACTION_LOGIN = "$PACKAGE.action.EINLOGGEN"

    /** Sofort ausloggen (wie Widget-Knopf; ohne Originalbild kommt die Rückfrage). */
    const val ACTION_LOGOUT = "$PACKAGE.action.AUSLOGGEN"

    /** Normale Auswahl Einloggen/Ausloggen/Abbrechen. */
    const val ACTION_CHOOSE = "$PACKAGE.action.AUSWAHL"

    /** Apps, die den Broadcast bekommen (müssen auch im Manifest unter `<queries>` stehen). */
    val CONSUMERS = listOf("de.blaintino.chip", "de.quaelgeist", "de.quaelgeist.beta")

    /** Aktion über die geschützte Alias → Direktwert für `ZeusActivity` (null = normale Auswahl). */
    fun directFor(action: String?): String? = when (action) {
        ACTION_LOGIN -> ZeusActivity.DIRECT_IN
        ACTION_LOGOUT -> ZeusActivity.DIRECT_OUT
        else -> null
    }

    /** Die Status-Zeile in der Reihenfolge von [COLUMNS]. */
    fun statusRow(
        state: StempelState,
        since: Long,
        triggerInfo: String?,
        detectingUntil: Long,
        colorOut: Int,
        colorIn: Int,
    ): Array<Any?> = arrayOf(
        VERSION,
        state.name,
        state.label,
        since,
        triggerInfo,
        detectingUntil,
        when (state) {
            StempelState.NEUTRAL -> null
            StempelState.NICHT_EINGELOGGT -> colorOut
            StempelState.EINGELOGGT -> colorIn
        },
    )
}
