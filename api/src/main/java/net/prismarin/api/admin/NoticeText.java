package net.prismarin.api.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record NoticeText(Map<String, String> templates, Map<String, String> placeholders) {

    public static final String ANY_LANGUAGE = "*";
    public static final Pattern LANGUAGE = Pattern.compile("\\*|[a-z]{2,3}(-[a-z0-9]{1,8})*");
    public static final Pattern PLACEHOLDER = Pattern.compile("[a-z0-9_]{1,32}");
    public static final int MAX_TEMPLATE = 1024;
    public static final int MAX_VALUE = 256;
    public static final int MAX_LANGUAGES = 16;
    public static final int MAX_PLACEHOLDERS = 16;

    public NoticeText {
        templates = languages(templates);
        placeholders = values(placeholders);
    }

    public static NoticeText of(String template) {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put(ANY_LANGUAGE, template);
        return new NoticeText(templates, Map.of());
    }

    public static NoticeText of(Map<String, String> templates) {
        return new NoticeText(templates, Map.of());
    }

    public NoticeText and(String language, String template) {
        Map<String, String> copy = new LinkedHashMap<>(templates);
        copy.put(language, template);
        return new NoticeText(copy, placeholders);
    }

    public NoticeText with(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(placeholders);
        copy.put(key, value);
        return new NoticeText(templates, copy);
    }

    public String template(Locale locale) {
        if (locale != null) {
            String exact = templates.get(locale.toLanguageTag().toLowerCase(Locale.ROOT));
            if (exact != null) {
                return exact;
            }
            String language = templates.get(locale.getLanguage().toLowerCase(Locale.ROOT));
            if (language != null) {
                return language;
            }
        }
        String any = templates.get(ANY_LANGUAGE);
        if (any != null) {
            return any;
        }
        String english = templates.get("en");
        return english != null ? english : templates.values().iterator().next();
    }

    private static Map<String, String> languages(Map<String, String> templates) {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("a notice needs at least one text");
        }
        if (templates.size() > MAX_LANGUAGES) {
            throw new IllegalArgumentException("a notice has at most " + MAX_LANGUAGES + " languages");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        templates.forEach((language, template) -> {
            String key = language == null ? "" : language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            if (!LANGUAGE.matcher(key).matches()) {
                throw new IllegalArgumentException("'" + language + "' is no language tag");
            }
            if (template == null || template.isBlank() || template.length() > MAX_TEMPLATE) {
                throw new IllegalArgumentException("a notice text needs 1 to " + MAX_TEMPLATE + " characters");
            }
            copy.put(key, template);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, String> values(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return Map.of();
        }
        if (placeholders.size() > MAX_PLACEHOLDERS) {
            throw new IllegalArgumentException("a notice has at most " + MAX_PLACEHOLDERS + " placeholders");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        placeholders.forEach((key, value) -> {
            if (key == null || !PLACEHOLDER.matcher(key).matches()) {
                throw new IllegalArgumentException("placeholder '" + key + "' must match " + PLACEHOLDER.pattern());
            }
            String text = value == null ? "" : value;
            if (text.length() > MAX_VALUE) {
                throw new IllegalArgumentException("placeholder values have at most " + MAX_VALUE + " characters");
            }
            copy.put(key, text);
        });
        return Collections.unmodifiableMap(copy);
    }
}
