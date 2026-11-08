package net.prismarin.shared.locale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.shared.text.TextFormatter;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class YamlLocaleService implements LocaleService {

    private static final List<String> BUNDLED_LANGUAGES = List.of("de", "en");

    private final TextFormatter formatter;
    private final Map<String, Map<String, String>> messages = new ConcurrentHashMap<>();
    private final Map<CacheKey, Component> componentCache = new ConcurrentHashMap<>();
    private final String fallbackLanguage;

    public YamlLocaleService(Path dataDirectory, String defaultLanguage) {
        this(dataDirectory, defaultLanguage, TextFormatter.plain());
    }

    public YamlLocaleService(Path dataDirectory, String defaultLanguage, TextFormatter formatter) {
        this.formatter = formatter;
        this.fallbackLanguage = defaultLanguage.toLowerCase(Locale.ROOT);
        try {
            Files.createDirectories(dataDirectory);
            for (String language : BUNDLED_LANGUAGES) {
                Path file = dataDirectory.resolve("messages_" + language + ".yml");
                seedIfAbsent(file, "messages_" + language + ".yml");
                Map<String, String> merged = new HashMap<>(bundled("messages_" + language + ".yml"));
                if (Files.exists(file)) {
                    merged.putAll(flatten(loadYaml(file)));
                }
                if (!merged.isEmpty()) {
                    messages.put(language, merged);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Component render(String key, Locale locale, TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return componentCache.computeIfAbsent(
                    new CacheKey(key, languageOf(locale)),
                    cacheKey -> formatter.format(raw(key, locale))
            );
        }
        return formatter.format(raw(key, locale), resolvers);
    }

    @Override
    public Component format(String text, TagResolver... resolvers) {
        return formatter.format(text, resolvers);
    }

    @Override
    public Component render(String key, Locale locale, UUID viewer, TagResolver... resolvers) {
        return formatter.format(viewer, raw(key, locale), resolvers);
    }

    @Override
    public Component format(UUID viewer, String text, TagResolver... resolvers) {
        return formatter.format(viewer, text, resolvers);
    }

    @Override
    public String raw(String key, Locale locale) {
        Map<String, String> primary = messages.get(languageOf(locale));
        if (primary != null) {
            String value = primary.get(key);
            if (value != null) {
                return value;
            }
        }
        Map<String, String> fallback = messages.get(fallbackLanguage);
        if (fallback != null) {
            String value = fallback.get(key);
            if (value != null) {
                return value;
            }
        }
        return key;
    }

    private String languageOf(Locale locale) {
        if (locale == null || locale.getLanguage().isEmpty()) {
            return fallbackLanguage;
        }
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        return messages.containsKey(language) ? language : fallbackLanguage;
    }

    private static void seedIfAbsent(Path file, String resourceName) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        try (InputStream resource = YamlLocaleService.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resource != null) {
                Files.copy(resource, file);
            }
        }
    }

    private static Map<String, String> bundled(String resourceName) throws IOException {
        try (InputStream resource = YamlLocaleService.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resource == null) {
                return Map.of();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8));
            return flatten(YamlConfigurationLoader.builder().source(() -> reader).build().load());
        }
    }

    private static ConfigurationNode loadYaml(Path file) throws IOException {
        return YamlConfigurationLoader.builder().path(file).build().load();
    }

    private static Map<String, String> flatten(ConfigurationNode root) {
        Map<String, String> flat = new HashMap<>();
        collect(root, "", flat);
        return flat;
    }

    private static void collect(ConfigurationNode node, String prefix, Map<String, String> flat) {
        Map<Object, ? extends ConfigurationNode> children = node.childrenMap();
        if (children.isEmpty()) {
            String value = node.getString();
            if (value != null && !prefix.isEmpty()) {
                flat.put(prefix, value);
            }
            return;
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : children.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey().toString() : prefix + "." + entry.getKey();
            collect(entry.getValue(), key, flat);
        }
    }

    private record CacheKey(String key, String language) {
    }
}
