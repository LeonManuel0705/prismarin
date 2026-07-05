package net.prismarin.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record MenuSettings(String clickSound, float clickVolume, float clickPitch) {

    private static final String DEFAULT_SOUND = "minecraft:ui.button.click";
    private static final double DEFAULT_VOLUME = 0.6d;
    private static final double DEFAULT_PITCH = 1.2d;

    public static MenuSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = loader.load();
            ConfigurationNode menu = root.node("menu");
            if (menu.node("click-sound").virtual()
                    || menu.node("click-volume").virtual()
                    || menu.node("click-pitch").virtual()) {
                menu.node("click-sound").set(menu.node("click-sound").getString(DEFAULT_SOUND));
                menu.node("click-volume").set(menu.node("click-volume").getDouble(DEFAULT_VOLUME));
                menu.node("click-pitch").set(menu.node("click-pitch").getDouble(DEFAULT_PITCH));
                loader.save(root);
            }
            return new MenuSettings(
                    menu.node("click-sound").getString(DEFAULT_SOUND),
                    (float) menu.node("click-volume").getDouble(DEFAULT_VOLUME),
                    (float) menu.node("click-pitch").getDouble(DEFAULT_PITCH)
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
