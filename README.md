# SkyZGallery · Forge 1.12.2

Wandbilder aus Dateien auf dem Server. Getragene Rüstung ist in der Spielwelt
unsichtbar; im Inventar bleiben Rüstungsgegenstände und ihre Schutzwerte erhalten.
Auch die 3D-Vorschau im Inventar zeigt die Rüstung weiterhin an.

## Einrichtung

Die gleiche JAR gehört in den `mods`-Ordner des Forge-Servers und in den
`mods`-Ordner aller Spieler. Benötigt Minecraft 1.12.2 mit Forge
14.23.5.2859 oder neuer innerhalb von 1.12.2. Die Mod läuft eigenständig
neben SkyZMusic; sie verändert keine Musikdateien.

Nach dem ersten Start gibt es `<Serververzeichnis>/skyzgallery/images/`.
Dort legen Serveradministratoren PNG-, JPG- oder JPEG-Dateien ab. Maximal
2 MiB und 1024 × 1024 Pixel pro Datei. Der Client fordert die Datei erst an,
wenn ein Spieler ein aufgehängtes Bild aus der Nähe sieht. Bilder liegen auf
dem Client nur im Arbeitsspeicher, nicht dauerhaft auf der Festplatte.

## Bilder erstellen und aufhängen

`/createimage <Datei> <Breite> <Höhe> <Bildname...>` erstellt ein Bild-Item im
Inventar des ausführenden Admins (OP-Stufe 2). Beispiel:

    /createimage pipa-merch.png 3 2 PiPa Merch

Das Bild-Item mit Rechtsklick auf eine feste Wand setzen. Breite und Höhe
sind in Blöcken angegeben, jeweils 1 bis 8. An der Wand muss entsprechend
viel Platz sein. Beim Abbau fällt das Bild-Item wieder herunter.

Jeder Spieler kann die Größe eines Bild-Items in der Haupthand ändern:

    /imagesize 4 3

Bereits aufgehängte Bilder werden dadurch nicht verändert. Diese zuerst
abbauen, das Item anpassen und danach erneut aufhängen. Admins können
erstellte Bild-Items an andere Spieler weitergeben. Bitte nur Illustrationen
hochladen, die mit Erlaubnis der Künstler verwendet werden dürfen.

Wird eine Bilddatei nachträglich entfernt oder verändert, bleibt der Rahmen
als Platzhalter sichtbar. In diesem Fall mit `/createimage` ein neues Item
erstellen und das alte Bild ersetzen.

## Bauen

Zum Erstellen der installierbaren JAR brauchst du ein Java-8-JDK, Gradle
5.6.4 und Zugriff auf die in `build.gradle` eingetragenen Forge- und
Maven-Repositories. Im Projektverzeichnis `gradle build` ausführen. Das
ForgeGradle-Ergebnis liegt unter `build/libs/SkyZGallery-1.0.0.jar`.
Eine von Hand mit `javac` erzeugte JAR ist nicht installierbar, weil
ForgeGradle die Minecraft-Namen für den Server umwandeln muss.

Die eigenständigen Tests für Bildformat, Hash und Grenzen stehen unter
`src/test/java` und können ohne Minecraft ausgeführt werden. Vor dem Einsatz
empfehle ich einen Test auf einem separaten Forge-1.12.2-Server mit Client.

## Automatischer Build auf GitHub

Das Projekt enthält `.github/workflows/build.yml`. Wenn die Projektdateien
im Hauptverzeichnis eines GitHub-Repositorys liegen, baut GitHub Actions
bei einem Push auf `main` oder `skyzgallery-build` die Mod mit Java 8 und
ForgeGradle 3. Nach erfolgreichem Build steht die JAR als Workflow-Artefakt
`SkyZGallery-1.0.0` für 14 Tage zum Download bereit. Der Build prüft zusätzlich
Mod-Metadaten und Java-8-Bytecode. Ein Test im laufenden Minecraft ist separat
nötig. Der Workflow benötigt keine gespeicherten Zugangsdaten.
