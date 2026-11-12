package net.prismarin.core.locale;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.locale.LocaleService;

@Singleton
public final class ConfiguredText {

    private final LocaleService locale;

    @Inject
    public ConfiguredText(LocaleService locale) {
        this.locale = locale;
    }

    public Component render(NoticeText text, Locale target, UUID viewer, TagResolver... resolvers) {
        String template = text.template(target);
        TagResolver[] all = withPlaceholders(text.placeholders(), resolvers);
        if (!locale.raw(template, target).equals(template)) {
            return locale.render(template, target, viewer, all);
        }
        return locale.format(viewer, template, all);
    }

    public Component render(Map<String, String> values, NoticeText text, Locale target, UUID viewer) {
        TagResolver[] resolvers = values.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        return render(text, target, viewer, resolvers);
    }

    private static TagResolver[] withPlaceholders(Map<String, String> placeholders, TagResolver... resolvers) {
        if (placeholders.isEmpty()) {
            return resolvers;
        }
        TagResolver[] all = new TagResolver[placeholders.size() + resolvers.length];
        int index = 0;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            all[index++] = Placeholder.unparsed(entry.getKey(), entry.getValue());
        }
        System.arraycopy(resolvers, 0, all, index, resolvers.length);
        return all;
    }
}
