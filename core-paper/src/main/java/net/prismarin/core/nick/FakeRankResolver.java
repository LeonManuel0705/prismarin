package net.prismarin.core.nick;

import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.text.format.NamedTextColor;
import net.prismarin.shared.text.PrefixStyle;

public final class FakeRankResolver {

    public static final NamedTextColor DEFAULT_COLOR = NamedTextColor.GRAY;

    private FakeRankResolver() {
        throw new AssertionError();
    }

    public static Look resolve(NickSettings.FakeRank settings, Optional<GroupLook> group) {
        Optional<String> prefix = nonBlank(settings.prefix())
                .or(() -> group.flatMap(look -> Optional.ofNullable(look.prefix())).flatMap(FakeRankResolver::nonBlank));
        int weight = settings.weight() >= 0
                ? settings.weight()
                : group.map(look -> look.weight().orElse(0)).orElse(0);
        NamedTextColor color = color(settings.nameColor())
                .or(() -> group.flatMap(look -> color(look.nameColor())))
                .or(() -> prefix.flatMap(PrefixStyle::lastColor).map(PrefixStyle::nearest))
                .orElse(DEFAULT_COLOR);
        return new Look(prefix, weight, color);
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
