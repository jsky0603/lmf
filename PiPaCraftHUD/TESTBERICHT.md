# PiPaCraft HUD 1.0.1 – Prüfbericht

- Vollständiger ForgeGradle-Build für Forge 1.12.2 / 14.23.5.2859 erfolgreich, inklusive `reobfJar`.
- 44 Prüfungen bestanden: 22 Text-/Config-Prüfungen und 22 Prüfungen für Server-Tode und die Config-Migration.
- Server-Tode: gespeicherte Offline-Stats, aktuelle Online-Werte ohne Doppelzählung, Resets, fehlende/defekte Dateien, Rekonstruktion nach Neustart und Summen über dem 32-Bit-Bereich geprüft.
- Config: angepasste Texte und unbekannte Felder bleiben erhalten; Backup, einmalige Zeilenergänzung, absichtliches Entfernen, volle 15-Zeilen-Layouts und ungültige Config geprüft.
- Release-JAR: 8 Java-8-Klassen (Class-Version 52), Version 1.0.1, Server-Metadaten, SRG-Befehlsmethoden, ServerDeathStats enthalten, keine Client-Klassenreferenzen und keine Testklassen.
- Java 8, Gradle 5.6.4, MCP 39-1.12, ForgeGradle 3.0.197.

Noch nicht geprüft: Darstellung bei echten verbundenen Spielern und Zusammenarbeit mit weiteren HUD-Mods auf Skys konkretem Modpack.
