# FH Stempelkarte

(Repo und Paketname ohne Leerzeichen: `FH-Stempelkarte`, `de.fhstempelkarte`.)

Android-App, die ans Einstempeln in **ZEUS** an der FH erinnert – über das Hintergrundbild.
Morgens im Zeitfenster (Standard Mo–Fr 7:00–9:00) erkennt die App, dass du an der FH bist
(FH-WLAN oder Geofence), und setzt den Hintergrund auf **„Nicht eingeloggt“** (rot) plus
eine Meldung. Nach dem Anmelden wird er **„Eingeloggt“** (grün), nach dem Abmelden kommt
dein eigenes Bild zurück. Alles lokal: keine Google-Dienste, kein Netzwerk, keine Konten.

## Funktionen

- **Erkennung nur im Zeitfenster** (Beginn, Ende, Wochentage einstellbar). Zwei Auslöser,
  der erste gewinnt:
  - **WLAN**: SSID(s) einstellbar, z. B. `eduroam`, mit „Aktuelles WLAN übernehmen“.
  - **Standort**: FH-Position per „Aktuellen Standort übernehmen“, Radius (Standard 200 m).
    Ohne Google über den Android-eigenen Proximity-Alert plus Standort-Updates alle 2 Min.
- Ausgelöst wird höchstens **einmal am Tag** und nur aus dem Zustand *Neutral*.
- **ZEUS-Knopf**: Widget (Farbe zeigt den Zustand) und App-Verknüpfung. Ein Tipp öffnet
  *Anmelden* / *Abmelden* / *Abbrechen*, danach startet ZEUS. Dieselben Knöpfe (ohne ZEUS)
  stehen auf dem Hauptbildschirm.
- **Keine Erinnerung zum Abmelden**: Der grüne Hintergrund ist die Erinnerung. *Eingeloggt*
  bleibt über Nacht, bis du abmeldest; solange wird morgens nichts ausgelöst.
- **Hintergründe**: rot/grün mit großem Text, Farbe wählbar oder eigenes Bild.
  Start- und/oder Sperrbildschirm. Dein **Originalbild wählst du einmal** in der App aus
  (Android lässt Apps den aktuellen Hintergrund nicht zuverlässig auslesen).
- **Prüfliste** mit Links in die Systemeinstellungen und *Test auslösen*.

Hinweis: Für den pünktlichen Start nutzt die App einen Wecker-Alarm. Deshalb zeigt Android
für die Startzeit des Fensters (7:00) ein Wecker-Symbol bzw. „nächster Wecker“ an.

## Einrichten

1. Prüfliste durchgehen: Benachrichtigungen, exakte Alarme, Standort „genau“ und
   **„Immer erlauben“**, Akku-Optimierung aus, auf Huawei App-Start „manuell verwalten“.
2. ZEUS-App wählen (Liste aller Apps, Kandidaten mit „zeus“/„isgus“ stehen oben).
3. Originalhintergrund wählen.
4. An der FH: *Aktuelles WLAN übernehmen* und/oder *Aktuellen Standort übernehmen*.
5. Widget anlegen: Startbildschirm lange drücken → Widgets → FH Stempelkarte.

## Installieren und aktualisieren

Voraussetzung: Android 8.0 oder neuer.

- **Mit automatischen Updates über [Obtainium](https://obtainium.imranr.dev/):** App
  hinzufügen mit der URL `https://github.com/Blaintino/FH-Stempelkarte`, unter „Filter release
  titles by regular expression“ `^fhstempelkarte-v` eintragen.
- **Direkt:** neueste `FH-Stempelkarte-….apk` unter
  [Releases](https://github.com/Blaintino/FH-Stempelkarte/releases/latest).

## Lokal bauen

JDK 21 + Android SDK (`ANDROID_HOME` oder `local.properties`), dann:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release-Signatur nur über Umgebungsvariablen (`ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`); im CI aus den
gleichnamigen Secrets (`ANDROID_KEYSTORE_BASE64` statt Pfad). Derselbe Schlüssel wie
[Quälgeist](https://github.com/Blaintino/Quaelgeist).

## Dateien

| Datei | Inhalt |
|---|---|
| `TimeWindow.kt` | Zeitfenster-Rechnung (rein, getestet) |
| `StateLogic.kt` | Zustände und Auslöse-Regel (rein, getestet) |
| `Ssid.kt` | SSID-Vergleich (rein, getestet) |
| `WallpaperLayout.kt` | Textanordnung der erzeugten Bilder (rein, getestet) |
| `Stempel.kt` | `setState` – einziger Ort für Zustandswechsel |
| `Scheduler.kt` | Alarme für Fensterbeginn/-ende, startet/stoppt Erkennung |
| `Detector.kt` | Geofence, Standort-Updates, WLAN-Callback, SSID lesen |
| `Receivers.kt` | Fenster-, Erkennungs- und Boot-Receiver |
| `Wallpapers.kt` | Hintergrund setzen, Bilder erzeugen/importieren |
| `Notifier.kt` | Meldung „Einstempeln nicht vergessen“ |
| `Zeus.kt` | ZEUS starten, Widget, Verknüpfung, Dialog-Activity |
| `Store.kt` | SharedPreferences (Einstellungen + Zustand) |
| `Ui.kt`, `Reliability.kt` | Hauptbildschirm, Einstellungen, Prüfliste |

## Lizenz

GPL-3.0, siehe `LICENSE`.
