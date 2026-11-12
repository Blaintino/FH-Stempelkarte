package de.fhstempelkarte

/**
 * Öffentlicher Vertrag des Kartei Launchers für andere eigene Apps (TODO Punkt 26), siehe
 * `SCHNITTSTELLE.md`. Reines Kotlin ohne Android – **darf in andere Apps kopiert werden**
 * (dort nur die `package`-Zeile anpassen). Namen und Bedeutung nie still ändern, nur ergänzen;
 * Unverträgliches erhöht [VERSION].
 *
 * Geschützt durch die Berechtigung [BERECHTIGUNG] (Schutzstufe `signature`): nur Apps mit
 * demselben Signaturschlüssel wie der Launcher dürfen senden.
 */
object KarteiSchnittstelle {
    const val VERSION = 1

    /** Paketname des Launchers (Release). Debug-Build: `de.blaintino.kartei.debug`. */
    const val PAKET = "de.blaintino.kartei"

    /** Berechtigung, die die sendende App braucht (`<uses-permission>`). */
    const val BERECHTIGUNG = "$PAKET.permission.AKZENT"

    /**
     * Akzentfarbe des Launchers setzen. Expliziter Broadcast (`setPackage(PAKET)`), Extra
     * [EXTRA_FARBE]. Die zuletzt gesetzte Farbe einer App gilt, bis sie zurückgenommen wird
     * oder eine andere App später sendet.
     */
    const val AKTION_AKZENT_SETZEN = "$PAKET.action.AKZENT_SETZEN"

    /** Eigene Farbe wieder freigeben; danach gilt die nächste Quelle (andere App, Automatik, feste Farbe). */
    const val AKTION_AKZENT_ZURUECKNEHMEN = "$PAKET.action.AKZENT_ZURUECKNEHMEN"

    /** Int: Farbe als ARGB (`0xFFRRGGBB`). Ohne Deckkraft (`0x00…`) wird sie voll deckend genommen. */
    const val EXTRA_FARBE = "farbe"

    /**
     * String, freiwillig: Anzeigename der App. Den Absender erkennt der Launcher ab Android 14
     * selbst, wenn die App `BroadcastOptions.setShareIdentityEnabled(true)` setzt – dann wird
     * der echte App-Name angezeigt; [EXTRA_APP] ist nur der Rückfall.
     */
    const val EXTRA_APP = "app"
}
