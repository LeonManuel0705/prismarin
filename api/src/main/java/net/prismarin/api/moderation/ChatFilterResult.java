package net.prismarin.api.moderation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ChatFilterResult(String original, String text, boolean blocked, List<ChatFilterMatch> matches) {

    public ChatFilterResult {
        Objects.requireNonNull(original, "original");
        text = text == null ? original : text;
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public static ChatFilterResult untouched(String text) {
        return new ChatFilterResult(text, text, false, List.of());
    }

    public boolean clean() {
        return matches.isEmpty();
    }

    public boolean changed() {
        return !blocked && !text.equals(original);
    }

    public boolean warned() {
        return matches.stream().anyMatch(match -> match.action() == ChatFilterAction.WARN);
    }

    public Optional<ChatFilterMatch> blocking() {
        return matches.stream().filter(match -> match.action() == ChatFilterAction.BLOCK).findFirst();
    }

    public List<String> rules() {
        return matches.stream().map(ChatFilterMatch::rule).distinct().toList();
    }
}
