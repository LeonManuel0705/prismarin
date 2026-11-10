package net.prismarin.api.locale;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public interface LocaleService {

    Component render(String key, Locale locale, TagResolver... resolvers);

    String raw(String key, Locale locale);

    Component format(String text, TagResolver... resolvers);

    default Component render(String key, Locale locale, UUID viewer, TagResolver... resolvers) {
        return render(key, locale, resolvers);
    }

    default Component format(UUID viewer, String text, TagResolver... resolvers) {
        return format(text, resolvers);
    }

    default Set<String> languages() {
        return Set.of();
    }
}
