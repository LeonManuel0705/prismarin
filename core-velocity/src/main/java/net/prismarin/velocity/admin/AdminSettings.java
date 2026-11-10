package net.prismarin.velocity.admin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record AdminSettings(String discordWebhook, int reportCooldownSeconds, int alertIntervalSeconds,
                            double lowTps) {

    public static AdminSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            boolean changed = setIfAbsent(root.node("alerts", "discord-webhook"), "");
            changed |= setIfAbsent(root.node("alerts", "interval-seconds"), 15);
            changed |= setIfAbsent(root.node("alerts", "low-tps"), 15.0);
            changed |= setIfAbsent(root.node("reports", "cooldown-seconds"), 60);
            if (changed) {
                loader.save(root);
            }
            return new AdminSettings(
                    root.node("alerts", "discord-webhook").getString(""),
                    root.node("reports", "cooldown-seconds").getInt(60),
                    Math.max(5, root.node("alerts", "interval-seconds").getInt(15)),
                    root.node("alerts", "low-tps").getDouble(15.0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
