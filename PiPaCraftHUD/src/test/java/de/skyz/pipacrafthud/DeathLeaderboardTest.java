package de.skyz.pipacrafthud;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class DeathLeaderboardTest {
    private static int tests;
    private static void eq(Object expected, Object actual) {
        tests++; if (!Objects.equals(expected, actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
    private static UUID id(int i) { return new UUID(0, i); }
    private static void reject(Runnable action) {
        tests++;
        try { action.run(); throw new AssertionError("Invalid input accepted"); }
        catch (IllegalArgumentException | UnsupportedOperationException expected) { }
    }
    private static void write(Path path, String text) throws Exception { Files.write(path, text.getBytes(StandardCharsets.UTF_8)); }

    public static void main(String[] args) throws Exception {
        ServerDeathStats stats = new ServerDeathStats();
        Map<UUID, String> names = new HashMap<>();
        for (int i = 1; i <= 23; i++) {
            stats.update(id(i), 23 - i);
            names.put(id(i), "Player" + i);
        }
        Map<UUID, Long> snapshot = stats.snapshot();
        stats.update(id(1), 30);
        eq(22L, snapshot.get(id(1))); // A running command uses one stable snapshot.
        reject(() -> snapshot.put(id(99), 10L));
        DeathLeaderboard ranking = new DeathLeaderboard(snapshot, names::get);
        eq(23, ranking.size()); eq(3, ranking.pages()); eq(253L, ranking.total());
        eq(10, ranking.page(1).size()); eq(10, ranking.page(2).size()); eq(3, ranking.page(3).size());
        eq("Player1", ranking.page(1).get(0).name); eq(1, ranking.page(1).get(0).rank);
        eq(11, ranking.page(2).get(0).rank); eq(23, ranking.page(3).get(2).rank);
        eq(0L, ranking.page(3).get(2).deaths); // Zero deaths still appears.
        Set<UUID> seen = new HashSet<>();
        for (int page = 1; page <= ranking.pages(); page++) for (DeathLeaderboard.Entry entry : ranking.page(page)) seen.add(entry.id);
        eq(23, seen.size()); // Nobody is skipped or appears on two pages.
        reject(() -> ranking.page(0)); reject(() -> ranking.page(4));
        reject(() -> ranking.page(1).clear());
        eq(1, new DeathLeaderboard(Collections.emptyMap(), names::get).pages());
        eq(0, new DeathLeaderboard(Collections.emptyMap(), names::get).page(1).size());
        for (int players : new int[]{1, 10, 11, 20, 21}) {
            Map<UUID, Long> counts = new HashMap<>();
            for (int i = 1; i <= players; i++) counts.put(id(i), 0L);
            DeathLeaderboard boundary = new DeathLeaderboard(counts, names::get);
            eq((players + 9) / 10, boundary.pages());
            eq((players - 1) % 10 + 1, boundary.page(boundary.pages()).size());
        }
        Map<UUID, Long> ties = new HashMap<>();
        ties.put(id(1), 9L); ties.put(id(2), 9L); ties.put(id(3), 5L);
        names.put(id(1), "Zulu"); names.put(id(2), "alice"); names.put(id(3), "Charlie");
        DeathLeaderboard tied = new DeathLeaderboard(ties, names::get);
        eq("alice", tied.page(1).get(0).name); eq("Zulu", tied.page(1).get(1).name);
        eq(1, tied.page(1).get(0).rank); eq(1, tied.page(1).get(1).rank); eq(3, tied.page(1).get(2).rank);
        Map<UUID, Long> crossing = new HashMap<>();
        for (int i = 1; i <= 11; i++) crossing.put(id(i), 5L);
        eq(1, new DeathLeaderboard(crossing, names::get).page(2).get(0).rank); // Ties cross page boundaries.
        eq("UUID-12345678", new DeathLeaderboard(Collections.singletonMap(UUID.fromString("12345678-0000-0000-0000-000000000001"), 1L), uuid -> null).page(1).get(0).name);
        ties.put(id(1), (long)Integer.MAX_VALUE); ties.put(id(2), (long)Integer.MAX_VALUE);
        eq(4294967299L, new DeathLeaderboard(ties, names::get).total());

        Path temp = Files.createTempDirectory("pipa-ranking-test-");
        try {
            Path disk = temp.resolve("stats"); Files.createDirectory(disk);
            write(disk.resolve(id(1) + ".json"), "{\"stat.deaths\":42}");
            write(disk.resolve(id(2) + ".json"), "{}");
            ServerDeathStats saved = ServerDeathStats.load(disk, (path, ex) -> { throw new AssertionError(ex); });
            saved.update(id(3), 99); // Online player not yet saved.
            DeathLeaderboard mixed = new DeathLeaderboard(saved.snapshot(), names::get);
            eq(3, mixed.size()); eq(id(3), mixed.page(1).get(0).id); eq(0L, mixed.page(1).get(2).deaths);
            Path file = temp.resolve("pipacrafthud.json");
            String old = "{\"configVersion\":2,\"serverName\":\"My Server\",\"scoreboardLines\":[\"My Line\"],\"futureSetting\":123}";
            write(file, old);
            HudConfig upgraded = HudConfig.load(file.toFile());
            eq(3, upgraded.configVersion); eq("My Server", upgraded.serverName);
            eq(Collections.singletonList("My Line"), Arrays.asList(upgraded.scoreboardLines));
            eq(true, upgraded.kills.enabled);
            eq(old, new String(Files.readAllBytes(temp.resolve("pipacrafthud.json.v2.bak")), StandardCharsets.UTF_8));
            String firstLoad = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(firstLoad).getAsJsonObject();
            eq(123, root.get("futureSetting").getAsInt()); eq(true, root.has("kills"));
            HudConfig.load(file.toFile());
            eq(firstLoad, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            root.getAsJsonObject("kills").addProperty("enabled", false);
            root.getAsJsonObject("kills").addProperty("row", "Custom #{rank}: {player}, {deaths}");
            root.getAsJsonObject("kills").addProperty("futureKillsSetting", true);
            write(file, root.toString());
            HudConfig custom = HudConfig.load(file.toFile());
            eq(false, custom.kills.enabled); eq("Custom #{rank}: {player}, {deaths}", custom.kills.row);
            eq(true, new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("kills").get("futureKillsSetting").getAsBoolean());
            HudConfig invalid = new HudConfig(); invalid.kills.rankColors = new String[]{"&d"};
            reject(invalid::validate);
            invalid.kills = new KillsConfig(); invalid.kills.row = null; reject(invalid::validate);
            invalid.kills = null; reject(invalid::validate);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("rank", "1"); row.put("rank_color", "&6&l"); row.put("player", "Alice"); row.put("deaths", "42");
            eq("#1 Alice \u00bb 42 Tode", HudText.render(new KillsConfig().row, row).replaceAll("\u00a7[0-9a-fk-or]", ""));
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                for (Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
        System.out.println("PiPaCraft HUD: " + tests + " Ranglisten-/Seiten-/Config-Pruefungen bestanden.");
    }
}
