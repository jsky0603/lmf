# PiPaCraft HUD 1.0.2 – Prüfbericht

- Vollständiger ForgeGradle-Build für Forge 1.12.2 / 14.23.5.2859 erfolgreich, inklusive `reobfJar`.
- 113 Prüfungen bestanden: 22 Text-/Config-Prüfungen, 22 Server-Tode-/Migrationsprüfungen, 55 Ranglisten-/Seiten-/Config-Prüfungen und 14 Prüfungen des tatsächlichen Befehls und der Chatbuttons.
- Server-Tode: gespeicherte Offline-Stats, aktuelle Online-Werte ohne Doppelzählung, Resets, fehlende/defekte Dateien, Rekonstruktion nach Neustart und Summen über dem 32-Bit-Bereich geprüft.
- Rangliste: absteigende Todeszahlen, stabile Reihenfolge bei Gleichstand, gemeinsame Plätze über Seitengrenzen, maximal zehn Spieler je Seite, Restseite, leere Liste, ungültige Seiten, null Tode, Online-/Offline-Spieler, UUID-Ersatzname und unveränderliche Momentaufnahme geprüft.
- Befehl: `/kills`, Alias `/pipakills`, Permission-Level 0 und ausdrückliche Freigabe für einen Sender ohne OP-Rechte geprüft. Deaktivierung, Nutzungsmeldung, vanilla RUN_COMMAND-Klickaktion mit korrekter Zielseite und Tooltip geprüft.
- Config: angepasste Texte und unbekannte Felder bleiben erhalten; Backups v1/v2, einmalige Migration auf Version 3 und neuer kills-Abschnitt, absichtliches Entfernen, volle 15-Zeilen-Layouts, eigene Ranglistentexte und ungültige Config geprüft.
- Release-JAR: 13 Java-8-Klassen (Class-Version 52), Version 1.0.2, Server-Metadaten, SRG-Befehlsmethoden, DeathLeaderboard/KillsConfig enthalten, keine Client-Klassenreferenzen und keine Testklassen.
- Java 8, Gradle 5.6.4, MCP 39-1.12, ForgeGradle 3.0.197.

Noch nicht geprüft: Darstellung bei echten verbundenen Spielern und Zusammenarbeit mit weiteren HUD-Mods auf Skys konkretem Modpack.
