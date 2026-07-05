package net.prismarin.velocity.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.prismarin.api.brand.Brand;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ProxyConfigCodec {

    private static final String DEFAULT_MOTD_LINE1 = Brand.gradient("<b>" + Brand.NAME + "</b>");
    private static final String DEFAULT_MOTD_LINE2 =
            "<gray>Lobby · Creative · Minigame</gray> <dark_gray>—</dark_gray> <gray>1.8 bis 1.21</gray>";
    private static final String DEFAULT_MAINTENANCE_LINE1 =
            Brand.gradient("<b>" + Brand.NAME + "</b>")
                    + " <dark_gray>—</dark_gray> <color:" + Brand.GOLD + "><b>Wartung</b></color>";
    private static final String DEFAULT_MAINTENANCE_LINE2 =
            "<gray>Wir schrauben gerade am Netzwerk und sind bald zurück.</gray>";
    private static final String DEFAULT_MAINTENANCE_VERSION_NAME = "Wartung";
    private static final int DEFAULT_MAX_PLAYERS = 200;
    private static final String DEFAULT_LOBBY_SERVER = "lobby";

    private ProxyConfigCodec() {
        throw new AssertionError();
    }

    public static ProxyConfig defaults() {
        return new ProxyConfig(DEFAULT_MOTD_LINE1, DEFAULT_MOTD_LINE2, DEFAULT_MAINTENANCE_LINE1,
                DEFAULT_MAINTENANCE_LINE2, DEFAULT_MAINTENANCE_VERSION_NAME, false, DEFAULT_MAX_PLAYERS,
                DEFAULT_LOBBY_SERVER);
    }

    public static ProxyConfig load(Path file) {
        try {
            if (Files.notExists(file)) {
                ProxyConfig defaults = defaults();
                save(file, defaults);
                return defaults;
            }
            ConfigurationNode root = loaderFor(file).load();
            return new ProxyConfig(
                    root.node("motd", "line1").getString(DEFAULT_MOTD_LINE1),
                    root.node("motd", "line2").getString(DEFAULT_MOTD_LINE2),
                    root.node("maintenance", "motd", "line1").getString(DEFAULT_MAINTENANCE_LINE1),
                    root.node("maintenance", "motd", "line2").getString(DEFAULT_MAINTENANCE_LINE2),
                    root.node("maintenance", "version-name").getString(DEFAULT_MAINTENANCE_VERSION_NAME),
                    root.node("maintenance", "enabled").getBoolean(false),
                    root.node("max-players").getInt(DEFAULT_MAX_PLAYERS),
                    root.node("lobby-server").getString(DEFAULT_LOBBY_SERVER)
            );
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

    private static YamlConfigurationLoader loaderFor(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
