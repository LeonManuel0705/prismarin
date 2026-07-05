package net.prismarin.api.locale;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Resolves translation keys to MiniMessage strings and rendered
 * Adventure components with a locale fallback chain.
 */
public interface LocaleService {

    /**
     * Renders the message behind the given key for the given locale.
     *
     * @param key the dotted translation key
     * @param locale the target locale
     * @param resolvers additional tag resolvers for placeholders
     * @return the rendered component, falling back to the default locale
     *         and finally to the key itself
     */
    Component render(String key, Locale locale, TagResolver... resolvers);

    /**
     * Returns the raw MiniMessage string behind the given key.
     *
     * @param key the dotted translation key
     * @param locale the target locale
     * @return the raw MiniMessage string, falling back to the default
     *         locale and finally to the key itself
     */
    String raw(String key, Locale locale);
}
