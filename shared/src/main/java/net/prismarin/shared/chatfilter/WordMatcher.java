package net.prismarin.shared.chatfilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class WordMatcher {

    private static final int MAX_JOINED = 24;

    enum Kind {
        WORD,
        PREFIX,
        SUFFIX,
        CONTAINS
    }

    record Token(int start, int end) {
    }

    private record Entry(String source, Kind kind, Pattern pattern, int length) {

        boolean matches(String canonical) {
            if (canonical.length() < length) {
                return false;
            }
            Matcher matcher = pattern.matcher(canonical);
            return switch (kind) {
                case WORD -> matcher.matches();
                case PREFIX -> matcher.lookingAt();
                case SUFFIX, CONTAINS -> matcher.find();
            };
        }
    }

    private record Phrase(String source, List<Pattern> words) {
    }

    private final List<Entry> entries;
    private final List<Phrase> phrases;
    private final List<Pattern> patterns;
    private final Set<String> allowed;

    private WordMatcher(List<Entry> entries, List<Phrase> phrases, List<Pattern> patterns, Set<String> allowed) {
        this.entries = List.copyOf(entries);
        this.phrases = List.copyOf(phrases);
        this.patterns = List.copyOf(patterns);
        this.allowed = Set.copyOf(allowed);
    }

    public static WordMatcher compile(List<String> words, List<String> regexes, List<String> allowed,
                                      List<String> problems) {
        List<Entry> entries = new ArrayList<>();
        List<Phrase> phrases = new ArrayList<>();
        for (String raw : words) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean leading = trimmed.startsWith("*");
            boolean trailing = trimmed.length() > 1 && trimmed.endsWith("*");
            String core = trimmed.substring(leading ? 1 : 0, trailing ? trimmed.length() - 1 : trimmed.length()).trim();
            List<String> parts = TextNormalizer.words(core).stream().map(TextNormalizer::canonical).toList();
            if (parts.isEmpty() || parts.stream().anyMatch(String::isEmpty)) {
                problems.add("words.list: '" + raw + "' has no letters and is skipped");
                continue;
            }
            if (parts.size() > 1) {
                phrases.add(new Phrase(trimmed, parts.stream().map(part -> pattern(part, Kind.WORD)).toList()));
                entries.add(entry(trimmed, String.join("", parts), Kind.WORD));
                continue;
            }
            Kind kind = leading && trailing ? Kind.CONTAINS : leading ? Kind.SUFFIX : trailing ? Kind.PREFIX : Kind.WORD;
            entries.add(entry(trimmed, parts.getFirst(), kind));
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : regexes) {
            if (regex == null || regex.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException e) {
                problems.add("words.patterns: '" + regex + "' is no valid regular expression: " + e.getDescription());
            }
        }
        Set<String> exempt = new HashSet<>();
        for (String word : allowed) {
            String canonical = word == null ? "" : TextNormalizer.canonical(word);
            if (!canonical.isEmpty()) {
                exempt.add(canonical);
            }
        }
        return new WordMatcher(entries, phrases, patterns, exempt);
    }

    public boolean empty() {
        return entries.isEmpty() && patterns.isEmpty();
    }

    public List<Span> find(String text) {
        List<Span> spans = new ArrayList<>();
        List<Token> spaced = split(text, Character::isWhitespace);
        List<Token> parts = split(text, c -> Character.isWhitespace(c)
                || !Character.isLetterOrDigit(c) && !TextNormalizer.leet((char) c));
        Set<Token> seen = new HashSet<>();
        for (Token token : spaced) {
            if (seen.add(token)) {
                single(text, token, spans);
            }
        }
        for (Token token : parts) {
            if (seen.add(token)) {
                single(text, token, spans);
            }
        }
        joined(text, spaced, spans);
        phrases(text, parts, spans);
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    spans.add(new Span(matcher.start(), matcher.end(), pattern.pattern()));
                }
            }
        }
        return spans;
    }

    private void single(String text, Token token, List<Span> spans) {
        int[] inner = TextNormalizer.trimmed(text, token.start(), token.end());
        String innerCanonical = inner[1] > inner[0] ? TextNormalizer.canonical(text.substring(inner[0], inner[1])) : "";
        String fullCanonical = TextNormalizer.canonical(text.substring(token.start(), token.end()));
        if (!innerCanonical.isEmpty() && !allowed.contains(innerCanonical)) {
            Entry entry = matching(innerCanonical);
            if (entry != null) {
                spans.add(new Span(inner[0], inner[1], entry.source()));
                return;
            }
        }
        if (!fullCanonical.isEmpty() && !fullCanonical.equals(innerCanonical) && !allowed.contains(fullCanonical)) {
            Entry entry = matching(fullCanonical);
            if (entry != null) {
                spans.add(new Span(token.start(), token.end(), entry.source()));
            }
        }
    }

    private void joined(String text, List<Token> spaced, List<Span> spans) {
        int index = 0;
        while (index < spaced.size()) {
            int end = index;
            while (end < spaced.size() && end - index < MAX_JOINED
                    && TextNormalizer.canonical(slice(text, spaced.get(end))).length() == 1) {
                end++;
            }
            if (end - index >= 2) {
                letters(text, spaced.subList(index, end), spans);
                index = end;
            } else {
                index++;
            }
        }
    }

    private void letters(String text, List<Token> run, List<Span> spans) {
        for (int from = 0; from < run.size(); from++) {
            StringBuilder word = new StringBuilder();
            for (int to = from; to < run.size(); to++) {
                word.append(TextNormalizer.canonical(slice(text, run.get(to))));
                if (to == from || allowed.contains(word.toString())) {
                    continue;
                }
                Entry entry = matching(word.toString());
                if (entry != null) {
                    spans.add(new Span(run.get(from).start(), run.get(to).end(), entry.source()));
                }
            }
        }
    }

    private void phrases(String text, List<Token> parts, List<Span> spans) {
        if (phrases.isEmpty()) {
            return;
        }
        List<String> words = new ArrayList<>(parts.size());
        for (Token token : parts) {
            int[] inner = TextNormalizer.trimmed(text, token.start(), token.end());
            words.add(inner[1] > inner[0] ? TextNormalizer.canonical(text.substring(inner[0], inner[1])) : "");
        }
        for (Phrase phrase : phrases) {
            int size = phrase.words().size();
            for (int start = 0; start + size <= words.size(); start++) {
                boolean all = true;
                for (int offset = 0; offset < size && all; offset++) {
                    all = phrase.words().get(offset).matcher(words.get(start + offset)).matches();
                }
                if (all) {
                    spans.add(new Span(parts.get(start).start(), parts.get(start + size - 1).end(), phrase.source()));
                }
            }
        }
    }

    private Entry matching(String canonical) {
        for (Entry entry : entries) {
            if (entry.matches(canonical)) {
                return entry;
            }
        }
        return null;
    }

    private static Entry entry(String source, String canonical, Kind kind) {
        return new Entry(source, kind, pattern(canonical, kind), canonical.length());
    }

    static Pattern pattern(String canonical, Kind kind) {
        StringBuilder body = new StringBuilder();
        canonical.codePoints().forEach(codePoint -> body.append(Character.isLetter(codePoint)
                ? Character.toString(codePoint) : Pattern.quote(Character.toString(codePoint))).append('+'));
        String regex = kind == Kind.SUFFIX ? "(?:" + body + ")$" : body.toString();
        return Pattern.compile(regex);
    }

    private static String slice(String text, Token token) {
        return text.substring(token.start(), token.end());
    }

    static List<Token> split(String text, IntPredicate separator) {
        List<Token> tokens = new ArrayList<>();
        int start = -1;
        for (int index = 0; index < text.length(); index++) {
            boolean split = separator.test(text.charAt(index));
            if (split && start >= 0) {
                tokens.add(new Token(start, index));
                start = -1;
            } else if (!split && start < 0) {
                start = index;
            }
        }
        if (start >= 0) {
            tokens.add(new Token(start, text.length()));
        }
        return tokens;
    }
}
