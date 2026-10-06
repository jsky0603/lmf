package de.skyz.pipacrafthud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

/** One count per player UUID; online stats replace their saved copy rather than adding it. */
public final class ServerDeathStats {
    private final Map<UUID, Long> counts = new HashMap<>();
    private long total;

    public long total() { return total; }
    public Map<UUID, Long> snapshot() { return Collections.unmodifiableMap(new HashMap<>(counts)); }

    public void update(UUID player, long deaths) {
        long count = Math.max(0, deaths);
        long before = counts.getOrDefault(player, 0L);
        counts.put(player, count);
        total += count - before;
    }

    public static ServerDeathStats load(Path directory, BiConsumer<Path, Exception> onError) {
        ServerDeathStats stats = new ServerDeathStats();
        if (!Files.exists(directory)) return stats;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                String stem = name.substring(0, name.length() - 5);
                UUID id;
                try { id = UUID.fromString(stem); }
                catch (IllegalArgumentException ignored) { continue; }
                if (!id.toString().equalsIgnoreCase(stem)) continue;
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement root = new JsonParser().parse(reader);
                    if (!root.isJsonObject()) throw new IllegalArgumentException("Stats-Datei ist kein JSON-Objekt.");
                    stats.update(id, readDeaths(root.getAsJsonObject()));
                } catch (IOException | RuntimeException ex) { onError.accept(file, ex); }
            }
        } catch (IOException ex) { onError.accept(directory, ex); }
        return stats;
    }

    private static long readDeaths(JsonObject root) {
        JsonElement value = root.get("stat.deaths");
        if (value == null) return 0;
        if (value.isJsonObject()) value = value.getAsJsonObject().get("value");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("stat.deaths ist keine ganze Zahl.");
        long count = value.getAsBigDecimal().longValueExact();
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("stat.deaths liegt ausserhalb des Minecraft-Statistikbereichs.");
        return Math.max(0, count);
    }
}
