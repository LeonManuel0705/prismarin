package net.prismarin.api.stats;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StatKeys {

    public static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9.-]{0,63}");
    public static final Pattern GAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");
    public static final String GENERAL = "general";

    private StatKeys() {
        throw new AssertionError();
    }

    public static boolean valid(String key) {
        return key != null && KEY.matcher(key.strip().toLowerCase(Locale.ROOT)).matches();
    }

    public static String key(String key) {
        if (!valid(key)) {
            throw new IllegalArgumentException("'" + key + "' is no stat key, use " + KEY.pattern());
        }
        return key.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean validGame(String game) {
        return game != null && GAME.matcher(game.strip().toLowerCase(Locale.ROOT)).matches();
    }

    public static String gameId(String game) {
        if (!validGame(game)) {
            throw new IllegalArgumentException("'" + game + "' is no game id, use " + GAME.pattern());
        }
        return game.strip().toLowerCase(Locale.ROOT);
    }

    public static String game(String key) {
        String normalized = key(key);
        int dot = normalized.indexOf('.');
        return dot <= 0 ? GENERAL : normalized.substring(0, dot);
    }

    public static String inGame(String game, String key) {
        String id = gameId(game);
        String normalized = key(key);
        return game(normalized).equals(id) ? normalized : key(id + "." + normalized);
    }

    public static String humanize(String key) {
        String normalized = key(key);
        int dot = normalized.indexOf('.');
        String words = words(dot < 0 ? normalized : normalized.substring(dot + 1));
        return words.isEmpty() ? normalized : words;
    }

    public static String gameName(String game) {
        String id = gameId(game);
        String words = words(id);
        return words.isEmpty() ? id : words;
    }

    private static String words(String text) {
        String spaced = text.replace('.', ' ').replace('-', ' ').strip().replaceAll(" +", " ");
        if (spaced.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
