# PiPaCraft HUD 1.0.1

Eine **eigene neue Mod**, unabhängig von SkyZVerify und SkyZGallery. Für Minecraft **1.12.2**, Forge **14.23.5.2859**, Java **8**. Nur auf dem Server installieren; Spieler brauchen diese zusätzliche Mod nicht.

## Installation

1. Server stoppen.
2. `PiPaCraftHUD-1.0.1.jar` in den `mods/`-Ordner des Servers legen.
3. Server starten. Die Mod erzeugt `config/pipacrafthud.json`.
4. Config bearbeiten und als OP `/pipahud reload` ausführen. In der Serverkonsole: `pipahud reload` ohne `/`.

Keine Änderung an Verify, Gallery, Discord-Bot oder bestehenden Serverdaten nötig.

## Befehle

| Befehl | Wirkung | Rechte |
|---|---|---|
| `/live` | Eigenen Live-Status umschalten | Alle Spieler |
| `/live on` / `/live off` | Live-Status gezielt setzen | Alle Spieler |
| `/live status` | Eigenen Status anzeigen | Alle Spieler |
| `/pipahud` | Eigenes Scoreboard umschalten | Alle Spieler |
| `/pipahud on` / `/pipahud off` | Eigenes Scoreboard gezielt setzen | Alle Spieler |
| `/pipahud reload` | Config validieren und neu laden | OP-Level 2 oder Serverkonsole |

Live ist eine manuelle Markierung: Es gibt keine Twitch-Abfrage. Beim Verlassen des Servers und bei einem Neustart wird der Status zurückgesetzt. Auch die persönliche Scoreboard-Auswahl gilt für die aktuelle Sitzung. Nach dem Tod und einem Dimensionswechsel bleibt der Live-Status erhalten.

## Anzeige

- Pink, Lila und Weiß; animierter Scoreboard-Titel und Tablist-Kopf/Fuß.
- Live-Prefix vor der bisherigen Chatnachricht sowie im Tab-Namen.
- Live-Prefix über dem Spieler, sofern er nicht bereits einem echten Server-Team angehört.
- Standard-Scoreboard: Name, Spielerzahl, Live-Status, gesamte Spielzeit, persönliche Tode, Server-Tode, Ping und geschätzte TPS. Ohne Economy.
- Nur veränderte Texte werden gesendet; Scoreboard-Zeilen werden über stabile Einträge aktualisiert.
- Keine eigenen Blöcke, Items, Client-Klassen oder zusätzlichen Netzwerkkanäle.

## Update von 1.0.0

Die alte JAR durch `PiPaCraftHUD-1.0.1.jar` ersetzen und den Server neu starten. Beim ersten Laden einer alten Config wird `"&7Server Tode: &f{server_deaths}"` direkt nach der persönlichen Todeszeile ergänzt. Farben und Position können danach in `scoreboardLines` frei geändert werden. Die vorhandenen Einstellungen und unbekannte Felder werden erhalten. Vor der einmaligen Migration wird `pipacrafthud.json.v1.bak` angelegt; `configVersion: 2` verhindert, dass eine bewusst entfernte Zeile beim nächsten Neustart wieder ergänzt wird. Hat eine angepasste Config bereits 15 Zeilen, wird keine Zeile verdrängt: eine ungenutzte Zeile entfernen und den Platzhalter selbst eintragen.

### Wie Server-Tode gezählt werden

Die Mod liest beim Start die Minecraft-Statistikdateien im `stats/`-Ordner der primären Serverwelt. Sie summiert `stat.deaths` pro Spieler-UUID. Für verbundene Spieler ersetzt der aktuelle Wert im Speicher den gespeicherten Dateiwert; derselbe Spieler wird nicht doppelt gezählt. Neue Tode erscheinen mit dem nächsten normalen HUD-Update. Offline-Werte bleiben enthalten. Die Anzeige gilt für die Serverwelt über alle Dimensionen und umfasst auch gespeicherte Tode aus der Zeit vor der Mod-Installation.

Der Gesamtstand bleibt nach Neustarts aus den Minecraft-Statistiken rekonstruierbar. Gelöschte oder zurückgesetzte Statistiken senken den Wert nach Neustart bzw. `/pipahud reload`. Defekte Statistikdateien werden mit einer Warnung im Serverlog übersprungen. Gibt es keine gespeicherten Todesstatistiken, beginnt der Zähler bei 0. Es wird keine zusätzliche Statistik in die Welt geschrieben; vorhandene Minecraft-Stats werden nicht verändert.

## Config

JSON mit UTF-8 speichern. Keine Kommentare oder zusätzlichen Kommas im JSON. Mit `/pipahud reload` wird erst geprüft; fehlerhafte Änderungen ersetzen die laufende Konfiguration nicht. Bei einer ungültigen Datei beim Serverstart wird die Datei erhalten und die Mod verwendet Defaults; im Serverlog steht der Fehler.

| Einstellung | Bedeutung |
|---|---|
| `configVersion` | Stand der Config-Migration; nicht manuell zurücksetzen |
| `serverName` | Für `{server}` |
| `timezone` | Zeitzone für `{time}` und `{date}`; Standard `Europe/Berlin` |
| `updateTicks` | Aktualisierung; 10 Ticks sind bei 20 TPS 0,5 Sekunden, erlaubt 5–1200 |
| `animationTicks` | Dauer eines Frames in Serverticks; erlaubt 5–1200 |
| `scoreboardEnabled`, `tabEnabled` | Anzeigen global aktivieren/deaktivieren |
| `nameTagsEnabled`, `chatPrefixEnabled` | Live-Prefix über Spielern/im Chat |
| `liveEnabled` | `/live` aktivieren/deaktivieren |
| `liveAnnouncements` | Serverweite Nachricht bei Statuswechsel |
| `liveCommandCooldownSeconds` | Schutz gegen häufiges Umschalten, 0–3600 Sekunden |
| `protectExistingSidebar` | Bestehende echte Server-Scoreboards bevorzugen; Standard `true` |
| `livePrefix`, `tabPlayerFormat` | Live-Prefix und Spielerformat im Tab |
| `liveOnLabel`, `liveOffLabel` | Texte für `{live}` |
| `titleFrames`, `headerFrames`, `footerFrames` | Animationsframes, zyklisch abgespielt |
| `scoreboardLines` | Reihenfolge und Inhalt der Zeilen, maximal 15 |
| `animations` | Weitere benannte Frame-Listen, z.B. `{anim:heart}` |
| `dimensionNames` | Anzeigenamen je Dimensions-ID |
| Alle Felder mit `Message` / `livePersonalOn` / `livePersonalOff` | Spieler- und Statusmeldungen |

Farbcodes: `&d` Pink, `&5` Lila, `&f` Weiß, `&7` Grau. Formate: `&l` fett, `&m` durchgestrichen, `&r` zurücksetzen. Im Tab-Kopf/Fuß erzeugt `\n` im JSON einen Zeilenumbruch. Für Leerzeilen im Scoreboard z.B. `"&r"` verwenden; auch mehrere gleiche Leerzeilen funktionieren.

### Platzhalter

| Platzhalter | Inhalt |
|---|---|
| `{player}`, `{server}` | Spielername / Servername |
| `{online}`, `{max_players}` | Spielerzahl / Slotlimit |
| `{live}`, `{live_prefix}`, `{live_count}` | Persönlicher Live-Status / Prefix / Anzahl live markierter Spieler |
| `{ping}` | Ping in Millisekunden |
| `{tps}`, `{mspt}` | Aus mittlerer Tickdauer geschätzte TPS / Tickdauer in ms |
| `{playtime}`, `{deaths}` | Minecraft-Statistiken der gesamten Spielzeit / persönlichen Tode |
| `{server_deaths}` | Summe der Tode aller bekannten Spieler dieser Serverwelt, einschließlich offline befindlicher Spieler |
| `{health}`, `{food}`, `{level}` | Lebenspunkte / Hungerpunkte / XP-Level |
| `{world}`, `{dimension}` | Konfigurierter Weltname / Dimensions-ID |
| `{x}`, `{y}`, `{z}` | Blockkoordinaten; standardmäßig nicht eingeblendet |
| `{time}`, `{date}` | Uhrzeit / Datum in eingestellter Zeitzone |
| `{anim:heart}` | Aktueller Frame der benannten Animation `heart` |

`{health}` zählt Lebenspunkte (20 sind zehn Herzen), `{food}` Hungerpunkte (0–20). `{tps}` ist eine Schätzung aus Tick-Rechenzeiten und keine unabhängige Messung der Tickrate bei äußeren Pausen. Stats sind die vorhandenen Minecraft-Stats, keine neu gestartete Mod-Zählung.

### Grenzen und Zusammenarbeit mit anderen Mods

Minecraft 1.12.2 hat einen Scoreboard-Titel mit höchstens 32 Zeichen und Zeilen-Teams mit je 16 Zeichen Prefix/Suffix. Farbcodes zählen mit. Zu lange Texte werden sicher gekürzt; deshalb kurze Beschriftungen verwenden. Die Zahlen am rechten Rand des Vanilla-Scoreboards lassen sich durch eine reine Servermod in dieser Version nicht ausblenden.

Echte Server-Teams werden nicht verändert. Gehört ein Spieler bereits einem Team an, wird der zusätzliche Live-Nametag ausgelassen; Chat und Tab-Markierung funktionieren weiterhin. Das schützt vorhandene Team-/PvP-/Nametag-Einstellungen. Ein echtes aktives Sidebar-Scoreboard hat standardmäßig Vorrang. Andere Mods, die ebenfalls Tab-Namen/Kopf/Fuß oder eigene Scoreboard-Pakete senden, können diese Anzeige überschreiben; in diesem Fall das entsprechende Modul in einer der Mods deaktivieren. Die Mod ist kein allgemeiner Adapter für beliebige weitere HUD-Mods.

## Selbst bauen

Java 8 installieren, anschließend im Projekt:

```sh
./gradlew --no-daemon build
```

Unter Windows `gradlew.bat --no-daemon build`. Ergebnis: `build/libs/PiPaCraftHUD-1.0.1.jar`. Die ForgeGradle-Reobfuskierung muss erfolgreich laufen: Eine reine `javac`-JAR ist kein installierbares Release.

## Prüfung auf deinem Testserver

1. Als normaler Spieler ohne OP `/live`, `/live status` und `/pipahud off` testen.
2. Mit einer zweiten Person Chat, Tab und Nametag prüfen.
3. Sterben, respawnen und Dimension wechseln; Live soll erhalten bleiben.
4. Verlassen und erneut verbinden; Live soll deaktiviert sein.
5. Als Nicht-OP `/pipahud reload` versuchen; es muss abgelehnt werden.
6. Als OP einen Config-Text ändern und neu laden. Eine ungültige Config muss abgelehnt werden.
7. Vorhandene Team-/HUD-Mods prüfen. Bei einem bestehenden Server-Team darf kein neuer Live-Nametag dieses Team ersetzen.

Der Test auf deinem konkreten Modpack ist zusätzlich zum Build notwendig; ein erfolgreicher Build ersetzt diesen Ingame-Test nicht.
