package net.prismarin.shared.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatOrder;

public final class StatCatalog {

    public static final String COLLECTION = "stat-catalog";
    static final String STAT = "stat";
    static final String GAME = "game";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Loaded(Map<String, StatDefinition> definitions, Map<String, StatGame> games) {

        public static final Loaded EMPTY = new Loaded(Map.of(), Map.of());

        public Loaded {
            definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
            games = Collections.unmodifiableMap(new LinkedHashMap<>(games));
        }
    }

    static final class Entry {

        String kind;
        String id;
        Map<String, String> name;
        String format;
        String order;
        Boolean inStats;
        Boolean inProfile;
        String icon;
        String color;
        Integer position;
        List<String> featured;
    }

    private StatCatalog() {
        throw new AssertionError();
    }

    public static String id(StatDefinition definition) {
        return STAT + "." + definition.key();
    }

    public static String id(StatGame game) {
        return GAME + "." + game.id();
    }

    public static String encode(StatDefinition definition) {
        Entry entry = new Entry();
        entry.kind = STAT;
        entry.id = definition.key();
        entry.name = definition.name().templates();
        entry.format = definition.format().id();
        entry.order = definition.order().id();
        entry.inStats = definition.inStats();
        entry.inProfile = definition.inProfile();
        return GSON.toJson(entry);
    }

    public static String encode(StatGame game) {
        Entry entry = new Entry();
        entry.kind = GAME;
        entry.id = game.id();
        entry.name = game.name().templates();
        entry.icon = game.icon();
        entry.color = game.color();
        entry.position = game.order();
        entry.featured = game.featured();
        return GSON.toJson(entry);
    }

    public static Loaded decode(Collection<String> documents) {
        Map<String, StatDefinition> definitions = new LinkedHashMap<>();
        Map<String, StatGame> games = new LinkedHashMap<>();
        for (String json : documents) {
            try {
                Entry entry = GSON.fromJson(json, Entry.class);
                if (entry == null || entry.id == null) {
                    continue;
                }
                NoticeText name = entry.name == null || entry.name.isEmpty() ? null : NoticeText.of(entry.name);
                if (STAT.equals(entry.kind)) {
                    StatDefinition definition = new StatDefinition(entry.id, name,
                            StatFormat.byId(entry.format).orElse(StatFormat.NUMBER),
                            StatOrder.byId(entry.order).orElse(StatOrder.HIGHEST),
                            entry.inStats == null || entry.inStats, entry.inProfile != null && entry.inProfile);
                    definitions.put(definition.key(), definition);
                } else if (GAME.equals(entry.kind)) {
                    StatGame game = new StatGame(entry.id, name, entry.icon, entry.color,
                            entry.position == null ? StatGame.DEFAULT_ORDER : entry.position, entry.featured);
                    games.put(game.id(), game);
                }
            } catch (RuntimeException e) {
                continue;
            }
        }
        return new Loaded(definitions, games);
    }
}
