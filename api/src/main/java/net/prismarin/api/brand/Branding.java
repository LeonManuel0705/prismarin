package net.prismarin.api.brand;

import java.util.Objects;
import java.util.regex.Pattern;

public record Branding(String name, String primaryColor, String accentColor, String prefix) {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public static final Branding DEFAULT = new Branding(
            "PRISMARIN", "#22D3EE", "#2DD4BF", "%brand% <dark_gray>|</dark_gray> <gray>");

    public Branding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(prefix, "prefix");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        requireHex(primaryColor, "primaryColor");
        requireHex(accentColor, "accentColor");
    }

    public String gradient(String text) {
        return "<gradient:" + primaryColor + ":" + accentColor + ">" + text + "</gradient>";
    }

    public String title() {
        return gradient("<b>" + name + "</b>");
    }

    private static void requireHex(String value, String field) {
        if (value == null || !HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a #RRGGBB hex color, got " + value);
        }
    }
}
