package net.prismarin.shared.skin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.prismarin.api.skin.ChatFace;

public final class FaceRows {

    public static final int MAX_ROWS = 16;
    public static final Pattern KEY = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)+");

    private FaceRows() {
        throw new AssertionError();
    }

    public static List<Component> compose(ChatFace face, String symbol, String gap, Map<Integer, Component> texts,
                                          boolean blankBefore, boolean blankAfter) {
        int last = face == null ? 0 : ChatFace.SIZE;
        for (int row : texts.keySet()) {
            if (row >= 1 && row <= MAX_ROWS) {
                last = Math.max(last, row);
            }
        }
        List<Component> lines = new ArrayList<>();
        if (blankBefore) {
            lines.add(Component.empty());
        }
        String spacing = gap == null ? "" : gap;
        for (int row = 1; row <= last; row++) {
            boolean faceRow = face != null && row <= ChatFace.SIZE;
            Component text = texts.get(row);
            Component line = faceRow ? face.line(row - 1, symbol) : Component.empty();
            if (text != null) {
                line = faceRow ? Component.text().append(line).append(Component.text(spacing)).append(text).build()
                        : text;
            }
            lines.add(line);
        }
        if (blankAfter) {
            lines.add(Component.empty());
        }
        return List.copyOf(lines);
    }

    public static Map<Integer, String> texts(Map<Integer, String> configured, Map<Integer, String> overrides) {
        Map<Integer, String> merged = new TreeMap<>();
        configured.forEach((row, value) -> {
            if (row >= 1 && row <= MAX_ROWS && value != null && !value.isBlank()) {
                merged.put(row, value);
            }
        });
        overrides.forEach((row, value) -> {
            if (row < 1 || row > MAX_ROWS) {
                return;
            }
            if (value == null || value.isBlank()) {
                merged.remove(row);
            } else {
                merged.put(row, value);
            }
        });
        return merged;
    }

    public static Component join(List<Component> lines) {
        return Component.join(JoinConfiguration.newlines(), lines);
    }
}
