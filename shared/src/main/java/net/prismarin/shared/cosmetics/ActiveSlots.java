package net.prismarin.shared.cosmetics;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.cosmetics.CosmeticSlot;

public final class ActiveSlots {

    private final String server;
    private final String group;
    private volatile List<String> defaults;
    private final Map<CosmeticSlot, Boolean> overrides = new ConcurrentHashMap<>();

    public ActiveSlots(String server, String group, List<String> defaults) {
        this.server = normalize(server);
        this.group = normalize(group);
        this.defaults = clean(defaults);
    }

    public void defaults(List<String> groups) {
        defaults = clean(groups);
    }

    public boolean active(CosmeticCategory category) {
        Objects.requireNonNull(category, "category");
        if (!category.enabled()) {
            return false;
        }
        Boolean override = overrides.get(category.slot());
        if (override != null) {
            return override;
        }
        return matches(category.groups().isEmpty() ? defaults : category.groups(), server, group);
    }

    public void set(CosmeticSlot slot, boolean active) {
        overrides.put(Objects.requireNonNull(slot, "slot"), active);
    }

    public void reset(CosmeticSlot slot) {
        overrides.remove(Objects.requireNonNull(slot, "slot"));
    }

    public Optional<Boolean> override(CosmeticSlot slot) {
        return Optional.ofNullable(overrides.get(slot));
    }

    public String group() {
        return group;
    }

    public static boolean matches(List<String> groups, String server, String group) {
        String serverName = normalize(server);
        String groupName = normalize(group);
        for (String entry : groups) {
            String value = normalize(entry);
            if (value.equals(CosmeticCategory.EVERYWHERE) || value.equals(groupName) || value.equals(serverName)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> clean(List<String> groups) {
        return groups == null ? List.of() : groups.stream().map(ActiveSlots::normalize)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
