package net.prismarin.core.rank;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.core.config.RankDisplaySettings;
import net.prismarin.shared.text.PrefixStyle;
import net.prismarin.shared.text.TextFormatter;
import org.bukkit.entity.Player;

public final class RankDisplayProvider implements RankFormatProvider {

    private static final int CACHE_LIMIT = 1024;

    private final RankFormatProvider delegate;
    private final RankDisplaySettings settings;
    private final TextFormatter formatter;
    private final Map<List<Object>, Component> cache = new ConcurrentHashMap<>();

    public RankDisplayProvider(RankFormatProvider delegate, RankDisplaySettings settings, TextFormatter formatter) {
        this.delegate = delegate;
        this.settings = settings;
        this.formatter = formatter;
    }

    @Override
    public Component prefix(Player player) {
        return render(settings.beforeName(), delegate.prefix(player), delegate.label(player));
    }

    @Override
    public Component ownPrefix(Player player) {
        return render(settings.beforeName(), delegate.ownPrefix(player), delegate.ownLabel(player));
    }

    @Override
    public Component label(Player player) {
        return render(settings.alone(), delegate.prefix(player), delegate.label(player));
    }

    @Override
    public Component ownLabel(Player player) {
        return render(settings.alone(), delegate.ownPrefix(player), delegate.ownLabel(player));
    }

    @Override
    public int weight(Player player) {
        return delegate.weight(player);
    }

    @Override
    public NamedTextColor nameColor(Player player) {
        return delegate.nameColor(player);
    }

    @Override
    public Component suffix(Player player) {
        return delegate.suffix(player);
    }

    @Override
    public TextColor chatColor(Player player) {
        return delegate.chatColor(player);
    }

    Component render(String template, Component prefix, Component label) {
        Component safePrefix = prefix == null ? Component.empty() : prefix;
        Component safeLabel = label == null ? safePrefix : label;
        return cache.computeIfAbsent(List.of(template, safePrefix, safeLabel), key -> {
            if (cache.size() > CACHE_LIMIT) {
                cache.clear();
            }
            Component rank = PrefixStyle.trimTrailing(safeLabel, settings.trim());
            if (!PrefixStyle.visible(rank) && !PrefixStyle.visible(safePrefix)) {
                return Component.empty();
            }
            if (template.equals(RankDisplaySettings.PREFIX)) {
                return safePrefix;
            }
            if (template.equals(RankDisplaySettings.RANK)) {
                return rank;
            }
            return formatter.format(template, Placeholder.component("prefix", safePrefix),
                    Placeholder.component("rank", rank));
        });
    }
}
