package net.prismarin.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ConfigLoader {

    private static final String DEFAULT_SERVER_ID = "unknown";
    private static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_MONGO_DATABASE = "prismarin";
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_LOCALE = "de";
    private static final List<String> DEFAULT_CURRENCIES = List.of(Currency.COINS.id(), Currency.GEMS.id());

    private ConfigLoader() {
        throw new AssertionError();
    }

    public static CoreConfig load(Path file, Collection<String> modules) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = loaderFor(file);
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            if (fillDefaults(root, modules)) {
                loader.save(root);
            }
            return read(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static CoreConfig read(ConfigurationNode root) throws SerializationException {
        Branding fallback = Branding.DEFAULT;
        Branding branding = new Branding(
                root.node("branding", "name").getString(fallback.name()),
                root.node("branding", "primary-color").getString(fallback.primaryColor()),
                root.node("branding", "accent-color").getString(fallback.accentColor()),
                root.node("branding", "prefix").getString(fallback.prefix()));
        List<Currency> currencies = new ArrayList<>();
        for (String id : root.node("economy", "currencies").getList(String.class, DEFAULT_CURRENCIES)) {
            Currency currency = Currency.of(id);
            if (!currencies.contains(currency)) {
                currencies.add(currency);
            }
        }
        Currency primary = Currency.of(root.node("economy", "primary").getString(currencies.isEmpty()
                ? Currency.COINS.id() : currencies.getFirst().id()));
        Map<String, Boolean> modules = new LinkedHashMap<>();
        root.node("modules").childrenMap().forEach((key, node) -> modules.put(key.toString(), node.getBoolean(true)));
        Map<String, Boolean> systems = new LinkedHashMap<>();
        root.node("systems").childrenMap().forEach((key, node) -> systems.put(key.toString(), node.getBoolean(true)));
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
                root.node("locale", "default").getString(DEFAULT_LOCALE),
                branding,
                currencies,
                primary,
                modules,
                systems
        );
    }

    private static boolean fillDefaults(ConfigurationNode root, Collection<String> modules)
            throws SerializationException {
        Branding branding = Branding.DEFAULT;
        boolean changed = false;
        changed |= setIfAbsent(root.node("server-id"), DEFAULT_SERVER_ID);
        changed |= setIfAbsent(root.node("storage", "mode"), CoreConfig.StorageMode.LOCAL.name());
        changed |= setIfAbsent(root.node("storage", "mongo", "uri"), DEFAULT_MONGO_URI);
        changed |= setIfAbsent(root.node("storage", "mongo", "database"), DEFAULT_MONGO_DATABASE);
        changed |= setIfAbsent(root.node("redis", "enabled"), false);
        changed |= setIfAbsent(root.node("redis", "host"), DEFAULT_REDIS_HOST);
        changed |= setIfAbsent(root.node("redis", "port"), DEFAULT_REDIS_PORT);
        changed |= setIfAbsent(root.node("redis", "password"), "");
        changed |= setIfAbsent(root.node("locale", "default"), DEFAULT_LOCALE);
        changed |= setIfAbsent(root.node("branding", "name"), branding.name());
        changed |= setIfAbsent(root.node("branding", "primary-color"), branding.primaryColor());
        changed |= setIfAbsent(root.node("branding", "accent-color"), branding.accentColor());
        changed |= setIfAbsent(root.node("branding", "prefix"), branding.prefix());
        if (root.node("economy", "currencies").virtual()) {
            root.node("economy", "currencies").setList(String.class, DEFAULT_CURRENCIES);
            changed = true;
        }
        changed |= setIfAbsent(root.node("economy", "primary"), DEFAULT_CURRENCIES.getFirst());
        for (String system : PrismarinSystems.ALL) {
            changed |= setIfAbsent(root.node("systems", system), true);
        }
        for (String module : modules) {
            changed |= setIfAbsent(root.node("modules", module), true);
        }
        return changed;
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }

    private static YamlConfigurationLoader loaderFor(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
