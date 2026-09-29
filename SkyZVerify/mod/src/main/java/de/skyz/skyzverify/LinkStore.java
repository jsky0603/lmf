package de.skyz.skyzverify;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.Properties;

/** Persistent UUID -> Discord ID mapping. Every change is saved before it is applied. */
public final class LinkStore {
    private static final Pattern DISCORD_ID = Pattern.compile("[0-9]{17,20}");
    private static final Pattern DISCORD_USER = Pattern.compile("[a-z0-9._]{2,32}");
    private static final Pattern MC_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final Path file;
    private Map<UUID, String> known = new HashMap<UUID, String>();
    private Map<UUID, Link> links = new HashMap<UUID, Link>();

    public static final class Link {
        public final String discordId;
        public final String discordUsername;

        private Link(String discordId, String discordUsername) {
            this.discordId = discordId;
            this.discordUsername = discordUsername;
        }
    }

    public static final class Result {
        public final UUID uuid;
        public final String playerName;
        public final Link link;

        private Result(UUID uuid, String playerName, Link link) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.link = link;
        }
    }

    public LinkStore(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        try {
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("player.") && !key.startsWith("link.")) {
                    throw new IllegalArgumentException("Unbekannter Schluessel");
                }
                UUID uuid = UUID.fromString(key.substring(key.indexOf('.') + 1));
                String value = properties.getProperty(key);
                if (key.startsWith("player.")) {
                    if (!MC_NAME.matcher(value).matches()) throw new IllegalArgumentException("Ungueltiger Spielername");
                    known.put(uuid, value);
                } else {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 2 || !validDiscordId(parts[0]) || !validDiscordUsername(parts[1])) {
                        throw new IllegalArgumentException("Ungueltige Verknuepfung");
                    }
                    links.put(uuid, new Link(parts[0], parts[1]));
                }
            }
            for (Map.Entry<UUID, Link> entry : links.entrySet()) {
                if (!known.containsKey(entry.getKey())) throw new IllegalArgumentException("Verknuepfung ohne Spieler");
                for (Map.Entry<UUID, Link> other : links.entrySet()) {
                    if (!entry.getKey().equals(other.getKey()) && entry.getValue().discordId.equals(other.getValue().discordId)) {
                        throw new IllegalArgumentException("Discord-ID doppelt vergeben");
                    }
                }
            }
        } catch (RuntimeException badFile) {
            throw new IOException("SkyZVerify-Verknuepfungsdatei ist ungueltig; sie wurde nicht ueberschrieben.", badFile);
        }
    }

    public static boolean validDiscordId(String id) {
        return id != null && DISCORD_ID.matcher(id).matches();
    }

    public static boolean validDiscordUsername(String name) {
        return name != null && DISCORD_USER.matcher(name.toLowerCase(Locale.ROOT)).matches();
    }

    public synchronized void remember(UUID uuid, String name) throws IOException {
        if (!MC_NAME.matcher(name).matches()) throw new IllegalArgumentException("Ungueltiger Minecraft-Name");
        if (name.equals(known.get(uuid))) return;
        Map<UUID, String> next = new HashMap<UUID, String>(known);
        next.put(uuid, name);
        save(next, links);
        known = next;
    }

    public synchronized boolean isLinked(UUID uuid) {
        return links.containsKey(uuid);
    }

    public synchronized void link(UUID uuid, String discordId, String discordUsername) throws IOException {
        String cleanName = discordUsername.toLowerCase(Locale.ROOT);
        if (!validDiscordId(discordId) || !validDiscordUsername(cleanName)) {
            throw new IllegalArgumentException("Ungueltige Discord-ID oder Username");
        }
        if (!known.containsKey(uuid)) throw new IllegalArgumentException("Spieler muss mindestens einmal beigetreten sein");
        if (links.containsKey(uuid)) throw new IllegalArgumentException("Minecraft-Spieler ist bereits verknuepft");
        for (Link link : links.values()) {
            if (link.discordId.equals(discordId)) throw new IllegalArgumentException("Discord-Konto ist bereits verknuepft");
        }
        Map<UUID, Link> next = new HashMap<UUID, Link>(links);
        next.put(uuid, new Link(discordId, cleanName));
        save(known, next);
        links = next;
    }

    public synchronized Result unlink(String query) throws IOException {
        Result found = findOne(query);
        if (found == null || found.link == null) throw new IllegalArgumentException("Keine Verknuepfung gefunden");
        Map<UUID, Link> next = new HashMap<UUID, Link>(links);
        next.remove(found.uuid);
        save(known, next);
        links = next;
        return found;
    }

    public synchronized Result findOne(String query) {
        List<Result> matches = search(query);
        if (matches.size() > 1) throw new IllegalArgumentException("Name ist mehrdeutig; bitte UUID verwenden");
        return matches.isEmpty() ? null : matches.get(0);
    }

    public synchronized List<Result> search(String query) {
        List<Result> out = new ArrayList<Result>();
        for (Map.Entry<UUID, String> player : known.entrySet()) {
            Link link = links.get(player.getKey());
            if (player.getKey().toString().equalsIgnoreCase(query)
                    || player.getValue().equalsIgnoreCase(query)
                    || (link != null && link.discordId.equals(query))) {
                out.add(new Result(player.getKey(), player.getValue(), link));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private void save(Map<UUID, String> players, Map<UUID, Link> bindings) throws IOException {
        Files.createDirectories(file.getParent());
        Properties properties = new Properties();
        for (Map.Entry<UUID, String> player : players.entrySet()) {
            properties.setProperty("player." + player.getKey(), player.getValue());
        }
        for (Map.Entry<UUID, Link> binding : bindings.entrySet()) {
            properties.setProperty("link." + binding.getKey(),
                    binding.getValue().discordId + "|" + binding.getValue().discordUsername);
        }
        Path temporary = Files.createTempFile(file.getParent(), "skyzverify-links-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                properties.store(out, "SkyZVerify 1.0");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
