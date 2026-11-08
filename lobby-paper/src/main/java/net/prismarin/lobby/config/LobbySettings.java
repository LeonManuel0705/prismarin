package net.prismarin.lobby.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record LobbySettings(Map<String, Boolean> features) {

    public static final String NAVIGATOR = "navigator";
    public static final String PROFILE_MENU = "profile-menu";
    public static final String PLAYER_HIDER = "player-hider";
    public static final String DOUBLE_JUMP = "double-jump";
    public static final String PROTECTION = "protection";
    public static final String SIDEBAR = "sidebar";
    public static final String JOIN_TITLE = "join-title";

    public static final List<String> ALL = List.of(NAVIGATOR, PROFILE_MENU, PLAYER_HIDER, DOUBLE_JUMP, PROTECTION,
            SIDEBAR, JOIN_TITLE);

    public LobbySettings {
        features = Map.copyOf(features);
    }

    public boolean enabled(String feature) {
        return features.getOrDefault(feature, true);
    }

    public static LobbySettings load(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            boolean changed = false;
            Map<String, Boolean> features = new LinkedHashMap<>();
            for (String feature : ALL) {
                CommentedConfigurationNode node = root.node("features", feature);
                if (node.virtual()) {
                    node.set(true);
                    changed = true;
                }
                features.put(feature, node.getBoolean(true));
            }
            if (changed) {
                loader.save(root);
            }
            return new LobbySettings(features);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
