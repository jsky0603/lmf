package de.skyz.pipacrafthud;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ServerDeathsTest {
    private static int tests;
    private static void eq(Object expected, Object actual) {
        tests++; if (!Objects.equals(expected, actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
    private static void write(Path path, String text) throws Exception { Files.write(path, text.getBytes(StandardCharsets.UTF_8)); }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("pipa-deaths-test-");
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), charlie = UUID.randomUUID();
        Path stats = temp.resolve("stats"); Files.createDirectory(stats);
        write(stats.resolve(alice + ".json"), "{\"stat.deaths\":5,\"stat.playOneMinute\":1234}");
        write(stats.resolve(bob + ".json"), "{\"stat.deaths\":{\"value\":3}}");
        write(stats.resolve(charlie + ".json"), "{\"stat.jump\":1}");
        write(stats.resolve("not-a-player.json"), "{\"stat.deaths\":999}");
        write(stats.resolve(UUID.randomUUID() + ".json"), "{bad}");
        write(stats.resolve(UUID.randomUUID() + ".json"), "{\"stat.deaths\":1.5}");
        List<Path> failures = new ArrayList<>();
        ServerDeathStats counter = ServerDeathStats.load(stats, (path, ex) -> failures.add(path));
        eq(8L, counter.total()); eq(2, failures.size());
        counter.update(alice, 7); eq(10L, counter.total()); // Online replaces saved 5; does not add another 7.
        counter.update(alice, 7); eq(10L, counter.total());
        counter.update(bob, 4); eq(11L, counter.total());
        counter.update(charlie, 1); eq(12L, counter.total());
        counter.update(alice, 0); eq(5L, counter.total()); // Reset statistics must lower the total.
        counter.update(alice, -1); eq(5L, counter.total());
        write(stats.resolve(bob + ".json"), "{\"stat.deaths\":4}");
        eq(9L, ServerDeathStats.load(stats, (path, ex) -> {}).total()); // Restart reads persistent counts.
        eq(0L, ServerDeathStats.load(temp.resolve("missing"), (path, ex) -> {}).total());
        ServerDeathStats big = new ServerDeathStats();
        big.update(alice, Integer.MAX_VALUE); big.update(bob, Integer.MAX_VALUE);
        eq(4294967294L, big.total());

        Path config = temp.resolve("pipacrafthud.json");
        String legacy = "{\"serverName\":\"Custom\",\"scoreboardLines\":[\"&7Tode: &f{deaths}\",\"Tail\"],\"futureField\":true}";
        write(config, legacy);
        HudConfig migrated = HudConfig.load(config.toFile());
        eq("Custom", migrated.serverName); eq(2, migrated.configVersion);
        eq(Arrays.asList("&7Tode: &f{deaths}", "&7Server Tode: &f{server_deaths}", "Tail"), Arrays.asList(migrated.scoreboardLines));
        eq(legacy, new String(Files.readAllBytes(temp.resolve("pipacrafthud.json.v1.bak")), StandardCharsets.UTF_8));
        JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(config), StandardCharsets.UTF_8)).getAsJsonObject();
        eq(true, root.get("futureField").getAsBoolean());
        eq(3, HudConfig.load(config.toFile()).scoreboardLines.length); // Reload does not insert twice.
        root.add("scoreboardLines", new Gson().toJsonTree(new String[]{"Only my own line"}));
        write(config, root.toString());
        eq(1, HudConfig.load(config.toFile()).scoreboardLines.length); // User can deliberately remove the line.
        root.remove("configVersion");
        String[] full = new String[15]; Arrays.fill(full, "Custom");
        root.add("scoreboardLines", new Gson().toJsonTree(full));
        write(config, root.toString());
        eq(15, HudConfig.load(config.toFile()).scoreboardLines.length); // No custom row is discarded.
        write(config, "{\"scoreboardLines\":[null]}");
        String invalid = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        try { HudConfig.load(config.toFile()); throw new AssertionError("Invalid config migrated"); }
        catch (IllegalArgumentException expected) { tests++; }
        eq(invalid, new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
        Map<String,String> values = new HashMap<>(); values.put("server_deaths", "12345");
        String row = HudText.render("&7Server Tode: &f{server_deaths}", values);
        String[] parts = HudText.split(row);
        eq("Server Tode: 12345", (parts[0] + parts[1]).replaceAll("\u00a7[0-9a-fk-or]", ""));
        System.out.println("PiPaCraft HUD: " + tests + " Server-Tode-/Migrationspruefungen bestanden.");
        try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
            for (Path p : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }
}
