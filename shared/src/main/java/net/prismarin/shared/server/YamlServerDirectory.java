package net.prismarin.shared.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class YamlServerDirectory implements ServerDirectory {

    private static final Set<String> RESERVED_KEYS = Set.of("display-name", "host", "port", "group", "category");

    private final List<NetworkServer> servers;
    private final List<ServerGroup> groups;

    public YamlServerDirectory(Path file) {
        try {
            seedIfAbsent(file);
            this.servers = load(file);
            this.groups = loadGroups(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<NetworkServer> servers() {
        return servers;
    }

    @Override
    public List<ServerGroup> groups() {
        return groups;
    }

    @Override
    public Optional<NetworkServer> byName(String name) {
        return servers.stream().filter(server -> server.name().equalsIgnoreCase(name)).findFirst();
    }

    private static void seedIfAbsent(Path file) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream resource = YamlServerDirectory.class.getClassLoader().getResourceAsStream("servers.yml")) {
            if (resource != null) {
                Files.copy(resource, file);
            }
        }
    }

    private static List<ServerGroup> loadGroups(Path file) throws IOException {
        if (Files.notExists(file)) {
            return List.of();
        }
        ConfigurationNode root = YamlConfigurationLoader.builder().path(file).build().load();
        List<ServerGroup> loaded = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("groups").childrenMap().entrySet()) {
            String name = entry.getKey().toString();
            ConfigurationNode node = entry.getValue();
            Map<String, String> properties = new HashMap<>();
            node.childrenMap().forEach((key, child) -> {
                if (!"display-name".equals(key.toString()) && child.childrenMap().isEmpty() && child.getString() != null) {
                    properties.put(key.toString(), child.getString());
                }
            });
            loaded.add(new ServerGroup(name, node.node("display-name").getString(name), properties));
        }
        return List.copyOf(loaded);
    }

    private static String groupOf(ConfigurationNode node) {
        String group = node.node("group").getString();
        if (group != null && !group.isBlank()) {
            return group;
        }
        String legacyCategory = node.node("category").getString();
        if (legacyCategory == null || legacyCategory.isBlank()) {
            return NetworkServer.GROUP_DEFAULT;
        }
        return switch (legacyCategory.toUpperCase(Locale.ROOT)) {
            case "LOBBY" -> NetworkServer.GROUP_LOBBY;
            case "BUILD" -> "build";
            case "GAME" -> "game";
            default -> legacyCategory;
        };
    }

    private static List<NetworkServer> load(Path file) throws IOException {
        if (Files.notExists(file)) {
            return List.of();
        }
        ConfigurationNode root = YamlConfigurationLoader.builder().path(file).build().load();
        List<NetworkServer> loaded = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("servers").childrenMap().entrySet()) {
            String name = entry.getKey().toString();
            ConfigurationNode node = entry.getValue();
            Map<String, String> properties = new HashMap<>();
            node.childrenMap().forEach((key, child) -> {
                String property = key.toString();
                if (!RESERVED_KEYS.contains(property) && child.childrenMap().isEmpty() && child.getString() != null) {
                    properties.put(property, child.getString());
                }
            });
            loaded.add(new NetworkServer(
                    name,
                    node.node("display-name").getString(name),
                    node.node("host").getString("127.0.0.1"),
                    node.node("port").getInt(25565),
                    groupOf(node),
                    properties
            ));
        }
        return List.copyOf(loaded);
    }
}
