package net.prismarin.api.server;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ServerGroup(String name, String displayName, Map<String, String> properties) {

    public ServerGroup {
        name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(displayName, "displayName");
        properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    }

    public Optional<String> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }
}
