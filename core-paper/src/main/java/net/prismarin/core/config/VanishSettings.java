package net.prismarin.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record VanishSettings(boolean silentContainers, boolean silentBlocks) {

    public static VanishSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = loader.load();
            ConfigurationNode vanish = root.node("vanish");
            if (vanish.node("silent-containers").virtual() || vanish.node("silent-blocks").virtual()) {
                vanish.node("silent-containers").set(vanish.node("silent-containers").getBoolean(true));
                vanish.node("silent-blocks").set(vanish.node("silent-blocks").getBoolean(true));
                loader.save(root);
            }
            return new VanishSettings(vanish.node("silent-containers").getBoolean(true),
                    vanish.node("silent-blocks").getBoolean(true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
