package net.prismarin.core.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record VanishSettings(boolean silentContainers, boolean silentBlocks, boolean flight, boolean invincible,
                             boolean noHunger, boolean mobsIgnore, boolean inspectInventories) {

    public static final VanishSettings DEFAULT = new VanishSettings(true, true, true, true, true, true, true);

    public static VanishSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = loader.load();
            ConfigurationNode vanish = root.node("vanish");
            Map<String, Boolean> values = new LinkedHashMap<>();
            values.put("silent-containers", true);
            values.put("silent-blocks", true);
            values.put("flight", true);
            values.put("invincible", true);
            values.put("no-hunger", true);
            values.put("mobs-ignore", true);
            values.put("inspect-inventories", true);
            boolean changed = false;
            for (Map.Entry<String, Boolean> entry : values.entrySet()) {
                changed |= fill(vanish.node(entry.getKey()), entry.getValue());
                entry.setValue(vanish.node(entry.getKey()).getBoolean(entry.getValue()));
            }
            if (changed) {
                loader.save(root);
            }
            return new VanishSettings(values.get("silent-containers"), values.get("silent-blocks"),
                    values.get("flight"), values.get("invincible"), values.get("no-hunger"),
                    values.get("mobs-ignore"), values.get("inspect-inventories"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean fill(ConfigurationNode node, boolean value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
