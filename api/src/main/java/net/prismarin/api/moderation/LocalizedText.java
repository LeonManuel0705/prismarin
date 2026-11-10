package net.prismarin.api.moderation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class LocalizedText {

    private LocalizedText() {
        throw new AssertionError();
    }

    static Optional<String> pick(Map<String, String> texts, Locale locale) {
        if (locale != null) {
            String exact = texts.get(locale.toLanguageTag().toLowerCase(Locale.ROOT));
            if (exact != null) {
                return Optional.of(exact);
            }
            String language = texts.get(locale.getLanguage().toLowerCase(Locale.ROOT));
            if (language != null) {
                return Optional.of(language);
            }
        }
        String english = texts.get("en");
        if (english != null) {
            return Optional.of(english);
        }
        return texts.values().stream().findFirst();
    }
}
