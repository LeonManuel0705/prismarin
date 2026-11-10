package net.prismarin.shared.locale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.shared.text.TextFormatter;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class YamlLocaleService implements LocaleService {

    public static final String LANGUAGE_NAME = "language.name";
    public static final String LANGUAGE_ICON = "language.icon";

    private static final List<String> BUNDLED_LANGUAGES = List.of("de", "en");
    private static final String DEFAULT_ICON = "PAPER";
    private static final Pattern LANGUAGE_FILE =
            Pattern.compile("messages_([A-Za-z]{2,3}(?:[_-](?:[A-Za-z]{2}|[0-9]{3}))?)\\.yml");
    private static final Logger LOGGER = Logger.getLogger(YamlLocaleService.class.getName());

    private final TextFormatter formatter;
    private final Map<String, Map<String, String>> messages = new ConcurrentHashMap<>();
    private final Map<CacheKey, Component> componentCache = new ConcurrentHashMap<>();
    private final String fallbackLanguage;
    private final Set<String> languages;

    public YamlLocaleService(Path dataDirectory, String defaultLanguage) {
        this(dataDirectory, defaultLanguage, TextFormatter.plain());
    }

    public YamlLocaleService(Path dataDirectory, String defaultLanguage, TextFormatter formatter) {
        this.formatter = formatter;
        this.fallbackLanguage = defaultLanguage.toLowerCase(Locale.ROOT);
        Set<String> loaded = new LinkedHashSet<>();
        try {
            Files.createDirectories(dataDirectory);
            for (String language : BUNDLED_LANGUAGES) {
                seedIfAbsent(dataDirectory.resolve(fileName(language)), fileName(language));
            }
            Map<String, Path> files = new LinkedHashMap<>();
            for (String language : BUNDLED_LANGUAGES) {
                files.put(language, dataDirectory.resolve(fileName(language)));
            }
            files.putAll(discover(dataDirectory, files.keySet()));
            for (Map.Entry<String, Path> entry : files.entrySet()) {
                String language = entry.getKey();
                Map<String, String> merged = new HashMap<>(bundled(fileName(language)));
                if (Files.exists(entry.getValue())) {
                    try {
                        merged.putAll(flatten(loadYaml(entry.getValue())));
                    } catch (IOException e) {
                        if (BUNDLED_LANGUAGES.contains(language)) {
                            throw e;
                        }
                        LOGGER.log(Level.WARNING, "Skipping broken language file " + entry.getValue().getFileName(), e);
                        continue;
                    }
                }
                if (!merged.isEmpty()) {
                    merged.putIfAbsent(LANGUAGE_NAME, nativeName(language));
                    merged.putIfAbsent(LANGUAGE_ICON, DEFAULT_ICON);
                    messages.put(language, merged);
                    loaded.add(language);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.languages = Collections.unmodifiableSet(loaded);
    }

    @Override
    public Set<String> languages() {
        return languages;
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
        String tag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
        if (messages.containsKey(tag)) {
            return tag;
        }
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        return messages.containsKey(language) ? language : fallbackLanguage;
    }

    static String normalizeTag(String tag) {
        return tag.replace('_', '-').toLowerCase(Locale.ROOT);
    }

    static String nativeName(String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        String name = locale.getDisplayLanguage(locale);
        if (name.isBlank() || name.equalsIgnoreCase(locale.getLanguage())) {
            return tag;
        }
        String capitalized = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
        String country = locale.getDisplayCountry(locale);
        return country.isBlank() ? capitalized : capitalized + " (" + country + ")";
    }

    private static String fileName(String language) {
        return "messages_" + language + ".yml";
    }

    private static Map<String, Path> discover(Path dataDirectory, Set<String> known) throws IOException {
        Map<String, Path> found = new TreeMap<>();
        try (Stream<Path> entries = Files.list(dataDirectory)) {
            for (Path path : entries.sorted().toList()) {
                Matcher matcher = LANGUAGE_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches() || !Files.isRegularFile(path)) {
                    continue;
                }
                String language = normalizeTag(matcher.group(1));
                if (!known.contains(language)) {
                    found.putIfAbsent(language, path);
                }
            }
        }
        return found;
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
