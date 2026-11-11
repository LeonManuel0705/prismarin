package net.prismarin.api.admin;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record Broadcast(String scope, NoticeText text) {

    public static final String ALL = "all";

    public Broadcast {
        scope = scope(scope);
        Objects.requireNonNull(text, "text");
    }

    public static Broadcast all(NoticeText text) {
        return new Broadcast(ALL, text);
    }

    public static Broadcast server(String server, NoticeText text) {
        return new Broadcast(MaintenanceState.SERVER_PREFIX + server, text);
    }

    public static Broadcast group(String group, NoticeText text) {
        return new Broadcast(MaintenanceState.GROUP_PREFIX + group, text);
    }

    public boolean everyone() {
        return scope.equals(ALL);
    }

    public Optional<String> targetServer() {
        return named(MaintenanceState.SERVER_PREFIX);
    }

    public Optional<String> targetGroup() {
        return named(MaintenanceState.GROUP_PREFIX);
    }

    private Optional<String> named(String prefix) {
        return scope.startsWith(prefix) ? Optional.of(scope.substring(prefix.length())) : Optional.empty();
    }

    private static String scope(String scope) {
        String value = scope == null || scope.isBlank() ? ALL : scope.trim().toLowerCase(Locale.ROOT);
        if (value.equals(ALL)) {
            return ALL;
        }
        for (String prefix : new String[] {MaintenanceState.SERVER_PREFIX, MaintenanceState.GROUP_PREFIX}) {
            if (value.startsWith(prefix) && MaintenanceState.validName(value.substring(prefix.length()))) {
                return value;
            }
        }
        throw new IllegalArgumentException("scope '" + scope + "' must be all, server:<name> or group:<name>");
    }
}
