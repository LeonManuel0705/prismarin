package net.prismarin.core.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class YamlServerDirectory implements ServerDirectory {

    private final List<NetworkServer> servers;

    public YamlServerDirectory(Path file) {
        try {
            seedIfAbsent(file);
            this.servers = load(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<NetworkServer> servers() {
        return servers;
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

    private static List<NetworkServer> load(Path file) throws IOException {
        ConfigurationNode root = YamlConfigurationLoader.builder().path(file).build().load();
        List<NetworkServer> loaded = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("servers").childrenMap().entrySet()) {
            String name = entry.getKey().toString();
            ConfigurationNode node = entry.getValue();
            loaded.add(new NetworkServer(
                    name,
                    node.node("display-name").getString(name),
                    node.node("host").getString("127.0.0.1"),
                    node.node("port").getInt(25565),
                    NetworkServer.Category.valueOf(
                            node.node("category").getString(NetworkServer.Category.GAME.name())
                                    .toUpperCase(Locale.ROOT))
            ));
        }
        return List.copyOf(loaded);
    }
}
