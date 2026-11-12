package net.prismarin.api.stats;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.prismarin.api.admin.NoticeText;

public record StatGame(String id, NoticeText name, String icon, String color, int order, List<String> featured) {

    public static final int DEFAULT_ORDER = 100;
    public static final Pattern ICON = Pattern.compile("[A-Z0-9_]{1,64}");
    public static final Pattern COLOR = Pattern.compile("#[0-9a-f]{6}|[a-z_]{3,20}");
    public static final int MAX_FEATURED = 16;

    public StatGame {
        id = StatKeys.gameId(id);
        name = name == null ? NoticeText.of(StatKeys.gameName(id)) : name;
        icon = icon(icon);
        color = color(color);
        featured = featured(id, featured);
    }

    public static StatGame of(String id) {
        return new StatGame(id, null, "", "", DEFAULT_ORDER, List.of());
    }

    public static StatGame of(String id, String name) {
        return of(id).withName(name);
    }

    public StatGame withName(NoticeText value) {
        return new StatGame(id, value, icon, color, order, featured);
    }

    public StatGame withName(String value) {
        return withName(value == null || value.isBlank() ? null : NoticeText.of(value));
    }

    public StatGame withName(String language, String value) {
        return withName(name.and(language, value));
    }

    public StatGame withIcon(String material) {
        return new StatGame(id, name, material, color, order, featured);
    }

    public StatGame withColor(String value) {
        return new StatGame(id, name, icon, value, order, featured);
    }

    public StatGame withOrder(int value) {
        return new StatGame(id, name, icon, color, value, featured);
    }

    public StatGame withFeatured(String... keys) {
        return withFeatured(List.of(keys));
    }

    public StatGame withFeatured(List<String> keys) {
        return new StatGame(id, name, icon, color, order, keys);
    }

    public boolean hasIcon() {
        return !icon.isEmpty();
    }

    public boolean hasColor() {
        return !color.isEmpty();
    }

    public int featuredIndex(String key) {
        int index = StatKeys.valid(key) ? featured.indexOf(StatKeys.key(key)) : -1;
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static String icon(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!ICON.matcher(normalized).matches()) {
            throw new IllegalArgumentException("'" + value + "' is no material name such as RED_BED");
        }
        return normalized;
    }

    private static String color(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!COLOR.matcher(normalized).matches()) {
            throw new IllegalArgumentException("'" + value + "' is no colour such as #F87171 or gold");
        }
        return normalized;
    }

    private static List<String> featured(String id, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                found.add(StatKeys.inGame(id, key));
            }
        }
        if (found.size() > MAX_FEATURED) {
            throw new IllegalArgumentException("a game features at most " + MAX_FEATURED + " stats");
        }
        return List.copyOf(new ArrayList<>(found));
    }
}
