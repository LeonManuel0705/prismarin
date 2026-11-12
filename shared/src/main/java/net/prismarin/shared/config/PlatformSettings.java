package net.prismarin.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record PlatformSettings(boolean metrics, boolean updateCheck, int updateIntervalHours, boolean notifyStaff) {

    public static final PlatformSettings DEFAULT = new PlatformSettings(true, true, 6, true);

    public PlatformSettings {
        updateIntervalHours = Math.max(1, updateIntervalHours);
    }

    public static PlatformSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = loader.load();
            boolean changed = fill(root.node("metrics", "enabled"), DEFAULT.metrics());
            changed |= fill(root.node("update-check", "enabled"), DEFAULT.updateCheck());
            changed |= fill(root.node("update-check", "interval-hours"), DEFAULT.updateIntervalHours());
            changed |= fill(root.node("update-check", "notify-staff"), DEFAULT.notifyStaff());
            if (changed) {
                loader.save(root);
            }
            return new PlatformSettings(root.node("metrics", "enabled").getBoolean(DEFAULT.metrics()),
                    root.node("update-check", "enabled").getBoolean(DEFAULT.updateCheck()),
                    root.node("update-check", "interval-hours").getInt(DEFAULT.updateIntervalHours()),
                    root.node("update-check", "notify-staff").getBoolean(DEFAULT.notifyStaff()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean fill(ConfigurationNode node, Object value) throws IOException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
