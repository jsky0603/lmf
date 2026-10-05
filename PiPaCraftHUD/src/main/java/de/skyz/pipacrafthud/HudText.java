package de.skyz.pipacrafthud;

import java.util.Map;

public final class HudText {
    private HudText() {}
    public static String color(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)&([0-9a-fk-or])", "\u00a7$1");
    }
    public static String render(String text, Map<String, String> values) {
        String out = text == null ? "" : text;
        for (Map.Entry<String, String> entry : values.entrySet())
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        return color(out);
    }
    public static String cut(String text, int max) {
        if (text.length() <= max) return text;
        int end = max;
        if (end > 0 && text.charAt(end - 1) == '\u00a7') end--;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
    public static String active(String text) {
        String color = "", styles = "";
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '\u00a7') continue;
            char code = Character.toLowerCase(text.charAt(++i));
            if ("0123456789abcdef".indexOf(code) >= 0) {
                color = "\u00a7" + code; styles = "";
            } else if (code == 'r') { color = ""; styles = ""; }
            else if ("klmno".indexOf(code) >= 0 && styles.indexOf(code) < 0)
                styles += "\u00a7" + code;
        }
        return color + styles;
    }
    public static String[] split(String text) {
        String prefix = cut(text, 16);
        String suffix = cut("\u00a7r" + active(prefix) + text.substring(prefix.length()), 16);
        return new String[] {prefix, suffix};
    }
    public static String frame(String[] frames, long index) {
        return frames.length == 0 ? "" : frames[(int)Math.floorMod(index, frames.length)];
    }
    public static String playtime(int ticks) {
        long minutes = Math.max(0, ticks) / 1200L;
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
