# Schnittstelle für andere eigene Apps

Stand: Version 1 (`InteropContract.VERSION`). Gedacht zuerst für das Widget der Kalender-App
[Chip](https://github.com/Blaintino/Chip) (Stempelkarte im Chip-Widget anzeigen und bedienen),
später für [Quälgeist](https://github.com/Blaintino/Quaelgeist). Wie das im Chip-Widget
aussieht, ist noch offen – die Schnittstelle legt nur fest, **was** Chip lesen und auslösen kann.

Alle Namen stehen in `app/src/main/java/de/fhstempelkarte/InteropContract.kt` (reines Kotlin,
darf in andere Apps kopiert werden). Namen nie still ändern, nur ergänzen; Unverträgliches
erhöht die Version.

## Zugriffsschutz

Berechtigung `de.fhstempelkarte.permission.INTEROP`, Schutzstufe `signature`: nur Apps mit
**demselben Signaturschlüssel** (Chip, Quälgeist und Stempelkarte nutzen denselben Keystore).
Die nutzende App trägt ein:

```xml
<uses-permission android:name="de.fhstempelkarte.permission.INTEROP" />
<queries>
    <package android:name="de.fhstempelkarte" />
</queries>
```

- Debug-Builds sind anders signiert und bekommen die Berechtigung nicht (die Debug-Stempelkarte
  hat eine eigene: `de.fhstempelkarte.debug.permission.INTEROP`).
- **Reihenfolge**: Android erteilt eine `signature`-Berechtigung verlässlich, wenn die
  definierende App (Stempelkarte) schon installiert ist, wenn die nutzende App installiert
  oder aktualisiert wird. Also erst die Stempelkarte mit dieser Schnittstelle aktualisieren,
  danach Chip. Ohne Berechtigung gibt es eine `SecurityException` – Chip soll dann
  „Stempelkarte: keine Berechtigung“ anzeigen statt abzustürzen.

## 1. Status lesen (ContentProvider)

`content://de.fhstempelkarte.status/status` – immer genau eine Zeile, nur lesbar.

| Spalte | Typ | Inhalt |
|---|---|---|
| `schnittstelle` | Int | Version (1) |
| `zustand` | String | `NEUTRAL`, `NICHT_EINGELOGGT`, `EINGELOGGT` |
| `bezeichnung` | String | Anzeigetext, z. B. „Nicht eingeloggt“ |
| `seit` | Long | letzter Zustandswechsel (ms seit 1970), 0 = unbekannt |
| `ausgeloest` | String? | zuletzt ausgelöst, z. B. „Mo 03.11. 07:42 · WLAN eduroam“ |
| `erkennung_bis` | Long | Ende der laufenden Erkennung (ms), 0 = keine |
| `farbe` | Int? | Farbe des Stempelkarten-Widgets (ARGB): Rot/Grün; null bei `NEUTRAL` (eigene Farbe nehmen) |
| `arbeit_heute_ms` | Long | abgeschlossene Arbeitszeit heute (Summe aller Sitzungen in `EINGELOGGT`, ms) |
| `arbeit_laeuft_seit` | Long | Beginn der laufenden Sitzung (ms, auf heute 0:00 begrenzt), 0 = keine |

Arbeitszeit jetzt = `arbeit_heute_ms` + (jetzt − `arbeit_laeuft_seit`), falls `arbeit_laeuft_seit` > 0.
So kann ein Widget sie mit einem `Chronometer` weiterzählen lassen, ohne jede Minute neu zu zeichnen.
Eine Sitzung über Mitternacht zählt ab 0:00 zum neuen Tag; der Leser sollte kurz nach Mitternacht
neu lesen. Bei Eingeloggt aus der Zeit vor dieser Spalte (Beginn unbekannt) ist sie 0.

Spalten immer per Name lesen (`getColumnIndex`), nicht per Position.

Der Provider meldet Änderungen per `notifyChange` – darauf kann Chip z. B. wie bei der
Kalenderdatenbank einen WorkManager-Content-URI-Auslöser setzen. Für das Widget ist der
Broadcast unten aber direkter.

## 2. Änderungen mitbekommen (Broadcast)

Bei jedem Zustandswechsel (`Stempel.setState`) und nach Boot/App-Update sendet die
Stempelkarte einen **expliziten** Broadcast (`setPackage`) an `de.blaintino.chip`,
`de.quaelgeist` und `de.quaelgeist.beta`, nur an Empfänger mit der Berechtigung:

- Aktion `de.fhstempelkarte.action.ZUSTAND_GEAENDERT`
- Extras `zustand` (String) und `seit` (Long) – zur Sicherheit trotzdem den Provider lesen.

Weitere Empfänger-Apps müssen in `InteropContract.CONSUMERS` **und** im Manifest der
Stempelkarte unter `<queries>` ergänzt werden.

## 3. Stempeln auslösen (Activity)

Exportierte Alias `de.fhstempelkarte/.ExternZeus` (ebenfalls nur mit der Berechtigung), danach
öffnet sich jeweils ZEUS – genau wie die Knöpfe des Stempelkarten-Widgets:

| Aktion | Wirkung |
|---|---|
| `de.fhstempelkarte.action.EINLOGGEN` | sofort `EINGELOGGT`, ZEUS öffnen |
| `de.fhstempelkarte.action.AUSLOGGEN` | sofort `NEUTRAL` (ohne Originalbild mit Rückfrage), ZEUS öffnen |
| `de.fhstempelkarte.action.AUSWAHL` | normale Auswahl Einloggen/Ausloggen/Abbrechen |

Extras werden nicht ausgewertet.

## Skizze für Chip (noch nicht umgesetzt)

Analog zum Sprungbrett `QuaelgeistActivity` und zu `WidgetCache`:

```kotlin
// Lesen (im Widget innerhalb der Komposition laden, Ergebnis cachen wie WidgetCache)
fun readStempel(ctx: Context): StempelStatus? = try {
    ctx.contentResolver.query(Uri.parse("content://de.fhstempelkarte.status/status"), null, null, null, null)
        ?.use { c ->
            if (!c.moveToFirst()) null else StempelStatus(
                state = c.getString(c.getColumnIndexOrThrow("zustand")),
                label = c.getString(c.getColumnIndexOrThrow("bezeichnung")),
                color = c.getColumnIndex("farbe").takeIf { it >= 0 && !c.isNull(it) }?.let(c::getInt),
            )
        }
} catch (_: SecurityException) { null }   // keine Berechtigung (Debug-Build, falsche Reihenfolge)
  catch (_: IllegalArgumentException) { null } // Stempelkarte nicht installiert / zu alt

// Knopf im Widget: PendingIntent auf eine eigene Sprungbrett-Activity, die startet:
Intent("de.fhstempelkarte.action.EINLOGGEN").setPackage("de.fhstempelkarte")

// Manifest: Empfänger (exported, android:permission="de.fhstempelkarte.permission.INTEROP")
// für de.fhstempelkarte.action.ZUSTAND_GEAENDERT → TodayWidget.refresh(ctx)
```

Ist die Stempelkarte nicht installiert, liefert `query` `null` – dann den Stempelkarten-Teil im
Chip-Widget einfach weglassen.
