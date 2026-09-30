# SkyZVerify · Minecraft Forge 1.12.2

Serverseitige Discord-Verifizierung für Forge **14.23.5.2859**. Die JAR wird nur
im `mods`-Ordner des Servers installiert; Spieler müssen nichts installieren.
Der Bot läuft als **separater Node.js-Prozess**. Seine Zugangsdaten stehen
nicht in der Mod-JAR.

## Vor der Installation

- Der Minecraft-Server braucht `online-mode=true`, damit UUIDs nicht gefälscht
  werden können. Sichere die Welt und den `config`-Ordner wie üblich.
- Erstelle im Discord Developer Portal einen Bot und aktiviere **Server Members
  Intent**. Lade ihn mit den Rechten *Kanäle verwalten*, *Rollen verwalten*,
  *Kanäle ansehen*, *Nachrichten senden* und *Nachrichtenverlauf lesen* in
  deinen Discord ein. Der Bot benötigt keinen Message Content Intent.
- Der Bot braucht **Node.js 24.17.0 oder neuer** und Netzwerkzugriff auf
  Discord. Der Minecraft-Server braucht Netzwerkzugriff auf den Bot-Bridge-Port.

## Einrichtung

1. Lege `SkyZVerify-1.1.0.jar` in den `mods`-Ordner des **Servers** und starte
   ihn einmal. Unter `config/skyzverify.properties` entstehen `bridge.url`,
   ein zufälliges `bridge.secret` und `discord.invite`. Trage bei
   `discord.invite` deinen Discord-Einladungslink ein und starte den Server
   nach Änderungen neu.
2. Entpacke `SkyZVerify-bot-1.1.0.zip` auf dem Host, der den Bot ausführt.
   Kopiere `config.example.json` zu `config.json`. Trage den Bot-Token, die
   Discord-Server-ID (`guildId`) und **denselben** 64-stelligen
   `bridge.secret`-Wert als `bridgeSecret` ein. `categoryId` ist optional.
   Token und Secret niemals in GitHub, Screenshots oder Logs posten.
3. Benutze Node.js 24.17 oder neuer. Im Bot-Ordner `npm ci` und danach
   `npm start` ausführen. Der Bot muss
   dauerhaft laufen. Neue Spieler bleiben gesperrt, solange er nicht erreichbar ist.

Wenn Bot und Minecraft in **derselben Netzwerkumgebung** laufen, kann
`bridge.url=http://127.0.0.1:8787` und `listenHost=127.0.0.1` bleiben.
Pterodactyl-Container und der Host teilen sich `127.0.0.1` normalerweise
**nicht**. Verwende dann eine für den Container erreichbare **interne**
Bot-Adresse in `bridge.url`, setze `listenHost` auf die passende interne
Schnittstelle und beschränke den Port per Firewall. Für eine Verbindung über
ein öffentliches Netz muss `bridge.url` per HTTPS abgesichert sein.

Der Bot speichert vorübergehende Anfragen unter `bot/data/requests.json`.
Die dauerhaften Verknüpfungen stehen ausschließlich auf dem Minecraft-Server
unter `config/skyzverify-links.properties`. Beide Dateien beim Umzug sichern;
die zweite **nicht** löschen, sonst müssen sich Spieler neu verifizieren.

## Admin-Übersicht in Discord

Nach dem Start erstellt der Bot `#skyzverify-admin` als privaten Kanal. Nur
Discord-Administratoren (und der Bot) können ihn öffnen und die Buttons
bedienen. Dort zeigt ein Embed auf mehreren Seiten **alle gespeicherten
Verknüpfungen** mit Minecraft-Name, Minecraft-UUID, Discord-Username und
Discord-ID an. Die Mod schickt alle zwei Sekunden ihren aktuellen Stand; bei
einem Verbindungsabbruch blendet der Bot veraltete Daten aus. Änderungen durch
`/verify`, `/verifylink` und `/verifyunlink` erscheinen ebenfalls automatisch.

**Hinzufügen** fragt nach Minecraft-Name oder UUID und der Discord-ID. Der
Minecraft-Spieler muss bereits mindestens einmal beigetreten sein, das
Discord-Konto muss Mitglied des eingestellten Discord-Servers sein. **Trennen**
akzeptiert Minecraft-Name, UUID oder Discord-ID. Wenn der Spieler online ist,
trennt die Mod ihn sofort nach Ausführung der Aktion vom Minecraft-Server. Beim
nächsten Beitritt bleibt er gesperrt, bis er sich erneut verifiziert. Ergebnisse
werden im privaten Kanal protokolliert. Offenstehende Aufträge und der Kanal
bleiben unter `bot/data/admin-panel.json` auch nach einem Bot-Neustart erhalten.

Erteile dem Bot im Discord-Server die Rechte **Kanäle verwalten**, **Berechtigungen
verwalten**, **Kanal ansehen**, **Nachrichten senden** und **Nachrichtenverlauf
lesen**. Im Developer Portal unter „Bot“ muss **Server Members Intent** aktiv
sein, damit der Bot Discord-Mitglieder suchen kann. Ein bloßes Installieren als
Benutzer-App reicht nicht; er muss als Bot Mitglied des Servers sein.

### Update von 1.0.0

Stoppe Bot und Minecraft-Server. Sichere `config.json`, den Bot-Ordner `data`
und auf dem Minecraft-Server `config/skyzverify.properties` sowie
`config/skyzverify-links.properties`. Entferne die alte Mod-JAR aus `mods`,
kopiere die **1.1.0-JAR** hinein und ersetze im Bot-Ordner `index.js`, `core.js`,
`admin.js`, `package.json` und `package-lock.json` aus dem neuen Paket. Lasse
`config.json` und `data` bestehen. Führe `npm ci` aus und starte danach Bot und
Minecraft-Server. Beide Komponenten müssen Version 1.1.0 verwenden.

## Ablauf

Neue Spieler erhalten den Titel `/verify <Discord-Username>` und eine
Erklärung im Chat. Beispiel: `/verify jsky`. Es gilt der **exakte Discord-
Username**, nicht der Anzeigename oder Server-Nickname. Der Bot sucht nur
Mitglieder des eingestellten Discord-Servers. Er erstellt einen privaten
Kanal, markiert das gefundene Konto und zeigt einen Bestätigungsbutton. Nur
dieses Discord-Konto kann den Button gültig benutzen. Die Anfrage läuft nach
5 Minuten ab; danach kann der Spieler erneut `/verify` eingeben.

Nach der Bestätigung wird die Discord-ID dauerhaft mit der Minecraft-UUID
verknüpft. Eine Discord-ID kann nur mit **einem** Minecraft-Konto verbunden
werden. Eine erneute Verifikation ist bei späteren Besuchen nicht nötig.

Wartende Spieler können sich serverseitig nicht fortbewegen, Blöcke benutzen,
angreifen, Gegenstände aufheben oder andere Befehle/Chat senden. Sie sind
vor Schaden geschützt und erhalten Blindheit. **Ein rein serverseitiger Mod
kann die bereits an den Client übertragenen Weltblöcke nicht vollständig
unsichtbar rendern.** Für einen buchstäblich schwarzen Bildschirm wäre eine
Client-Mod nötig.

## Adminbefehle (OP-Stufe 2)

- `/search <Discord-ID|Minecraft-Name|UUID>` zeigt die gespeicherte Zuordnung.
- `/verifylink <Minecraft-Name|UUID> <Discord-ID> [Discord-Username]`
  verknüpft manuell. Der Spieler muss dem Server mindestens einmal beigetreten
  sein. Ohne dritten Parameter wird `manuell` als Name angezeigt.
- `/verifyunlink <Minecraft-Name|UUID|Discord-ID>` entfernt die Zuordnung.
  Ein online befindlicher Spieler wird sofort wieder gesperrt.

Die Serverkonsole kann diese Adminbefehle ebenfalls ausführen. Für reguläre
Spieler ist `/verify` ohne OP-Rechte verfügbar. Manuelle Änderungen werden im
Serverlog protokolliert.

## Bauen

Die Mod verwendet Java 8, Gradle 5.6.4 und ForgeGradle 3. Im Verzeichnis
`mod` mit `gradle build` bauen; `build/libs/SkyZVerify-1.1.0.jar` ist das
reobfuskierte Forge-Ergebnis. Für den Bot in `bot` `npm install` und
`npm test` ausführen. Der GitHub-Workflow unter `.github/workflows` baut und
prüft beides und veröffentlicht die Pakete als Build-Artefakt.

Ein echter Verbindungstest benötigt einen eigenen Discord-Bot, einen
Discord-Testserver und einen gestarteten Forge-Dedicated-Server.
