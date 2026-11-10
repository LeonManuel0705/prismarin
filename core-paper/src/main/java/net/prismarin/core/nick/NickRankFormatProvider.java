package net.prismarin.core.nick;

import com.google.inject.Provider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.prismarin.core.rank.RankFormatProvider;
import org.bukkit.entity.Player;

public final class NickRankFormatProvider implements RankFormatProvider {

    private final RankFormatProvider real;
    private final NickRegistry registry;
    private final Provider<FakeRankFormat> fake;

    public NickRankFormatProvider(RankFormatProvider real, NickRegistry registry, Provider<FakeRankFormat> fake) {
        this.real = real;
        this.registry = registry;
        this.fake = fake;
    }

    @Override
    public Component prefix(Player player) {
        return registry.nicked(player.getUniqueId()) ? fake.get().prefix(player) : real.prefix(player);
    }

    @Override
    public int weight(Player player) {
        return registry.nicked(player.getUniqueId()) ? fake.get().weight() : real.weight(player);
    }

    @Override
    public NamedTextColor nameColor(Player player) {
        return registry.nicked(player.getUniqueId()) ? fake.get().nameColor() : real.nameColor(player);
    }

    @Override
    public Component suffix(Player player) {
        return registry.nicked(player.getUniqueId()) ? Component.empty() : real.suffix(player);
    }

    @Override
    public TextColor chatColor(Player player) {
        return registry.nicked(player.getUniqueId()) ? fake.get().chatColor() : real.chatColor(player);
    }
}
