package net.prismarin.velocity.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ProxyConfigCodec {

    private static final String RESOURCE_NAME = "proxy.yml";

    private ProxyConfigCodec() {
        throw new AssertionError();
    }

    public static ProxyConfig defaults() {
        try (InputStream resource = ProxyConfigCodec.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (resource == null) {
                throw new IllegalStateException("bundled " + RESOURCE_NAME + " is missing from the jar");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            return fromNode(root, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ProxyConfig load(Path file) {
        try {
            ProxyConfig defaults = defaults();
            if (Files.notExists(file)) {
                save(file, defaults);
                return defaults;
            }
            ConfigurationNode root = loaderFor(file).load();
            return fromNode(root, defaults);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void save(Path file, ProxyConfig config) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = loaderFor(file);
            CommentedConfigurationNode root = loader.createNode();
            root.node("motd", "line1").set(config.motdLine1());
            root.node("motd", "line2").set(config.motdLine2());
            root.node("maintenance", "enabled").set(config.maintenance());
            root.node("maintenance", "motd", "line1").set(config.maintenanceMotdLine1());
            root.node("maintenance", "motd", "line2").set(config.maintenanceMotdLine2());
            root.node("maintenance", "version-name").set(config.maintenanceVersionName());
            root.node("max-players").set(config.maxPlayers());
            root.node("lobby-server").set(config.lobbyServer());
            loader.save(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ProxyConfig fromNode(ConfigurationNode root, ProxyConfig defaults) {
        return new ProxyConfig(
                root.node("motd", "line1").getString(defaults == null ? "" : defaults.motdLine1()),
                root.node("motd", "line2").getString(defaults == null ? "" : defaults.motdLine2()),
                root.node("maintenance", "motd", "line1")
                        .getString(defaults == null ? "" : defaults.maintenanceMotdLine1()),
                root.node("maintenance", "motd", "line2")
                        .getString(defaults == null ? "" : defaults.maintenanceMotdLine2()),
                root.node("maintenance", "version-name")
                        .getString(defaults == null ? "" : defaults.maintenanceVersionName()),
                root.node("maintenance", "enabled").getBoolean(defaults != null && defaults.maintenance()),
                root.node("max-players").getInt(defaults == null ? 0 : defaults.maxPlayers()),
                root.node("lobby-server").getString(defaults == null ? "" : defaults.lobbyServer())
        );
    }

    private static YamlConfigurationLoader loaderFor(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
