# FH Stempelkarte – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk 37): erinnert per Hintergrundbild
ans Einstempeln in ZEUS an der FH. Schwester-App von Quälgeist (`Blaintino/Quaelgeist`),
Werkzeuge und Muster von dort übernommen. Funktionen und Dateiübersicht in `README.md`.

## Status und Werkzeuge

Stand 2026-10-14: kompiliert, Lint ohne echte Befunde (nur Versions-Hinweise,
`InlinedApi` für die Alarm-Berechtigungs-Aktion – harmlos, wie in Quälgeist), 22 Unit-Tests
grün (`TimeWindowTest`, `StateLogicTest`, `SsidTest`, `WallpaperLayoutTest`; Zeitzone fest
`Europe/Berlin`). **Auf dem Gerät noch nicht getestet.** Der Nutzer installiert über
Obtainium, nicht per USB/adb. Erstes Release `fhstempelkarte-v2` (CI grün, signiert). Offen zu prüfen: App-Start/Meldung →
Auswahl → ZEUS startet und Hintergrund wechselt; WLAN- und Geofence-Auslösung im (vorgezogenen) Zeitfenster; nach Neustart neu
geplante Alarme; ob Huawei den Sperrbildschirm-Hintergrund übernimmt.
ZEUS-Paketname ist unbekannt (wird in der App aus den installierten Apps gewählt; Kandidaten
mit `zeus`/`isgus` stehen oben) – nicht raten und nicht fest eintragen.

- Toolchain wie Quälgeist: Gradle 9.8, AGP 9.4 mit eingebautem Kotlin (kein
  `org.jetbrains.kotlin.android`, nur `org.jetbrains.kotlin.plugin.compose`), Versionen in
  `gradle/libs.versions.toml`, JDK 21.
- Befehle: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- `.github/workflows/build-apk.yml`: Tests + Lint, signierte Release-APK aus den Secrets
  `ANDROID_KEYSTORE_BASE64` usw.; nur auf `main` mit Secrets ein Release
  `fhstempelkarte-v<run_number>` (Obtainium filtert auf `^fhstempelkarte-v`). Ohne Secrets
  Debug-APK mit Warnung. Nie auf Debug-Signatur umstellen.
- **Gleicher Keystore wie Quälgeist** (Voraussetzung für die spätere
  `signature`-Berechtigung, siehe unten). Nie einen Keystore einchecken oder neu erzeugen.
- `versionCode` aus `VERSION_CODE`; Debug: App-ID `de.fhstempelkarte.debug`, Name über
  Manifest-Platzhalter `appLabel`.

## Verhalten, das erhalten bleiben muss

- Zustände `NEUTRAL` / `NICHT_EINGELOGGT` / `EINGELOGGT` in SharedPreferences, geschrieben
  mit `commit()`. Laufzeit-Zustand und `Config` werden getrennt geschrieben (sonst
  überschreibt die Oberfläche mit alter Kopie einen gerade ausgelösten Zustand).
- **Alle Zustandswechsel nur über `Stempel.setState`** (Meldung, Erkennung neu
  planen, Hintergrund). Blockiert wegen des Hintergrunds – nie im Hauptthread.
- Auslösen nur über `Stempel.maybeTrigger` → `StateLogic.shouldTrigger`: im Fenster, aus
  `NEUTRAL`, höchstens einmal am Tag. „Test auslösen“ umgeht Fenster und Tagessperre und
  setzt `lastTriggerDay` nicht.
- **Keine Erinnerung zum Abmelden/Gehen** (ausdrücklicher Wunsch: der grüne Hintergrund ist
  die Erinnerung). `EINGELOGGT` bleibt über Nacht; dann wird morgens nichts ausgelöst.
- `Scheduler.planAll` ist idempotent und wird bei jedem Alarm, Boot/Update/Zeitänderung und
  App-Start aufgerufen. Fensterbeginn mit `setAlarmClock` (Fallback ohne Exakt-Berechtigung),
  Ende mit `setExactAndAllowWhileIdle` (kein zweites Wecker-Symbol).
- Erkennung nur im Fenster, danach alles abmelden (Akku). Keine Google-Dienste:
  `addProximityAlert` + `requestLocationUpdates` (Fused, sonst Netzwerk, sonst GPS, alle
  2 Min, eigene Entfernungsprüfung) + `registerNetworkCallback(…, PendingIntent)`. Diese
  PendingIntents müssen `FLAG_MUTABLE` sein (System hängt Extras an).
- Standort- und WLAN-Erkennung einzeln abschaltbar (`Config.useLocation`/`useWifi`, Wunsch des
  Nutzers); überall `locationActive`/`wifiActive` prüfen, auch in den Receivern (alte
  Registrierungen). `Detector.start` meldet zuerst alles ab. FH-Standort lässt sich löschen.
- SSID: zuerst `NetworkCapabilities.transportInfo`, ab Android 12 oft geschwärzt, dann
  `WifiManager.connectionInfo` (braucht Standort-Recht, im Hintergrund „Immer erlauben“).
- **Ablauf (Wunsch des Nutzers, 2026-10-14): kein Widget.** Meldung antippen oder App
  öffnen → `ZeusActivity` (durchsichtiger Dialog, Launcher-Activity) mit *Einloggen* /
  *Ausloggen* / *Abbrechen* / *Einstellungen* → ZEUS. `MainActivity` sind nur noch die
  Einstellungen (nicht exportiert); ohne gewählte ZEUS-App springt der Start direkt dorthin.
- Meldung: Kanal `einstempeln`, Kategorie `REMINDER`, nicht `ongoing`, `cancel` + `notify`
  (Huawei Health), kein Vollbild-Intent, keine Knöpfe – Antippen öffnet die Auswahl.
- Originalhintergrund wird nie vom System gelesen, sondern per Photo Picker gewählt und nach
  `files/original` kopiert. Ohne Original beim Abmelden **immer erst fragen**, bevor
  `WallpaperManager.clear()` läuft (`LogoutNoOriginalDialog`).
- Einstellungen → Hintergründe: drei Plätze *Normal* (`files/original`), *Einloggen-Erinnerung*
  und *Eingeloggt* (Farbe oder eigenes Bild `files/bild_<zustand>`), mit Vorschau
  (`Wallpapers.preview`). Änderung am aktiven Zustand wird sofort angewendet (Wunsch des
  Nutzers: alle drei in der App einstellbar).
- Erster Gerätetest 2026-10-16: Meldung kam erst beim Öffnen der App, die
  Hintergrund-Erkennung griff nicht. Deshalb: `EventLog` (Protokoll in der Prüfliste,
  `files/protokoll.txt`) an allen Hintergrund-Stellen und im Fenster eine 5-Min-Prüfung per
  Alarm (`Scheduler.ACTION_POLL` → `Detector.checkNow`). Protokoll beibehalten.
  Befund per adb (Samsung S26, Android 17): alle Rechte erteilt (inkl. Hintergrund-Standort,
  exakte Alarme), Akku-Ausnahme aktiv, Standby-Bucket „exempted“; der 7:00-Alarm kam
  pünktlich. Das Handy verband sich aber erst um 7:23:54 mit eduroam (beim Entsperren),
  14 s später öffnete der Nutzer die App. Samsung verbindet WLAN bei ausgeschaltetem
  Bildschirm oft spät → WLAN allein reicht nicht, Standort-Erkennung als zweiter Auslöser
  wichtig. Hintergrund-Standort wurde an dem Morgen nicht abgefragt (Standort-Erkennung
  vermutlich aus/kein FH-Standort). ZEUS-Paket auf dem Gerät: `de.isgus.zeusmobile`.
  USB/adb ist inzwischen möglich (Gerät `SM-S948B`).
- Prüfliste (`Reliability.kt`): neue Voraussetzungen dort ergänzen.

## Rahmenbedingungen

- Nur lokal: keine Google-Dienste, kein Netzwerk, keine Tracker, keine Konten.
- Abhängigkeiten nur Compose, Core-KTX, Activity-Compose, Coroutines.
- Oberfläche und Texte deutsch. Öffentliches Repo, GPL-3.0.
- Privater Gebrauch (`USE_EXACT_ALARM` gesetzt). Handy: **Samsung Galaxy S26** (nicht Huawei –
  der ursprüngliche Auftrag sprach fälschlich von Huawei; Huawei Health/Band gibt es trotzdem).

## Mögliche nächste Schritte (nur auf Nachfrage)

- **Schnittstelle zu Quälgeist**: z. B. „30 Min nach Auslösen noch nicht eingeloggt →
  Quälgeist nervt, bis angemeldet“. Geplant über eine eigene `signature`-Berechtigung (beide
  Apps mit demselben Schlüssel signiert) und explizite Broadcasts mit `setPackage`. Andocken
  in `Stempel.setState` (dort ist die Stelle kommentiert).
- Eigenes App-Icon statt des Uhr-Symbols.
- Feiertage/Semesterferien überspringen.
