package net.prismarin.api.server;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record NetworkServer(String name, String displayName, String host, int port, String group,
                            Map<String, String> properties) {

    public static final String GROUP_LOBBY = "lobby";

    public static final String GROUP_DEFAULT = "default";

    public NetworkServer {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(host, "host");
        group = Objects.requireNonNull(group, "group").toLowerCase(Locale.ROOT);
        properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    }

    public Optional<String> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    public boolean inGroup(String group) {
        return this.group.equalsIgnoreCase(group);
    }
}
