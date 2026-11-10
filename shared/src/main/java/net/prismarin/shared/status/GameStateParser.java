package net.prismarin.shared.status;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.server.GameState;

public final class GameStateParser {

    public static final String MOTD_PREFIX = "prismarin:";

    public static final Map<GameState, List<String>> DEFAULT_MAPPING = defaults();

    private GameStateParser() {
        throw new AssertionError();
    }

    public static GameState parse(String text, Map<GameState, List<String>> mapping) {
        if (text == null || text.isBlank()) {
            return GameState.UNKNOWN;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<GameState, List<String>> entry : mapping.entrySet()) {
            for (String word : entry.getValue()) {
                if (!word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT))) {
                    return entry.getKey();
                }
            }
        }
        return GameState.UNKNOWN;
    }

    public static Parsed parseMotd(String description) {
        if (description == null) {
            return new Parsed(GameState.UNKNOWN, "");
        }
        String trimmed = description.strip();
        if (trimmed.startsWith(MOTD_PREFIX)) {
            String rest = trimmed.substring(MOTD_PREFIX.length());
            int separator = rest.indexOf(':');
            String name = separator < 0 ? rest : rest.substring(0, separator);
            String text = separator < 0 ? "" : rest.substring(separator + 1);
            try {
                return new Parsed(GameState.valueOf(name.toUpperCase(Locale.ROOT)), text);
            } catch (IllegalArgumentException e) {
                return new Parsed(GameState.UNKNOWN, text);
            }
        }
        return new Parsed(parse(trimmed, DEFAULT_MAPPING), "");
    }

    public static String motd(GameState state, String text) {
        return MOTD_PREFIX + state.name() + ":" + (text == null ? "" : text);
    }

    private static Map<GameState, List<String>> defaults() {
        Map<GameState, List<String>> mapping = new LinkedHashMap<>();
        mapping.put(GameState.RESTARTING, List.of("restarting", "neustart", "resetting"));
        mapping.put(GameState.ENDING, List.of("ending", "ended", "finished", "endphase", "beendet"));
        mapping.put(GameState.STARTING, List.of("starting", "countdown", "startet"));
        mapping.put(GameState.RUNNING, List.of("ingame", "in-game", "in game", "playing", "running", "läuft"));
        mapping.put(GameState.WAITING, List.of("waiting", "wartet", "warten"));
        return Collections.unmodifiableMap(mapping);
    }

    public record Parsed(GameState state, String text) {
    }
}
