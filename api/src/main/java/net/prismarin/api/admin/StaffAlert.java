package net.prismarin.api.admin;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record StaffAlert(String source, String permission, NoticeText text) {

    public static final String DEFAULT_PERMISSION = "prismarin.staff.alerts";
    public static final Pattern PERMISSION = Pattern.compile("[a-z0-9_.-]{1,96}");
    public static final int MAX_SOURCE = 32;

    public StaffAlert {
        source = source == null ? "" : source.trim();
        if (source.isEmpty() || source.length() > MAX_SOURCE) {
            throw new IllegalArgumentException("an alert source needs 1 to " + MAX_SOURCE + " characters");
        }
        permission = permission == null || permission.isBlank() ? DEFAULT_PERMISSION
                : permission.trim().toLowerCase(Locale.ROOT);
        if (!PERMISSION.matcher(permission).matches()) {
            throw new IllegalArgumentException("permission '" + permission + "' must match " + PERMISSION.pattern());
        }
        Objects.requireNonNull(text, "text");
    }

    public static StaffAlert of(String source, NoticeText text) {
        return new StaffAlert(source, DEFAULT_PERMISSION, text);
    }

    public static StaffAlert of(String source, String template) {
        return of(source, NoticeText.of(template));
    }

    public StaffAlert permission(String node) {
        return new StaffAlert(source, node, text);
    }
}
