package de.skyz.pipacrafthud;

import java.util.*;

public final class HudTextTest {
    private static int tests;
    private static void eq(Object expected, Object actual) {
        tests++; if (!Objects.equals(expected, actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
    public static void main(String[] args) throws Exception {
        eq("\u00a7dLIVE &z", HudText.color("&dLIVE &z"));
        eq("123456789012345", HudText.cut("123456789012345\u00a7d", 16));
        eq("123456789012345", HudText.cut("123456789012345\ud83d\udc96", 16));
        eq("\u00a7d\u00a7l", HudText.active("\u00a75hi\u00a7d\u00a7lthere"));
        eq("", HudText.active("\u00a7d\u00a7lhi\u00a7r"));
        eq("\u00a7a", HudText.active("\u00a7d\u00a7lhi\u00a7a"));
        eq("1h 1m", HudText.playtime(73200));
        eq("0h 0m", HudText.playtime(-1));
        eq("B", HudText.frame(new String[]{"A","B"}, 3));
        eq("", HudText.frame(new String[0], 5));
        Map<String, String> vars = new LinkedHashMap<>(); vars.put("player", "Sky"); vars.put("live", "&dLIVE");
        eq("Sky \u00a7dLIVE", HudText.render("{player} {live}", vars));
        for (String s : Arrays.asList("", "\u00a7dhello", "\u00a7d1234567890123456789012345678901234567890", "123456789012345\u00a7aABC")) {
            String[] split = HudText.split(s);
            eq(true, split[0].length() <= 16 && split[1].length() <= 16);
            eq(false, split[0].endsWith("\u00a7") || split[1].endsWith("\u00a7"));
        }
        HudConfig c = new HudConfig(); c.validate(); tests++;
        c.scoreboardLines = new String[16];
        try { c.validate(); throw new AssertionError("16 lines accepted"); } catch (IllegalArgumentException expected) { tests++; }
        c = new HudConfig(); c.updateTicks = 0;
        try { c.validate(); throw new AssertionError("zero interval accepted"); } catch (IllegalArgumentException expected) { tests++; }
        System.out.println("PiPaCraft HUD: " + tests + " Text-/Config-Pruefungen bestanden.");
        try (java.io.Writer writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream("pipacrafthud.example.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(new HudConfig(), writer);
        }
    }
}
