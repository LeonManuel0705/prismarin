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

    public static final int DEFAULT_PINGTEST_SECONDS = 30;

    public static int pingTestSeconds(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            int seconds = root.node("essentials", "pingtest-seconds").getInt(DEFAULT_PINGTEST_SECONDS);
            return Math.max(5, Math.min(300, seconds));
        } catch (IOException e) {
            return DEFAULT_PINGTEST_SECONDS;
        }
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
            ConfigurationNode pingTest = root.node("essentials", "pingtest-seconds");
            if (pingTest.virtual()) {
                pingTest.set(DEFAULT_PINGTEST_SECONDS);
                changed = true;
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
