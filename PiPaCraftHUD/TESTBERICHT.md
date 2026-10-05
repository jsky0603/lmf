# PiPaCraft HUD 1.0.0 – Prüfbericht

- Vollständiger ForgeGradle-Build für Forge 1.12.2 / 14.23.5.2859 erfolgreich, inklusive `reobfJar`.
- 22 Text- und Config-Prüfungen bestanden: Farbcodes, Abschneiden an Format-/Unicode-Grenzen, Formaterhalt, Frame-Auswahl, Spielzeit, Platzhalter und ungültige Konfigurationen.
- Release-JAR: 7 Java-8-Klassen (Class-Version 52), korrekte Mod-Metadaten, SRG-Befehlsmethoden und keine Client-Klassenreferenzen. Testklassen nicht im Release enthalten.
- Kompiliert mit Java 8 und Gradle 5.6.4, stabile MCP-Mappings 39-1.12, ForgeGradle 3.0.197.

Noch nicht geprüft: echte Darstellung bei verbundenen Spielern, Laufzeit auf Skys Modpack und Zusammenarbeit mit weiteren Tab-/Nametag-/Scoreboard-Mods. Die Ingame-Prüfschritte stehen in README-DE.md.
