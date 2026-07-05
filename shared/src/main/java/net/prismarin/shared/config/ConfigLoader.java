package net.prismarin.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ConfigLoader {

    private static final String DEFAULT_SERVER_ID = "unknown";
    private static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_MONGO_DATABASE = "prismarin";
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_LOCALE = "de";

    private ConfigLoader() {
        throw new AssertionError();
    }

    public static CoreConfig load(Path file) {
        try {
            if (Files.notExists(file)) {
                writeDefault(file);
            }
            ConfigurationNode root = loaderFor(file).load();
            return new CoreConfig(
                    root.node("server-id").getString(DEFAULT_SERVER_ID),
                    CoreConfig.StorageMode.valueOf(
                            root.node("storage", "mode").getString(CoreConfig.StorageMode.LOCAL.name())
                                    .toUpperCase(Locale.ROOT)),
                    root.node("storage", "mongo", "uri").getString(DEFAULT_MONGO_URI),
                    root.node("storage", "mongo", "database").getString(DEFAULT_MONGO_DATABASE),
                    root.node("redis", "enabled").getBoolean(false),
                    root.node("redis", "host").getString(DEFAULT_REDIS_HOST),
                    root.node("redis", "port").getInt(DEFAULT_REDIS_PORT),
                    root.node("redis", "password").getString(""),
                    root.node("locale", "default").getString(DEFAULT_LOCALE)
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeDefault(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        YamlConfigurationLoader loader = loaderFor(file);
        CommentedConfigurationNode root = loader.createNode();
        root.node("server-id").set(DEFAULT_SERVER_ID);
        root.node("storage", "mode").set(CoreConfig.StorageMode.LOCAL.name());
        root.node("storage", "mongo", "uri").set(DEFAULT_MONGO_URI);
        root.node("storage", "mongo", "database").set(DEFAULT_MONGO_DATABASE);
        root.node("redis", "enabled").set(false);
        root.node("redis", "host").set(DEFAULT_REDIS_HOST);
        root.node("redis", "port").set(DEFAULT_REDIS_PORT);
        root.node("redis", "password").set("");
        root.node("locale", "default").set(DEFAULT_LOCALE);
        loader.save(root);
    }

    private static YamlConfigurationLoader loaderFor(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
