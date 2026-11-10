package net.prismarin.core.essentials;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record EssentialsSettings(Map<EssentialsFeature, Boolean> commands) {

    public EssentialsSettings {
        commands = Map.copyOf(commands);
    }

    public boolean enabled(EssentialsFeature feature) {
        return commands.getOrDefault(feature, true);
    }

    public static EssentialsSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            ConfigurationNode section = root.node("essentials", "commands");
            Map<EssentialsFeature, Boolean> commands = new EnumMap<>(EssentialsFeature.class);
            boolean changed = false;
            for (EssentialsFeature feature : EssentialsFeature.values()) {
                ConfigurationNode node = section.node(feature.key());
                if (node.virtual()) {
                    node.set(true);
                    changed = true;
                }
                commands.put(feature, node.getBoolean(true));
            }
            if (changed) {
                loader.save(root);
            }
            return new EssentialsSettings(commands);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
