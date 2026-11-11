# TODO – FH Stempelkarte

Stand 2026-11-11. Erledigtes abgehakt, offene Punkte nummeriert. Entscheidungen des Nutzers
stehen jeweils dabei; Hintergründe und Details in `CLAUDE.md`.

## Erledigt

- [x] 1. App aus dem Auftrag gebaut (Repo, CI, signiertes Release, Obtainium) – 2026-10-14,
  erstes Release `fhstempelkarte-v2`. Gleicher Keystore wie Quälgeist.
- [x] 2. Ablauf ohne Widget: App öffnen oder Meldung antippen → Auswahl Einloggen/Ausloggen →
  ZEUS (Entscheidung 2026-10-14).
- [x] 3. Alle drei Hintergründe in der App einstellbar (Normal, Einloggen-Erinnerung,
  Eingeloggt), mit Vorschau (2026-10-14).
- [x] 4. Geofence und WLAN-Erkennung getrennt ein-/ausschaltbar, FH-Standort löschbar (2026-10-14).
- [x] 5. Erster Gerätetest „Meldung kam erst beim Öffnen“ untersucht (2026-10-16, per adb):
  kein App-Fehler – Standort-Erkennung war aus, Samsung verband eduroam erst beim Entsperren.
  Dazu gebaut: Protokoll in der Prüfliste, 5-Min-Prüfung per Alarm im Zeitfenster,
  Prüfliste auf Samsung umgestellt (vorher fälschlich Huawei).
- [x] 6. Start- und Sperrbildschirm getrennte Hintergründe, Oberfläche untereinander statt
  Auswahl (2026-10-16).
- [x] 7. Widget wieder eingebaut, mit direkten Knöpfen Einloggen/Ausloggen (Entscheidung
  2026-10-19, PR #1).
- [x] 8. Schnittstelle für eigene Apps (Status-Provider, Broadcast, geschützte Alias), PR #2/#4.
- [x] 9. Arbeitszeit pro Tag für andere Apps (PR #3), Pausen nach § 4 ArbZG mit 9 h statt 10 h
  (Entscheidung 2026-11-04, PR #5) – Release `fhstempelkarte-v23`.

## Offen

- [ ] 10. **Gerätetest der Hintergrund-Erkennung bestätigen**: nach dem Wiedereinschalten der
  Standort-Erkennung sollte am Montag 2026-10-19 getestet werden; Ergebnis nicht dokumentiert.
  Den Nutzer nach dem Protokoll (Prüfliste → Protokoll → Kopieren) fragen.
- [ ] 11. **Schnittstelle auf dem Gerät testen** (Provider, Broadcast, Alias) – bisher nur
  Unit-Tests.
- [ ] 12. **Chip-Widget anbinden** – Arbeit im Repo `Blaintino/Chip`, Skizze in `INTEROP.md`;
  Gestaltung dort noch offen.
- [ ] 13. Klären: Soll die Stempelkarte die Arbeitszeit/Pausen selbst anzeigen? (bisher nur
  über den Provider für Chip)
- [ ] 14. Klären: „Nie schlafende Apps“ auf dem Samsung eingerichtet? (Empfehlung an den Nutzer)
- [ ] 15. Klären: erledigte Remote-Branches `claude/*` löschen? (nur nach Rückfrage)

## Ideen (nur auf Nachfrage)

- [ ] 16. Quälgeist anbinden: „30 Min nach Auslösen noch nicht eingeloggt → Quälgeist nervt“.
  Der Broadcast geht schon an `de.quaelgeist`/`.beta`, dort fehlen Berechtigung + Empfänger.
- [ ] 17. Eigenes App-Icon statt des Uhr-Symbols.
- [ ] 18. Feiertage/Semesterferien überspringen.
- [ ] 19. Abhängigkeiten aktualisieren (Lint meldet Gradle 9.8.1, Compose-Plugin 2.4.21,
  Coroutines 1.11.0) – mit Quälgeist abstimmen, dort gleiche Versionen.
