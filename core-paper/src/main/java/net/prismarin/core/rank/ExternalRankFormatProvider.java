package net.prismarin.core.rank;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.prismarin.api.player.RankDisplay;
import net.prismarin.api.player.RankSources;
import net.prismarin.shared.text.PrefixStyle;
import org.bukkit.entity.Player;

public final class ExternalRankFormatProvider implements RankFormatProvider {

    private final RankFormatProvider fallback;
    private final RankSources sources;

    public ExternalRankFormatProvider(RankFormatProvider fallback, RankSources sources) {
        this.fallback = fallback;
        this.sources = sources;
    }

    private Optional<RankDisplay> external(Player player) {
        return sources.any() ? sources.rank(player.getUniqueId()) : Optional.empty();
    }

    @Override
    public Component prefix(Player player) {
        return external(player).map(RankDisplay::prefix).orElseGet(() -> fallback.prefix(player));
    }

    @Override
    public int weight(Player player) {
        return external(player).map(RankDisplay::weight).orElseGet(() -> fallback.weight(player));
    }

    @Override
    public NamedTextColor nameColor(Player player) {
        return external(player).map(rank -> PrefixStyle.nearest(rank.nameColor()))
                .orElseGet(() -> fallback.nameColor(player));
    }

    @Override
    public Component ownPrefix(Player player) {
        return external(player).map(RankDisplay::prefix).orElseGet(() -> fallback.ownPrefix(player));
    }

    @Override
    public Component suffix(Player player) {
        return external(player).map(RankDisplay::suffix).orElseGet(() -> fallback.suffix(player));
    }

    @Override
    public TextColor chatColor(Player player) {
        Optional<RankDisplay> rank = external(player);
        return rank.isPresent() ? rank.get().chatColor() : fallback.chatColor(player);
    }
}
