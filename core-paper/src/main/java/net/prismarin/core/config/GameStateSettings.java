package net.prismarin.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.server.GameState;
import net.prismarin.shared.status.GameStateParser;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record GameStateSettings(String placeholder, boolean writeMotd, Map<GameState, List<String>> mapping) {

    public GameStateSettings {
        mapping = Map.copyOf(mapping);
    }

    public static GameStateSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = loader.load();
            ConfigurationNode section = root.node("game-state");
            boolean changed = false;
            if (section.node("placeholder").virtual()) {
                section.node("placeholder").set("");
                changed = true;
            }
            if (section.node("write-motd").virtual()) {
                section.node("write-motd").set(true);
                changed = true;
            }
            for (Map.Entry<GameState, List<String>> entry : GameStateParser.DEFAULT_MAPPING.entrySet()) {
                ConfigurationNode words = section.node("mapping", entry.getKey().name().toLowerCase(Locale.ROOT));
                if (words.virtual()) {
                    words.setList(String.class, entry.getValue());
                    changed = true;
                }
            }
            if (changed) {
                loader.save(root);
            }
            Map<GameState, List<String>> mapping = new LinkedHashMap<>();
            for (GameState state : GameStateParser.DEFAULT_MAPPING.keySet()) {
                mapping.put(state, section.node("mapping", state.name().toLowerCase(Locale.ROOT))
                        .getList(String.class, List.of()));
            }
            return new GameStateSettings(section.node("placeholder").getString(""),
                    section.node("write-motd").getBoolean(true), mapping);
        } catch (SerializationException e) {
            throw new IllegalStateException("Invalid game-state section in " + file.getFileName(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
