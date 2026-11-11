package net.prismarin.core.nick;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.shared.text.TextFormatter;
import net.prismarin.shared.text.PrefixStyle;

public final class FakeRankResolver {

    public static final NamedTextColor DEFAULT_COLOR = NamedTextColor.GRAY;

    private FakeRankResolver() {
        throw new AssertionError();
    }

    private static final TextFormatter FORMATTER = TextFormatter.plain();

    public static Look resolve(NickSettings.FakeRank settings, Optional<GroupLook> group) {
        return resolve(settings, group, List.of());
    }

    public static Look resolve(NickSettings.FakeRank settings, Optional<GroupLook> group, List<GroupLook> groups) {
        Optional<String> override = nonBlank(settings.prefix());
        Optional<GroupLook> source = override.flatMap(prefix -> lookalike(prefix, groups)).or(() -> group);
        Optional<String> prefix = override
                .or(() -> group.flatMap(look -> Optional.ofNullable(look.prefix())).flatMap(FakeRankResolver::nonBlank));
        int weight = settings.weight() >= 0
                ? settings.weight()
                : source.map(look -> look.weight().orElse(0)).orElse(0);
        NamedTextColor color = color(settings.nameColor())
                .or(() -> source.flatMap(look -> color(look.nameColor())))
                .or(() -> prefix.flatMap(PrefixStyle::lastColor).map(PrefixStyle::nearest))
                .orElse(DEFAULT_COLOR);
        return new Look(prefix, weight, color);
    }

    public static Optional<GroupLook> lookalike(String prefix, List<GroupLook> groups) {
        String shown = shown(prefix);
        if (shown.isEmpty()) {
            return Optional.empty();
        }
        return groups.stream()
                .filter(group -> group.prefix() != null && shown(group.prefix()).equalsIgnoreCase(shown))
                .max(Comparator.comparingInt(group -> group.weight().orElse(0)));
    }

    static String shown(String raw) {
        return PlainTextComponentSerializer.plainText().serialize(FORMATTER.format(raw)).strip();
    }

    public static Optional<NamedTextColor> color(String value) {
        return PrefixStyle.parse(value).map(PrefixStyle::nearest);
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public record GroupLook(String prefix, OptionalInt weight, String nameColor) {
    }

    public record Look(Optional<String> prefix, int weight, NamedTextColor nameColor) {

        public String disguisePrefix() {
            return prefix.orElse("");
        }

        public String disguiseColor() {
            return NamedTextColor.NAMES.key(nameColor);
        }
    }
}
