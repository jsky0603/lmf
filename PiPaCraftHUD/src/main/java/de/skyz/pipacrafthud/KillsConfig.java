package de.skyz.pipacrafthud;

/** Text and colours for the public death leaderboard. Page size stays limited to ten. */
public final class KillsConfig {
    public boolean enabled = true;
    public String separator = "&5&m--------------------------------";
    public String title = "&d&lPiPa &5&lCraft &8| &f&lTodesrangliste";
    public String summary = "&7{players} Spieler &8| &7Server-Tode: &d{total_deaths}";
    public String row = "{rank_color}#{rank} &f{player} &8\u00bb &d{deaths} &7Tode";
    public String empty = "&7Noch keine Spielerdaten vorhanden.";
    public String previous = "&d\u00ab Zur\u00fcck";
    public String next = "&dWeiter \u00bb";
    public String page = "&7Seite &f{page}&7/&f{pages}";
    public String navigationSeparator = "&8 | ";
    public String navigationHover = "&fZu Seite &d{target_page}";
    public String playerHover = "&7Spieler: &f{player}\n&7UUID: &f{uuid}";
    public String hint = "&8Die meisten Tode zuerst. &7/kills <Seite>";
    public String usage = "&7Nutzung: &d/kills [Seite]";
    public String invalidPage = "&cDiese Seite gibt es nicht. &7W\u00e4hle &d1 &7bis &d{pages}&7.";
    public String disabled = "&7Die Todesrangliste ist deaktiviert.";
    // First, second, third and all remaining places. Tied players use their shared place's colour.
    public String[] rankColors = {"&6&l", "&d&l", "&5&l", "&7"};

    public void validate() {
        for (java.lang.reflect.Field field : getClass().getFields()) {
            try {
                if (field.getType() == String.class) check((String)field.get(this), "kills." + field.getName());
            } catch (IllegalAccessException ex) { throw new IllegalStateException(ex); }
        }
        if (rankColors == null || rankColors.length != 4)
            throw new IllegalArgumentException("kills.rankColors: vier Farben fuer Platz 1, 2, 3 und alle weiteren Plaetze.");
        for (String color : rankColors) check(color, "kills.rankColors");
    }
    private static void check(String text, String key) {
        if (text == null || text.length() > 2048) throw new IllegalArgumentException(key + ": null oder zu langer Text.");
    }
}
