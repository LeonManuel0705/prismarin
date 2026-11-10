package net.prismarin.shared.text;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class TextFormatter {

    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("%([a-z0-9_-]+)%");

    private final MiniMessage miniMessage;
    private final TagResolver globalPlaceholders;
    private volatile PlaceholderExpander expander = PlaceholderExpander.NONE;

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
        return format(null, text, resolvers);
    }

    public Component format(UUID viewer, String text, TagResolver... resolvers) {
        TagResolver placeholders = TagResolver.resolver(globalPlaceholders, TagResolver.resolver(resolvers));
        return miniMessage.deserialize(prepare(text, placeholders, viewer), resolvers);
    }

    public String prepare(String text, TagResolver placeholders, UUID viewer) {
        String known = percentToTags(text, placeholders);
        String expanded = viewer != null && known.indexOf('%') >= 0 ? expander.expand(viewer, known) : known;
        return LegacyColors.toMiniMessage(expanded);
    }

    public String expandExternal(UUID viewer, String text) {
        return text.indexOf('%') >= 0 ? expander.expand(viewer, text) : text;
    }

    public void expander(PlaceholderExpander expander) {
        this.expander = expander == null ? PlaceholderExpander.NONE : expander;
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
