package net.prismarin.core.staff;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record StaffModeSettings(boolean nick, boolean vanish) {

    public static StaffModeSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            ConfigurationNode section = root.node("staff-mode");
            boolean changed = false;
            if (section.node("nick").virtual()) {
                section.node("nick").set(true);
                changed = true;
            }
            if (section.node("vanish").virtual()) {
                section.node("vanish").set(false);
                changed = true;
            }
            if (changed) {
                loader.save(root);
            }
            return new StaffModeSettings(section.node("nick").getBoolean(true), section.node("vanish").getBoolean(false));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
