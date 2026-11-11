package net.prismarin.api.brand;

import java.util.Objects;
import java.util.regex.Pattern;

public record Branding(String name, String primaryColor, String accentColor, String prefix, String successPrefix,
                       String errorPrefix) {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public static final String DEFAULT_SUCCESS_PREFIX = "%brand_prefix%<color:#86EFAC>";
    public static final String DEFAULT_ERROR_PREFIX = "%brand_prefix%<color:#FCA5A5>";

    public static final Branding DEFAULT = new Branding(
            "PRISMARIN", "#22D3EE", "#2DD4BF", "%brand% <dark_gray>|</dark_gray> <gray>");

    public Branding(String name, String primaryColor, String accentColor, String prefix) {
        this(name, primaryColor, accentColor, prefix, DEFAULT_SUCCESS_PREFIX, DEFAULT_ERROR_PREFIX);
    }

    public Branding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(prefix, "prefix");
        successPrefix = successPrefix == null || successPrefix.isBlank() ? DEFAULT_SUCCESS_PREFIX : successPrefix;
        errorPrefix = errorPrefix == null || errorPrefix.isBlank() ? DEFAULT_ERROR_PREFIX : errorPrefix;
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
