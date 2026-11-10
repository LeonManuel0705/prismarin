package net.prismarin.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public final class YamlComments {

    public static final List<String> TEXT_HELP = List.of(
            "Every text accepts MiniMessage tags such as <red>, <bold> or <gradient:#22D3EE:#2DD4BF>,",
            "classic colour codes such as &a, &l or &r, and hex colours written as &#22D3EE or <#22D3EE>.",
            "Placeholders are written as %name%, for example %player%, %brand% or %brand_prefix%.");

    private static final String COMMENT = "# ";

    private YamlComments() {
        throw new AssertionError();
    }

    public static void apply(Path file, List<String> header, Map<String, List<String>> comments) {
        try {
            if (!Files.exists(file)) {
                return;
            }
            List<String> original = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> updated = annotate(original, header, comments);
            if (!updated.equals(original)) {
                Files.write(file, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> annotate(List<String> lines, List<String> header, Map<String, List<String>> comments) {
        List<String> out = new ArrayList<>();
        int start = 0;
        if (!header.isEmpty()) {
            List<String> headerLines = header.stream().map(line -> line.isEmpty() ? "#" : COMMENT + line).toList();
            if (!startsWith(lines, headerLines)) {
                out.addAll(headerLines);
                out.add("");
            } else {
                out.addAll(headerLines);
                start = headerLines.size();
            }
        }
        Deque<Entry> stack = new ArrayDeque<>();
        for (int index = start; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ") || trimmed.equals("-")) {
                out.add(line);
                continue;
            }
            String key = keyOf(trimmed);
            if (key == null) {
                out.add(line);
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            while (!stack.isEmpty() && stack.peek().indent() >= indent) {
                stack.pop();
            }
            String path = stack.isEmpty() ? key : stack.peek().path() + "." + key;
            stack.push(new Entry(indent, path));
            List<String> comment = comments.get(path);
            if (comment != null && !comment.isEmpty()) {
                String pad = " ".repeat(indent);
                List<String> block = comment.stream()
                        .map(text -> text.isEmpty() ? pad + "#" : pad + COMMENT + text).toList();
                if (!endsWith(out, block)) {
                    if (indent == 0 && !out.isEmpty() && !out.getLast().isBlank()) {
                        out.add("");
                    }
                    out.addAll(block);
                }
            }
            out.add(line);
        }
        return out;
    }

    private static String keyOf(String trimmed) {
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            char quote = trimmed.charAt(0);
            int end = trimmed.indexOf(quote, 1);
            if (end < 0 || end + 1 >= trimmed.length() || trimmed.charAt(end + 1) != ':') {
                return null;
            }
            return trimmed.substring(1, end);
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        if (colon + 1 < trimmed.length() && trimmed.charAt(colon + 1) != ' ') {
            return null;
        }
        return trimmed.substring(0, colon);
    }

    private static boolean startsWith(List<String> lines, List<String> prefix) {
        if (lines.size() < prefix.size()) {
            return false;
        }
        return lines.subList(0, prefix.size()).equals(prefix);
    }

    private static boolean endsWith(List<String> lines, List<String> suffix) {
        if (lines.size() < suffix.size()) {
            return false;
        }
        return lines.subList(lines.size() - suffix.size(), lines.size()).equals(suffix);
    }

    private record Entry(int indent, String path) {
    }
}
