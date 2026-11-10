package net.prismarin.core.essentials;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum GameModeChoice {
    SURVIVAL("0", "s", "su", "survival", "gms"),
    CREATIVE("1", "c", "creative", "gmc"),
    ADVENTURE("2", "a", "adventure", "gma"),
    SPECTATOR("3", "sp", "spectator", "gmsp");

    public static final List<String> SUGGESTIONS = List.of("survival", "creative", "adventure", "spectator",
            "0", "1", "2", "3");

    private final List<String> words;

    GameModeChoice(String... words) {
        this.words = List.of(words);
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<GameModeChoice> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String word = text.trim().toLowerCase(Locale.ROOT);
        for (GameModeChoice choice : values()) {
            if (choice.words.contains(word)) {
                return Optional.of(choice);
            }
        }
        return Optional.empty();
    }
}
