# FH Stempelkarte – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk 37): erinnert per Hintergrundbild
ans Einstempeln in ZEUS an der FH. Schwester-App von Quälgeist (`Blaintino/Quaelgeist`),
Werkzeuge und Muster von dort übernommen. Funktionen und Dateiübersicht in `README.md`.

## Status und Werkzeuge

Stand 2026-10-14: kompiliert, Lint ohne echte Befunde (nur Versions-Hinweise,
`InlinedApi` für die Alarm-Berechtigungs-Aktion – harmlos, wie in Quälgeist), 22 Unit-Tests
grün (`TimeWindowTest`, `StateLogicTest`, `SsidTest`, `WallpaperLayoutTest`; Zeitzone fest
`Europe/Berlin`). **Auf dem Gerät noch nicht getestet.** Der Nutzer installiert über
Obtainium, nicht per USB/adb. Offen zu prüfen: Widget → Dialog → ZEUS startet und Hintergrund
wechselt; WLAN- und Geofence-Auslösung im (vorgezogenen) Zeitfenster; nach Neustart neu
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
- **Alle Zustandswechsel nur über `Stempel.setState`** (Meldung, Widget, Erkennung neu
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
- SSID: zuerst `NetworkCapabilities.transportInfo`, ab Android 12 oft geschwärzt, dann
  `WifiManager.connectionInfo` (braucht Standort-Recht, im Hintergrund „Immer erlauben“).
- Meldung: Kanal `einstempeln`, Kategorie `REMINDER`, nicht `ongoing`, `cancel` + `notify`
  (Huawei Health), kein Vollbild-Intent. Knopf *ZEUS öffnen* = derselbe Dialog wie das Widget.
- Originalhintergrund wird nie vom System gelesen, sondern per Photo Picker gewählt und nach
  `files/original` kopiert. Ohne Original beim Abmelden **immer erst fragen**, bevor
  `WallpaperManager.clear()` läuft (`LogoutNoOriginalDialog`).
- Widget mit klassischen `RemoteViews` (kein Glance). Launcher-Verknüpfung dynamisch
  (`ShortcutManagerCompat`), weil statische `shortcuts.xml` die Debug-App-ID nicht kennt.
- Prüfliste (`Reliability.kt`): neue Voraussetzungen dort ergänzen.

## Rahmenbedingungen

- Nur lokal: keine Google-Dienste, kein Netzwerk, keine Tracker, keine Konten.
- Abhängigkeiten nur Compose, Core-KTX, Activity-Compose, Coroutines.
- Oberfläche und Texte deutsch. Öffentliches Repo, GPL-3.0.
- Privater Gebrauch (`USE_EXACT_ALARM` gesetzt). Handy: Huawei.

## Mögliche nächste Schritte (nur auf Nachfrage)

- **Schnittstelle zu Quälgeist**: z. B. „30 Min nach Auslösen noch nicht eingeloggt →
  Quälgeist nervt, bis angemeldet“. Geplant über eine eigene `signature`-Berechtigung (beide
  Apps mit demselben Schlüssel signiert) und explizite Broadcasts mit `setPackage`. Andocken
  in `Stempel.setState` (dort ist die Stelle kommentiert).
- Eigenes App-Icon statt des Uhr-Symbols.
- Feiertage/Semesterferien überspringen.
