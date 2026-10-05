package de.skyz.pipacrafthud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;

public final class HudConfig {
    public String serverName = "PiPa Craft";
    public String timezone = "Europe/Berlin";
    public int updateTicks = 10;
    public int animationTicks = 10;
    public boolean scoreboardEnabled = true;
    public boolean tabEnabled = true;
    public boolean nameTagsEnabled = true;
    public boolean chatPrefixEnabled = true;
    public boolean liveAnnouncements = true;
    public boolean protectExistingSidebar = true;
    public int liveCommandCooldownSeconds = 5;
    public String livePrefix = "&d[LIVE] &r";
    public String tabPlayerFormat = "{live_prefix}&f{player}";
    public String liveOnLabel = "&dLIVE";
    public String liveOffLabel = "&7Offline";
    public String liveOnMessage = "&d{player} &fist jetzt live. Bitte respektiert, wenn jemand nicht im Stream vorkommen moechte.";
    public String liveOffMessage = "&d{player} &fist nicht mehr live.";
    public String livePersonalOn = "&dDein Live-Status ist jetzt aktiviert.";
    public String livePersonalOff = "&7Dein Live-Status ist jetzt deaktiviert.";
    public String liveStatusMessage = "&fDein Status: {live}";
    public String liveDisabledMessage = "&7/live ist in der Config deaktiviert.";
    public boolean liveEnabled = true;
    public String cooldownMessage = "&7Bitte warte {seconds} Sekunden.";
    public String hudOnMessage = "&dDein Scoreboard ist aktiviert.";
    public String hudOffMessage = "&7Dein Scoreboard ist deaktiviert.";
    public String reloadMessage = "&dPiPaCraft HUD: Config neu geladen.";
    public String[] titleFrames = {"&d&lPiPa &5&lCraft", "&5&lPiPa &d&lCraft", "&d&lPiPa &f&lCraft", "&f&lPiPa &d&lCraft"};
    public String[] headerFrames = {
        "&5&m------------------------\n&d&lPiPa &5&lCraft\n&fGemeinsam spielen &d<3\n&7Online: &f{online}&7/&f{max_players}",
        "&d&m------------------------\n&5&lPiPa &d&lCraft\n&fGemeinsam spielen &5<3\n&7Online: &f{online}&7/&f{max_players}"
    };
    public String[] footerFrames = {
        "&7Live: &d{live_count} &8| &7Ping: &f{ping} ms\n&fMit &d/live &fmarkierst du deinen Stream.\n&5&m------------------------",
        "&7Live: &d{live_count} &8| &7Ping: &f{ping} ms\n&fMit &5/live &fmarkierst du deinen Stream.\n&d&m------------------------"
    };
    public String[] scoreboardLines = {"&5&m----------------", "&7Spieler: &f{player}", "&7Online: &d{online}&7/&f{max_players}", "&7Stream: {live}", "&r", "&7Spielzeit: &f{playtime}", "&7Tode: &f{deaths}", "&7Ping: &f{ping} ms", "&7TPS: &f{tps}", "&r", "&d/live &7zum Umschalten", "&5&m----------------"};
    public Map<String, String[]> animations = new LinkedHashMap<>();
    public Map<String, String> dimensionNames = new LinkedHashMap<>();

    public HudConfig() {
        dimensionNames.put("0", "Oberwelt"); dimensionNames.put("-1", "Nether");
        dimensionNames.put("1", "End"); dimensionNames.put("20", "Halloweenwelt");
        animations.put("heart", new String[] {"&d<3", "&5<3", "&f<3", "&5<3"});
    }
    public void validate() {
        if (updateTicks < 5 || updateTicks > 1200 || animationTicks < 5 || animationTicks > 1200)
            throw new IllegalArgumentException("updateTicks und animationTicks: 5 bis 1200.");
        if (liveCommandCooldownSeconds < 0 || liveCommandCooldownSeconds > 3600)
            throw new IllegalArgumentException("liveCommandCooldownSeconds: 0 bis 3600.");
        ZoneId.of(timezone);
        check(titleFrames, "titleFrames", 100); check(headerFrames, "headerFrames", 100);
        check(footerFrames, "footerFrames", 100); check(scoreboardLines, "scoreboardLines", 15);
        if (scoreboardLines.length == 0) throw new IllegalArgumentException("scoreboardLines ist leer.");
        if (animations == null || dimensionNames == null) throw new IllegalArgumentException("Maps duerfen nicht null sein.");
        for (Map.Entry<String, String[]> e : animations.entrySet()) {
            if (!e.getKey().matches("[a-zA-Z][a-zA-Z0-9_]*")) throw new IllegalArgumentException("Ungueltiger Animationsname.");
            check(e.getValue(), "animations." + e.getKey(), 100);
        }
        for (java.lang.reflect.Field f : getClass().getFields()) {
            try { if (f.getType() == String.class && f.get(this) == null) throw new IllegalArgumentException(f.getName() + " darf nicht null sein."); }
            catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
        }
    }
    private static void check(String[] values, String key, int max) {
        if (values == null || values.length > max) throw new IllegalArgumentException(key + ": maximal " + max + " Eintraege.");
        for (String value : values) if (value == null || value.length() > 2048)
            throw new IllegalArgumentException(key + ": null oder zu langer Text.");
    }
    public static HudConfig load(File file) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        if (!file.exists()) {
            Files.createDirectories(file.toPath().getParent());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { gson.toJson(new HudConfig(), w); }
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            HudConfig config = gson.fromJson(r, HudConfig.class);
            if (config == null) throw new IllegalArgumentException("Config ist leer.");
            config.validate(); return config;
        }
    }
}
