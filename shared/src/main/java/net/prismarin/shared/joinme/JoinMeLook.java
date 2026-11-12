package net.prismarin.shared.joinme;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.prismarin.shared.skin.FaceRows;

public record JoinMeLook(String texture, Map<Integer, String> lines) {

    public static final int MAX_TEXTURE = 8_192;
    public static final int MAX_LINE = 256;
    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/=\\r\\n]*");
    public static final JoinMeLook NONE = new JoinMeLook("", Map.of());

    public JoinMeLook {
        texture = texture == null || texture.length() > MAX_TEXTURE || !BASE64.matcher(texture).matches() ? ""
                : texture.strip();
        lines = cleanLines(lines);
    }

    public static Map<Integer, String> cleanLines(Map<Integer, String> values) {
        Map<Integer, String> clean = new TreeMap<>();
        if (values != null) {
            values.forEach((row, text) -> {
                if (row != null && row >= 1 && row <= FaceRows.MAX_ROWS && text != null) {
                    clean.put(row, text.length() > MAX_LINE ? text.substring(0, MAX_LINE) : text);
                }
            });
        }
        return Collections.unmodifiableMap(clean);
    }

    public JoinMeLook withLines(Map<Integer, String> value) {
        return new JoinMeLook(texture, value);
    }

    public boolean hasTexture() {
        return !texture.isEmpty();
    }
}
