package net.prismarin.lobby.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ProfileLayout(List<Line> head, int rows, int headSlot, int entryRow, int actionRow, int backSlot,
                            int closeSlot, List<Entry> entries, LanguageClick languageClick, List<Entry> social) {

    public static final String AUTO = "auto";
    public static final String OFF = "off";
    public static final List<String> DEFAULT_HEAD = List.of("currencies", "level");
    public static final Map<String, String> DEFAULT_ENTRIES = defaultEntries();
    public static final ProfileLayout DEFAULT = new ProfileLayout(
            DEFAULT_HEAD.stream().map(Line::parse).toList(), 5, 13, 2, 3, 36, 44,
            List.of(new Entry(Entry.Kind.CURRENCIES, -1), new Entry(Entry.Kind.LEVEL, -1),
                    new Entry(Entry.Kind.LANGUAGE, -1)),
            LanguageClick.MENU,
            List.of(new Entry(Entry.Kind.FRIENDS, -1), new Entry(Entry.Kind.PARTY, -1)));

    public ProfileLayout {
        head = List.copyOf(head);
        entries = List.copyOf(entries);
        social = List.copyOf(social);
        Objects.requireNonNull(languageClick, "languageClick");
    }

    public List<Entry> allEntries() {
        List<Entry> all = new ArrayList<>(entries);
        all.addAll(social);
        return List.copyOf(all);
    }

    public int size() {
        return rows * 9;
    }

    public static boolean classicDefaults(int rows, int headSlot, int entryRow, int backSlot, int closeSlot) {
        return rows == 3 && headSlot == 4 && entryRow == 1 && backSlot == 18 && closeSlot == 26;
    }

    public static boolean actionEntry(Entry.Kind kind) {
        return kind == Entry.Kind.LANGUAGE || kind.social();
    }

    public enum LanguageClick {
        TOGGLE,
        MENU;

        public static Optional<LanguageClick> parse(String value) {
            if (value == null) {
                return Optional.empty();
            }
            return switch (value.strip().toLowerCase(Locale.ROOT)) {
                case "toggle" -> Optional.of(TOGGLE);
                case ProfileLayout.AUTO, "menu", "list" -> Optional.of(MENU);
                default -> Optional.empty();
            };
        }
    }

    public record Line(Kind kind, String argument) {

        public enum Kind {
            CURRENCIES,
            CURRENCY,
            LEVEL,
            RANK,
            EMPTY,
            TEXT
        }

        public Line {
            Objects.requireNonNull(kind, "kind");
            argument = argument == null ? "" : argument;
        }

        public static Line parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new Line(Kind.EMPTY, "");
            }
            String trimmed = raw.strip();
            int colon = trimmed.indexOf(':');
            String name = (colon < 0 ? trimmed : trimmed.substring(0, colon)).strip().toLowerCase(Locale.ROOT);
            String argument = colon < 0 ? "" : trimmed.substring(colon + 1).strip();
            Kind kind = switch (name) {
                case "currencies" -> Kind.CURRENCIES;
                case "currency" -> Kind.CURRENCY;
                case "level" -> Kind.LEVEL;
                case "rank" -> Kind.RANK;
                case "empty", "blank" -> Kind.EMPTY;
                case "text" -> Kind.TEXT;
                default -> throw new IllegalArgumentException("unknown profile line '" + trimmed + "'");
            };
            if ((kind == Kind.CURRENCY || kind == Kind.TEXT) && argument.isEmpty()) {
                throw new IllegalArgumentException("profile line '" + name + "' needs a value after the colon");
            }
            return new Line(kind, kind == Kind.CURRENCY ? argument.toLowerCase(Locale.ROOT) : argument);
        }
    }

    public record Entry(Kind kind, int slot) {

        public enum Kind {
            CURRENCIES,
            LEVEL,
            RANK,
            LANGUAGE,
            FRIENDS,
            PARTY;

            public boolean social() {
                return this == FRIENDS || this == PARTY;
            }

            public String id() {
                return name().toLowerCase(Locale.ROOT);
            }

            public static Optional<Kind> byId(String id) {
                for (Kind kind : values()) {
                    if (kind.id().equals(id.strip().toLowerCase(Locale.ROOT))) {
                        return Optional.of(kind);
                    }
                }
                return Optional.empty();
            }
        }

        public boolean automatic() {
            return slot < 0;
        }
    }

    public static Optional<Integer> placement(String value, int menuSize) {
        String normalized = value == null ? OFF : value.strip().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case AUTO, "true", "on", "yes" -> {
                return Optional.of(-1);
            }
            case OFF, "false", "no", "none", "hidden", "" -> {
                return Optional.empty();
            }
            default -> {
                int slot;
                try {
                    slot = Integer.parseInt(normalized);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("'" + value + "' is neither auto, off nor a slot number");
                }
                if (slot < 0 || slot >= menuSize) {
                    throw new IllegalArgumentException("slot " + slot + " is outside of the profile menu");
                }
                return Optional.of(slot);
            }
        }
    }

    public static List<Line> parseHead(List<String> raws, List<String> warnings) {
        List<Line> lines = new ArrayList<>();
        for (String raw : raws) {
            try {
                lines.add(Line.parse(raw));
            } catch (IllegalArgumentException e) {
                warnings.add("profile.head: " + e.getMessage());
            }
        }
        return lines;
    }

    private static Map<String, String> defaultEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("currencies", AUTO);
        entries.put("level", AUTO);
        entries.put("rank", OFF);
        entries.put("language", AUTO);
        entries.put("friends", AUTO);
        entries.put("party", AUTO);
        return Collections.unmodifiableMap(entries);
    }
}
