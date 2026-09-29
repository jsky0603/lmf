package de.skyz.skyzverify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class LinkStoreTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempDirectory("skyzverify-").resolve("links.properties");
        UUID first = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID second = UUID.fromString("22222222-2222-4222-8222-222222222222");
        LinkStore store = new LinkStore(file);
        store.remember(first, "WahnhopeTv");
        store.remember(second, "Nou");
        store.link(first, "123456789012345678", "jsky");
        check(store.isLinked(first), "Link fehlt");
        check(store.search("123456789012345678").get(0).uuid.equals(first), "ID-Suche falsch");
        try {
            store.link(second, "123456789012345678", "jsky");
            throw new AssertionError("Doppelte Discord-ID akzeptiert");
        } catch (IllegalArgumentException expected) { /* one Discord account per player */ }
        store.remember(first, "NeuerName");
        LinkStore reloaded = new LinkStore(file);
        check(reloaded.isLinked(first), "Neustart verlor die Verknüpfung");
        check(reloaded.search("NeuerName").get(0).uuid.equals(first), "Namensänderung nicht gespeichert");
        reloaded.unlink("123456789012345678");
        check(!new LinkStore(file).isLinked(first), "Löschen nicht gespeichert");
        Files.write(file, ("player." + first + "=NeuerName\nlink." + first + "=ungueltig\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        try {
            new LinkStore(file);
            throw new AssertionError("Beschädigte Datei stillschweigend akzeptiert");
        } catch (IOException expected) { /* fail closed */ }
        Path configuration = file.getParent().resolve("config").resolve("skyzverify.properties");
        VerifyConfig generated = VerifyConfig.load(configuration);
        check(generated.bridgeSecret.matches("[a-f0-9]{64}"), "Kein sicherer Bridge-Schlüssel");
        check(generated.bridgeSecret.equals(VerifyConfig.load(configuration).bridgeSecret),
                "Bridge-Schlüssel ging beim Neustart verloren");
        System.out.println("SkyZVerify: persistente eindeutige Verknüpfung und Fehlerfall geprüft.");
    }
}
