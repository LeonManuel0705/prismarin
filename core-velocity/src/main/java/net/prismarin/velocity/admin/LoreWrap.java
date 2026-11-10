package net.prismarin.velocity.admin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;

public final class LoreWrap {

    public static final int WIDTH = 34;

    private record Piece(String text, Style style) {
    }

    private LoreWrap() {
        throw new AssertionError();
    }

    public static List<Component> wrapAll(List<Component> lines, int width) {
        List<Component> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(wrap(line, width));
        }
        return List.copyOf(wrapped);
    }

    public static List<Component> wrap(Component line, int width) {
        int limit = Math.max(8, width);
        List<List<Piece>> lines = new ArrayList<>();
        List<Piece> current = new ArrayList<>();
        List<Piece> pending = new ArrayList<>();
        int length = 0;
        for (List<Piece> unit : units(tokens(line))) {
            String first = unit.getFirst().text();
            if (first.equals("\n")) {
                lines.add(current);
                current = new ArrayList<>();
                pending.clear();
                length = 0;
                continue;
            }
            if (first.isBlank()) {
                if (length > 0 || lines.isEmpty()) {
                    pending.addAll(unit);
                }
                continue;
            }
            int size = unit.stream().mapToInt(piece -> piece.text().length()).sum();
            int spaces = pending.stream().mapToInt(piece -> piece.text().length()).sum();
            if (length > 0 && length + spaces + size > limit) {
                lines.add(current);
                current = new ArrayList<>();
                pending.clear();
                length = 0;
                spaces = 0;
            }
            current.addAll(pending);
            length += spaces;
            pending.clear();
            for (Piece piece : unit) {
                String word = piece.text();
                while (length + word.length() > limit && size > limit) {
                    int cut = Math.max(1, limit - length);
                    if (cut >= word.length()) {
                        break;
                    }
                    current.add(new Piece(word.substring(0, cut), piece.style()));
                    lines.add(current);
                    current = new ArrayList<>();
                    length = 0;
                    word = word.substring(cut);
                    size -= cut;
                }
                current.add(new Piece(word, piece.style()));
                length += word.length();
            }
        }
        if (!current.isEmpty() || lines.isEmpty()) {
            lines.add(current);
        }
        return lines.stream().map(LoreWrap::join).toList();
    }

    private static List<List<Piece>> units(List<Piece> pieces) {
        List<List<Piece>> units = new ArrayList<>();
        List<Piece> word = new ArrayList<>();
        for (Piece piece : pieces) {
            boolean breaking = piece.text().isBlank() || piece.text().equals("\n");
            if (breaking) {
                if (!word.isEmpty()) {
                    units.add(word);
                    word = new ArrayList<>();
                }
                units.add(List.of(piece));
            } else {
                word.add(piece);
            }
        }
        if (!word.isEmpty()) {
            units.add(word);
        }
        return units;
    }

    private static List<Piece> tokens(Component line) {
        List<Piece> pieces = new ArrayList<>();
        Deque<Style> styles = new ArrayDeque<>();
        styles.push(Style.empty());
        ComponentFlattener.basic().flatten(line, new FlattenerListener() {
            @Override
            public void pushStyle(Style style) {
                styles.push(styles.peek().merge(style));
            }

            @Override
            public void component(String text) {
                split(text, styles.peek(), pieces);
            }

            @Override
            public void popStyle(Style style) {
                if (styles.size() > 1) {
                    styles.pop();
                }
            }
        });
        return pieces;
    }

    private static void split(String text, Style style, List<Piece> pieces) {
        StringBuilder run = new StringBuilder();
        Boolean blank = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                flush(run, style, pieces);
                pieces.add(new Piece("\n", style));
                blank = null;
                continue;
            }
            boolean space = c == ' ';
            if (blank != null && blank != space) {
                flush(run, style, pieces);
            }
            run.append(c);
            blank = space;
        }
        flush(run, style, pieces);
    }

    private static void flush(StringBuilder run, Style style, List<Piece> pieces) {
        if (!run.isEmpty()) {
            pieces.add(new Piece(run.toString(), style));
            run.setLength(0);
        }
    }

    private static Component join(List<Piece> pieces) {
        List<Piece> merged = new ArrayList<>();
        for (Piece piece : pieces) {
            if (!merged.isEmpty() && Objects.equals(merged.getLast().style(), piece.style())) {
                Piece last = merged.removeLast();
                merged.add(new Piece(last.text() + piece.text(), piece.style()));
            } else {
                merged.add(piece);
            }
        }
        TextComponent.Builder builder = Component.text();
        merged.forEach(piece -> builder.append(Component.text(piece.text(), piece.style())));
        return builder.build();
    }
}
