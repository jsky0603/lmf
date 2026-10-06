package de.skyz.pipacrafthud;

import java.util.*;
import java.util.function.Function;

/** Immutable ranking for one command response, including players with zero deaths. */
public final class DeathLeaderboard {
    public static final int PAGE_SIZE = 10;
    public static final class Entry {
        public final UUID id;
        public final String name;
        public final long deaths;
        public final int rank;
        private Entry(UUID id, String name, long deaths, int rank) {
            this.id = id; this.name = name; this.deaths = deaths; this.rank = rank;
        }
    }
    private final List<Entry> entries;
    private final long total;

    public DeathLeaderboard(Map<UUID, Long> counts, Function<UUID, String> names) {
        List<Entry> sorted = new ArrayList<>();
        long sum = 0;
        for (Map.Entry<UUID, Long> count : counts.entrySet()) {
            String name = names.apply(count.getKey());
            if (name == null || name.trim().isEmpty()) name = "UUID-" + count.getKey().toString().substring(0, 8);
            long deaths = Math.max(0, count.getValue());
            sum += deaths;
            sorted.add(new Entry(count.getKey(), name, deaths, 0));
        }
        sorted.sort(Comparator.comparingLong((Entry e) -> e.deaths).reversed()
                .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.name).thenComparing(e -> e.id.toString()));
        List<Entry> ranked = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Entry entry = sorted.get(i);
            if (i == 0 || entry.deaths != sorted.get(i - 1).deaths) rank = i + 1;
            ranked.add(new Entry(entry.id, entry.name, entry.deaths, rank));
        }
        entries = Collections.unmodifiableList(ranked);
        total = sum;
    }
    public int size() { return entries.size(); }
    public long total() { return total; }
    public int pages() { return entries.isEmpty() ? 1 : (entries.size() - 1) / PAGE_SIZE + 1; }
    public List<Entry> page(int page) {
        if (page < 1 || page > pages()) throw new IllegalArgumentException("Ungueltige Ranglistenseite.");
        int from = (page - 1) * PAGE_SIZE;
        return entries.subList(from, Math.min(entries.size(), from + PAGE_SIZE));
    }
}
