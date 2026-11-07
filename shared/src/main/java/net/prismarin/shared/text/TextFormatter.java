package net.prismarin.shared.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class TextFormatter {

    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("%([a-z0-9_-]+)%");

    private final MiniMessage miniMessage;
    private final TagResolver globalPlaceholders;

    public TextFormatter(TagResolver globalPlaceholders) {
        this.globalPlaceholders = globalPlaceholders;
        this.miniMessage = MiniMessage.builder()
                .tags(TagResolver.resolver(TagResolver.standard(), globalPlaceholders))
                .build();
    }

    public static TextFormatter plain() {
        return new TextFormatter(TagResolver.empty());
    }

    public Component format(String text, TagResolver... resolvers) {
        TagResolver placeholders = TagResolver.resolver(globalPlaceholders, TagResolver.resolver(resolvers));
        return miniMessage.deserialize(prepare(text, placeholders), resolvers);
    }

    public String prepare(String text, TagResolver placeholders) {
        return percentToTags(LegacyColors.toMiniMessage(text), placeholders);
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    static String percentToTags(String text, TagResolver placeholders) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        Matcher matcher = PERCENT_PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = placeholders.has(name) ? "<" + name + ">" : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
