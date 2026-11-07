package net.prismarin.api.locale;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public interface LocaleService {

    Component render(String key, Locale locale, TagResolver... resolvers);

    String raw(String key, Locale locale);

    Component format(String text, TagResolver... resolvers);
}
