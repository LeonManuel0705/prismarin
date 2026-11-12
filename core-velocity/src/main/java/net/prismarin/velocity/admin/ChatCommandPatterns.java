package net.prismarin.velocity.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ChatCommandPatterns {

    record Pattern(String channel, List<String> literals, int skipped) {
    }

    public record Match(String channel, String label, String prefix, String text) {

        public String rebuild(String replaced) {
            return prefix + replaced;
        }
    }

    private final List<Pattern> patterns;

    private ChatCommandPatterns(List<Pattern> patterns) {
        List<Pattern> sorted = new ArrayList<>(patterns);
        sorted.sort(Comparator.comparingInt((Pattern pattern) -> pattern.literals().size()).reversed());
        this.patterns = List.copyOf(sorted);
    }

    public static ChatCommandPatterns parse(Map<String, List<String>> channels, List<String> problems) {
        List<Pattern> patterns = new ArrayList<>();
        channels.forEach((channel, entries) -> {
            for (String entry : entries) {
                parse(channel, entry).ifPresentOrElse(patterns::add, () -> problems.add("proxy-commands.channels."
                        + channel + ": '" + entry + "' needs a command and ends with <message>"));
            }
        });
        return new ChatCommandPatterns(patterns);
    }

    static Optional<Pattern> parse(String channel, String entry) {
        String[] tokens = entry == null ? new String[0] : entry.trim().split("\\s+");
        List<String> literals = new ArrayList<>();
        int arguments = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            boolean argument = token.startsWith("<") && token.endsWith(">");
            if (argument) {
                arguments++;
            } else if (arguments > 0) {
                return Optional.empty();
            } else {
                String literal = literals.isEmpty() && token.startsWith("/") ? token.substring(1) : token;
                if (literal.isEmpty()) {
                    return Optional.empty();
                }
                literals.add(literal.toLowerCase(Locale.ROOT));
            }
        }
        if (literals.isEmpty() || arguments == 0) {
            return Optional.empty();
        }
        return Optional.of(new Pattern(channel, List.copyOf(literals), arguments - 1));
    }

    public boolean empty() {
        return patterns.isEmpty();
    }

    public Optional<Match> match(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        String line = command.startsWith("/") ? command.substring(1) : command;
        String lead = command.startsWith("/") ? "/" : "";
        List<int[]> words = words(line);
        for (Pattern pattern : patterns) {
            int needed = pattern.literals().size() + pattern.skipped() + 1;
            if (words.size() < needed) {
                continue;
            }
            boolean literal = true;
            for (int index = 0; index < pattern.literals().size() && literal; index++) {
                int[] word = words.get(index);
                literal = line.substring(word[0], word[1]).equalsIgnoreCase(pattern.literals().get(index));
            }
            if (!literal) {
                continue;
            }
            int start = words.get(pattern.literals().size() + pattern.skipped())[0];
            return Optional.of(new Match(pattern.channel(), pattern.literals().getFirst(),
                    lead + line.substring(0, start), line.substring(start)));
        }
        return Optional.empty();
    }

    private static List<int[]> words(String line) {
        List<int[]> words = new ArrayList<>();
        int start = -1;
        for (int index = 0; index < line.length(); index++) {
            boolean space = Character.isWhitespace(line.charAt(index));
            if (space && start >= 0) {
                words.add(new int[]{start, index});
                start = -1;
            } else if (!space && start < 0) {
                start = index;
            }
        }
        if (start >= 0) {
            words.add(new int[]{start, line.length()});
        }
        return words;
    }
}
