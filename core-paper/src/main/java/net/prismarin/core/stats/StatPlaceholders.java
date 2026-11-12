package net.prismarin.core.stats;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.StatsService;

public final class StatPlaceholders {

    private final StatsService stats;
    private final Function<UUID, Locale> locales;

    public StatPlaceholders(StatsService stats, Function<UUID, Locale> locales) {
        this.stats = stats;
        this.locales = locales;
    }

    public String resolve(UUID player, String params) {
        Optional<StatPlaceholder> parsed = StatPlaceholder.parse(params);
        if (parsed.isEmpty()) {
            return null;
        }
        StatPlaceholder placeholder = parsed.get();
        if (!stats.periods().contains(placeholder.period())) {
            return "";
        }
        Locale locale = locales.apply(player);
        if (placeholder.kind() == StatPlaceholder.Kind.TOP) {
            Optional<LeaderboardEntry> entry = stats.cachedTop(placeholder.key(), placeholder.period())
                    .flatMap(board -> board.entry(placeholder.position()));
            return entry.map(found -> switch (placeholder.field()) {
                case NAME -> found.name();
                case RAW -> Long.toString(found.value());
                case UUID -> found.player().toString();
                default -> stats.format(placeholder.key(), found.value(), locale);
            }).orElse("");
        }
        if (player == null) {
            return "";
        }
        if (placeholder.field() == StatPlaceholder.Field.RANK) {
            OptionalInt rank = stats.cachedRank(player, placeholder.key(), placeholder.period());
            return rank.isPresent() ? Integer.toString(rank.getAsInt()) : "";
        }
        long value = stats.cached(player)
                .map(found -> found.value(placeholder.key(), placeholder.period()))
                .orElse(0L);
        return placeholder.field() == StatPlaceholder.Field.RAW
                ? Long.toString(value)
                : stats.format(placeholder.key(), value, locale);
    }
}
