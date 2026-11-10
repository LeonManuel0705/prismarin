package net.prismarin.core.rank;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.prismarin.shared.text.PrefixStyle;
import net.prismarin.shared.text.TextFormatter;
import org.bukkit.entity.Player;

public final class LuckPermsRankFormatProvider implements RankFormatProvider {

    private final TextFormatter formatter;
    private final RankFormatProvider fallback;
    private final Map<String, Component> prefixCache = new ConcurrentHashMap<>();

    public LuckPermsRankFormatProvider(TextFormatter formatter, RankFormatProvider fallback) {
        this.formatter = formatter;
        this.fallback = fallback;
    }

    @Override
    public Component prefix(Player player) {
        String prefix = metaData(player).getPrefix();
        if (prefix == null || prefix.isBlank()) {
            return fallback.prefix(player);
        }
        return prefixCache.computeIfAbsent(prefix, text -> formatter.format(text));
    }

    @Override
    public int weight(Player player) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        Group group = luckPerms.getGroupManager().getGroup(user.getPrimaryGroup());
        if (group == null) {
            return fallback.weight(player);
        }
        return group.getWeight().orElse(fallback.weight(player));
    }

    @Override
    public NamedTextColor nameColor(Player player) {
        CachedMetaData meta = metaData(player);
        return PrefixStyle.parse(meta.getMetaValue("name-color"))
                .or(() -> PrefixStyle.lastColor(meta.getPrefix()))
                .map(PrefixStyle::nearest)
                .orElseGet(() -> fallback.nameColor(player));
    }

    private static CachedMetaData metaData(Player player) {
        return LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
    }
}
