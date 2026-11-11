# FH Stempelkarte – Hinweise für Claude Code

## Was die App ist

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk/compileSdk 37 = Android 17): erinnert
per **Hintergrundbild** ans Einstempeln in **ZEUS** (Zeiterfassung der FH, App
`de.isgus.zeusmobile`). Morgens im Zeitfenster (Standard Mo–Fr 7:00–9:00) erkennt sie über
FH-WLAN und/oder Standort, dass der Nutzer an der FH ist, setzt den Hintergrund „Nicht
eingeloggt“ und zeigt eine Meldung. Nach dem Einloggen wird der Hintergrund „Eingeloggt“, nach dem Ausloggen kommt das eigene Bild
zurück. Schwester-App von Quälgeist (`Blaintino/Quaelgeist`), Werkzeuge und Muster von dort.
Funktionen für Nutzer und Dateiübersicht: `README.md`; Schnittstelle für andere Apps: `INTEROP.md`;
offene Punkte: `TODO.md`.
Herkunft und ursprüngliche Absprachen (Chat vom 2026-10-14): `docs/auftrag-2026-10-14.md`.

| | |
|---|---|
| Repo | `Blaintino/FH-Stempelkarte` (öffentlich, GPL-3.0) |
| App-ID | `de.fhstempelkarte`, Debug `de.fhstempelkarte.debug` (läuft daneben, Name „FH Stempelkarte (Debug)“) |
| Release-Tag/-Titel | `fhstempelkarte-v<run_number>`, APK `FH-Stempelkarte-<run_number>.apk` |
| Installation | Obtainium mit URL `https://github.com/Blaintino/FH-Stempelkarte`, Filter `^fhstempelkarte-v`; alternativ USB/adb (seit 2026-10-16 möglich) |
| Gerät | Samsung Galaxy S26 Ultra (`SM-S948B`), Android 17. **Nicht Huawei** – der ursprüngliche Auftrag sprach fälschlich davon; nur das Fitnessband ist von Huawei (Huawei Health). |

## Aufbau des Codes

Alles in `app/src/main/java/de/fhstempelkarte/`, Tabelle in `README.md`. Kernpfade:

- **Startpunkt**: `ZeusActivity` (`Zeus.kt`, Launcher-Activity, durchsichtiger Dialog) →
  *Einloggen*/*Ausloggen* → `Stempel.setState` → ZEUS starten. `MainActivity` + `Ui.kt` =
  nur Einstellungen; `Reliability.kt` = Prüfliste mit Test und Protokoll.
- **Zustand**: `StateLogic.kt` (Zustände + Auslöse-Regel), `Store.kt` (SharedPreferences),
  `Stempel.kt` (`setState` – einziger Ort für Wechsel, `maybeTrigger`).
- **Hintergrund-Erkennung**: `Scheduler.kt` (Alarme Fensterbeginn/-ende/5-Min-Prüfung,
  `planAll`), `Detector.kt` (Geofence, Standort-Updates, WLAN-Callback, SSID), `Receivers.kt`,
  `TimeWindow.kt`, `Ssid.kt`, `Geo.kt`, `EventLog.kt` (Protokoll).
- **Hintergründe**: `Wallpapers.kt` (setzen, erzeugen, importieren), `WallpaperPlan.kt`
  (Start/Sperre), `WallpaperLayout.kt` (Text auf erzeugten Bildern).
- **Sonstiges**: `Notifier.kt` (Meldung), `Widget.kt` (Startbildschirm-Widget),
  `WorkTime.kt` (Arbeitszeit/Pausen), `InteropContract.kt` + `Interop.kt` (Schnittstelle),
  `AppScope.kt`.
- Reine Logik (ohne Android) liegt in eigenen Dateien mit Unit-Tests in
  `app/src/test/java/de/fhstempelkarte/` (44 Tests, Zeitzone fest `Europe/Berlin`).
  Neue Logik möglichst genauso: rein + getestet, Android-Teil dünn.

## Bauen und testen

- Toolchain wie Quälgeist: Gradle 9.8, AGP 9.4 mit eingebautem Kotlin (kein
  `org.jetbrains.kotlin.android`, nur `org.jetbrains.kotlin.plugin.compose`), Versionen in
  `gradle/libs.versions.toml`, JDK 21. Abhängigkeiten nur Compose, Core-KTX,
  Activity-Compose, Coroutines.
- Befehl: `./gradlew testDebugUnitTest lintDebug assembleDebug`. Lint hat nur bekannte
  Warnungen (Versions-Hinweise, `InlinedApi` in `Receivers.kt`, `UseKtx` in `Wallpapers.kt`) –
  keine Fehler einführen.
- Auf dem Mac des Nutzers: JDK = Android Studios JBR
  (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`), SDK `~/Library/Android/sdk`.
  Die Variablen stehen in `~/.zshrc`, werden von nicht-interaktiven Shells (Agent-Bash) aber
  **nicht** geladen – `JAVA_HOME`/`ANDROID_HOME`/`PATH` (für `adb`) im Befehl selbst setzen.
- Gerät per USB: `adb devices`; Debug-App mit `./gradlew installDebug`. Ein signiertes
  Release lässt sich per `gh release download` + `adb install -r` als Update einspielen
  (gleicher Schlüssel, Daten bleiben).
- Cloud-Sessions haben weder Gerät noch Emulator: dort nur bauen und Unit-Tests.

## Ablauf Branch → PR → Merge → Release

1. Arbeit auf einem Branch `claude/<thema>` (nicht direkt auf `main`; nur die allerersten
   Commits bis 2026-10-16 gingen direkt auf `main`).
2. Push → CI (`.github/workflows/build-apk.yml`) läuft auch auf `claude/**`: Tests, Lint,
   signierte APK als Artefakt, **kein** Release.
3. PR nach `main`, der Nutzer prüft/merged (bisher PRs #1–#5, alle gemergt).
4. Merge auf `main` → CI baut erneut und legt automatisch das Release
   `fhstempelkarte-v<run_number>` an → Obtainium findet das Update.
- Signatur: Secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD` (gesetzt). **Gleicher Keystore wie Quälgeist** (nötig für die
  `signature`-Berechtigung der Schnittstelle). Keystore liegt nur lokal beim Nutzer – nie
  einchecken, nie neu erzeugen, Passwörter nie selbst eintippen (der Nutzer setzt sie).
  Ohne Secrets baut CI eine Debug-APK mit Warnung; nie auf Debug-Signatur „umstellen“.
- `versionCode` = `VERSION_CODE` (= `github.run_number`), sonst lehnt Android Updates ab.

## Feste Entscheidungen des Nutzers (nicht ohne Rückfrage ändern)

- Nur lokal: keine Google-Dienste (kein Play Services, kein WorkManager, kein Glance), kein
  Netzwerk, keine Tracker, keine Konten. Oberfläche und Texte deutsch.
- **Keine Erinnerung zum Ausloggen/Gehen** – der grüne Hintergrund ist die Erinnerung.
- Ablauf: Meldung antippen oder App öffnen → Auswahl → ZEUS (2026-10-14). Das Widget kam
  danach auf Wunsch zurück (2026-10-19), mit direkten Knöpfen.
- Höchstens einmal am Tag auslösen, nur aus *Neutral*; Erinnerung als **eine** Meldung, keine
  Wiederholung, kein Vollbild.
- Standort- und WLAN-Erkennung einzeln schaltbar, FH-Standort löschbar (2026-10-14).
- Hintergründe: drei Zustände, Start- und Sperrbildschirm getrennt einstellbar, untereinander
  statt Auswahl (2026-10-16). Vor `WallpaperManager.clear()` immer fragen.
- ZEUS-App wird in der App gewählt, nicht fest eingetragen.
- ZEUS (ISGUS) ist geschlossen, ohne Nutzer-Schnittstelle: Die App **erkennt nicht**, ob wirklich
  gestempelt wurde, sie fragt Einloggen/Ausloggen und startet dann ZEUS. Keine Bedienungshilfe
  (Accessibility) und keine Nutzungsstatistik, um ZEUS-Starts zu erkennen (Wahl des Nutzers,
  2026-10-14). Nie versuchen, ZEUS-Daten auszulesen oder nachzubauen.
- Erkennung nur im Zeitfenster (Standard 7–9 Uhr), WLAN **und** Standort zusammen (Idee des
  Nutzers: Akku sparen, Standort genauer, WLAN als Fallback).
- Eigene App, nicht Teil von Quälgeist; Verbindung zu Quälgeist erwünscht (siehe unten).
- Pausen nach § 4 ArbZG mit 9 h (nicht 10 h) – mit dem Nutzer geklärt (2026-11-04).
- Öffentliche Namen der Schnittstelle (`InteropContract`) nie still ändern, nur ergänzen.
- Privater Gebrauch, kein Play Store: `USE_EXACT_ALARM` ist deshalb gesetzt.

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
- **Ablauf (Wunsch des Nutzers, 2026-10-14):** Meldung antippen oder App
  öffnen → `ZeusActivity` (durchsichtiger Dialog, Launcher-Activity) mit *Einloggen* /
  *Ausloggen* / *Abbrechen* / *Einstellungen* → ZEUS. `MainActivity` sind nur noch die
  Einstellungen (nicht exportiert); ohne gewählte ZEUS-App springt der Start direkt dorthin.
- **Widget wieder da (Wunsch des Nutzers, 2026-10-19)**: `StempelWidget` (`Widget.kt`,
  klassische RemoteViews, kein Glance). Hintergrund in Zustandsfarbe (feste Rot/Grün-Drawables,
  nicht die Hintergrund-Farben aus `Config`), Knöpfe *Einloggen*/*Ausloggen* starten
  die geschützte Alias `.ExternZeus` (Aktionen `EINLOGGEN`/`AUSLOGGEN`, wie Chip) → sofort
  `setState` + ZEUS, ohne Auswahl (früher Extra `EXTRA_DIRECT` am offenen Launcher-Eintrag –
  damit konnte jede App stempeln, deshalb entfernt); Ausloggen ohne
  Original fragt trotzdem (`LogoutNoOriginalDialog`). Tipp auf die Fläche = normale Auswahl.
  `Stempel.setState` und `BootReceiver` rufen `StempelWidget.updateAll`.
- Meldung: Kanal `einstempeln`, Kategorie `REMINDER`, nicht `ongoing`, `cancel` + `notify`
  (Huawei Health), kein Vollbild-Intent, keine Knöpfe – Antippen öffnet die Auswahl.
- Originalhintergrund wird nie vom System gelesen, sondern per Photo Picker gewählt und nach
  `files/original` kopiert. Ohne Original beim Abmelden **immer erst fragen**, bevor
  `WallpaperManager.clear()` läuft (`LogoutNoOriginalDialog`).
- Einstellungen → Hintergründe: drei Plätze *Normal* (`files/original`), *Einloggen-Erinnerung*
  und *Eingeloggt* (Farbe oder eigenes Bild `files/bild_<zustand>`), mit Vorschau
  (`Wallpapers.preview`). Änderung am aktiven Zustand wird sofort angewendet (Wunsch des
  Nutzers: alle drei in der App einstellbar).
- Start- und Sperrbildschirm getrennt (Wunsch des Nutzers, 2026-10-16): pro Zustand eigenes
  Bild für die Sperre (`files/original_sperre`, `files/bild_<zustand>_sperre`) bzw. eigene
  Farbe (`Config.colorOutLock`/`colorInLock`, null = wie Start). Ohne Eigenes übernimmt die
  Sperre den Startbildschirm (`WallpaperPlan.source`, `WallpaperPlanTest`); die Dateien des
  Startbildschirms behalten die alten Namen. `Wallpapers.apply`: gleiche Quelle → ein Aufruf
  mit beiden Flags, sonst erst `FLAG_SYSTEM`, dann `FLAG_LOCK`. `hasOriginal` prüft alle
  eingeschalteten Bildschirme.
  Oberfläche (Wunsch des Nutzers: eine Auswahl „Bearbeiten für“ war verwirrend): einfach
  untereinander 3× Startbildschirm, 3× Sperrbildschirm, gleiche Slots wie vorher. Die Vorschau
  zeigt, was wirklich gesetzt wird (auch die Übernahme vom Start).
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
- **Schnittstelle für eigene Apps (Wunsch des Nutzers, 2026-11-03)**: Vorbereitung, damit das
  Widget der Kalender-App Chip (`Blaintino/Chip`) perspektivisch die Stempelkarte anzeigt und
  bedient; Gestaltung dort noch offen, Chip noch nicht angepasst. Vertrag in `InteropContract.kt`
  (rein, Test `InteropContractTest`), Beschreibung für die Gegenseite in `INTEROP.md`. Drei Wege,
  alle hinter der `signature`-Berechtigung `${applicationId}.permission.INTEROP` (applicationId
  im Namen, sonst Installationskonflikt mit der anders signierten Debug-App):
  nur lesbarer `StatusProvider` (`content://de.fhstempelkarte.status/status`, eine Zeile),
  expliziter Broadcast `ZUSTAND_GEAENDERT` an `InteropContract.CONSUMERS` (aus
  `Stempel.setState` vor dem Hintergrund und aus `BootReceiver`, über `Interop.notifyChanged`),
  Activity-Alias `.ExternZeus` mit Aktionen `EINLOGGEN`/`AUSLOGGEN`/`AUSWAHL`. Direkt-Aktionen
  nimmt `ZeusActivity` nur an, wenn sie über diese Alias kam (`Interop.directFromExtern`
  prüft den Komponentennamen) – nicht über den offenen Launcher-Eintrag. Öffentliche Namen nie
  still ändern (`oeffentlicheNamenStabil`), nur ergänzen. `Store.stateSince` wird in
  `setState` beim echten Wechsel gesetzt. Auf dem Gerät noch nicht getestet.
- **Arbeitszeit pro Tag (Wunsch des Nutzers, 2026-11-03, für das Chip-Widget)**: `WorkTime.kt` (rein,
  `WorkTimeTest`), Zeit in `EINGELOGGT` aufsummiert über mehrmaliges Ein-/Ausloggen; beim Verlassen von
  `EINGELOGGT` schließt `setState` die Sitzung ab (`Store.workDay` = Tag + Summe). Nur der heutige Tag,
  Sitzung über Mitternacht zählt ab 0:00. Über den Provider als `arbeit_heute_ms` +
  `arbeit_laeuft_seit` (zusätzliche Spalten, Version bleibt 1). Die Stempelkarte zeigt sie selbst
  (noch) nicht an.
- **Pausen nach § 4 ArbZG (Wunsch des Nutzers, 2026-11-04)**: > 6 h → 30 min, > 9 h → 45 min insgesamt
  (der Nutzer meinte zuerst „bei 10 h“ – gesetzlich sind es 9 h; 10 h ist die Höchstarbeitszeit). Ausloggen
  ≥ 15 min zählt als genommene Pause (`WorkTime.open` beim Wiedereinloggen, `Day.breakMs`/`lastEnd`).
  `WorkTime.net` zieht nur die fehlende Pause ab, gleitend (Zeit steht bei 6:00/9:00, kein Sprung). Provider-
  Spalte `arbeit_pausen_ms`; das Netto rechnet der Leser (Chip) mit derselben Regel.

## Gelernte Fallen

- **Samsung verbindet WLAN bei ausgeschaltetem Bildschirm oft erst beim Entsperren** (per adb
  belegt, 2026-10-16: eduroam erst 7:23:54). WLAN allein reicht nicht, Standort-Erkennung als
  zweiten Auslöser eingeschaltet lassen. Der „Fehler“ beim ersten Test war: Standort-Erkennung
  aus, Handy noch nicht im WLAN.
- Vor Fehlersuche am Code erst das **Protokoll** (Prüfliste) bzw. `adb shell dumpsys alarm |
  grep fhstempelkarte`, `dumpsys location`, `dumpsys wifi` ansehen.
- `PendingIntent`s für Proximity-Alert, Standort-Updates und Netzwerk-Callback müssen
  `FLAG_MUTABLE` sein, sonst fehlen die System-Extras.
- SSID im Hintergrund: `NetworkCapabilities.transportInfo` ist ab Android 12 geschwärzt;
  Fallback `WifiManager.connectionInfo` braucht „Standort: Immer erlauben“.
- Launcher-Verknüpfungen über statische `shortcuts.xml` kennen die Debug-App-ID nicht – nur
  dynamisch (wurde später ganz entfernt).
- `signature`-Berechtigung muss `${applicationId}` im Namen tragen, sonst Installationskonflikt
  zwischen Release- und (anders signierter) Debug-App.
- In KDoc-Kommentaren kein `*/` im Text (z. B. Pfade mit `/*`): schließt den Kommentar
  vorzeitig (Bug in `Widget.kt`, Commit `1ccd7c8`).
- CI prüft nur, ob `ANDROID_KEYSTORE_BASE64` gesetzt ist: fehlen die übrigen Secrets, schlägt
  der Release-Build fehl (beim ersten Lauf passiert), statt auf Debug auszuweichen.
- Der ursprüngliche Auftrag nannte ein Huawei-Handy – falsch, es ist ein Samsung. Hinweise
  in Code-Kommentaren auf „Huawei Health“ betreffen nur das Band.

### Fallen in Cloud-Sessions (claude.ai/code)

- **Kein Android-SDK vorinstalliert.** Commandline-Tools von `dl.google.com` laden, dann
  `sdkmanager "platforms;android-37.0" "build-tools;37.0.0"` – das Paket heißt
  `android-37.0`, nicht `android-37`. `sdk.dir` in `local.properties` (steht in `.gitignore`).
- **Maven Central antwortet aus Cloud-Containern oft mit HTTP 429** (Retry-After viele
  Stunden). Dann nicht lokal bauen, sondern Branch `claude/<thema>` pushen und das CI-Ergebnis
  abwarten (CI läuft auf `claude/**`). Ohne lokalen Build Code besonders sorgfältig lesen.
- Neues GitHub-Repo anlegen geht aus der Cloud nicht (403) – der Nutzer bzw. ein lokaler
  Agent muss es anlegen.
- Commits aus der Cloud mit `user.name Claude` / `user.email noreply@anthropic.com`, sonst
  meldet der Stop-Hook „Unverified“. Nur auf eigenen Branches umschreiben.
- Flacher Klon: Nach `git push` kennt der Klon den Remote-Branch evtl. nicht, der Stop-Hook
  meldet dann fälschlich „unpushed“. Mit `git ls-remote origin <branch>` prüfen,
  ggf. `git update-ref refs/remotes/origin/<branch> HEAD`.
- GitHub meldet beim Push „This repository moved“: Repo heißt `Blaintino/FH-Stempelkarte`
  (Groß-/Kleinschreibung), der Push klappt trotzdem.

## Übergabe-Notizen (Stand 2026-11-11)

- **Letztes Release**: `fhstempelkarte-v23` (2026-11-04, Merge von PR #5 „Pausen nach § 4
  ArbZG“, Commit `fe3e287`). CI auf `main` grün.
- **Branches**: alle Arbeit ist in `main`. Die Remote-Branches `claude/widget`,
  `claude/interop-widget`, `claude/arbeitszeit`, `claude/interop-protokoll`, `claude/pausen`
  (gemergt) und `claude/sperre-start-getrennt` (Commits liegen direkt auf `main`) sind
  erledigt und könnten gelöscht werden – nur nach Rückfrage. Dieser Doku-Stand liegt auf
  `claude/uebergabe-doku` (PR offen, nicht gemergt).
- **Nichts halbfertig im Code** bekannt. Offen sind Gerätetests und Folge-Wünsche, siehe `TODO.md`.
- **Offene Fragen an den Nutzer**:
  1. Hat die Hintergrund-Erkennung seit dem Wiedereinschalten der Standort-Erkennung
     (Test war für Montag 2026-10-19 geplant) zuverlässig ausgelöst? Eine Rückmeldung dazu
     fehlt in den Notizen; Protokoll in der Prüfliste ansehen lassen.
  2. Ist „Nie schlafende Apps“ (Samsung-Akku) für die App eingerichtet?
  3. Soll die Stempelkarte die Arbeitszeit selbst anzeigen (bisher nur für Chip über den Provider)?
  4. Dürfen die erledigten Remote-Branches gelöscht werden?
  5. Funktioniert das Widget am Gerät, und sollen dort die eigenen Hintergrundfarben statt
     festem Rot/Grün erscheinen? (`TODO.md` Nr. 20/21)
- Keine Artifacts/Mockups zu dieser App; die Chip-/Kartei-Mockups gehören zu anderen Projekten.
- Der ursprüngliche Auftrag und die Absprachen aus dem Gründungs-Chat liegen jetzt in
  `docs/auftrag-2026-10-14.md` (vorher nur im Chat bzw. im Cloud-Container).
- Widget (PR #1, Release `fhstempelkarte-v14`) und getrennte Start-/Sperr-Hintergründe
  (`fhstempelkarte-v11`) entstanden in einer Cloud-Session ohne Gerät. Rückmeldung des
  Nutzers zu den Hintergründen: „Wirkt gut“. Zum Widget gibt es keine Rückmeldung.

## Mögliche nächste Schritte (nur auf Nachfrage)

- **Chip-Widget** an die Schnittstelle anbinden (in `Blaintino/Chip`, siehe Skizze in `INTEROP.md`).
- **Quälgeist**: z. B. „30 Min nach Auslösen noch nicht eingeloggt → Quälgeist nervt, bis
  angemeldet“. Der Broadcast `ZUSTAND_GEAENDERT` geht schon an `de.quaelgeist`/`.beta`;
  Quälgeist braucht nur noch Berechtigung + Empfänger.
- Eigenes App-Icon statt des Uhr-Symbols.
- Feiertage/Semesterferien überspringen.
