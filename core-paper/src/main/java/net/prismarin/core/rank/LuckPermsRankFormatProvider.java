package net.prismarin.core.rank;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

public final class LuckPermsRankFormatProvider implements RankFormatProvider {

    private final MiniMessage miniMessage;
    private final RankFormatProvider fallback;
    private final Map<String, Component> prefixCache = new ConcurrentHashMap<>();

    public LuckPermsRankFormatProvider(MiniMessage miniMessage, RankFormatProvider fallback) {
        this.miniMessage = miniMessage;
        this.fallback = fallback;
    }

    @Override
    public Component prefix(Player player) {
        String prefix = metaData(player).getPrefix();
        if (prefix == null || prefix.isBlank()) {
            return fallback.prefix(player);
        }
        return prefixCache.computeIfAbsent(prefix, miniMessage::deserialize);
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
        String value = metaData(player).getMetaValue("name-color");
        if (value == null) {
            return fallback.nameColor(player);
        }
        NamedTextColor color = NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
        return color == null ? fallback.nameColor(player) : color;
    }

    private static CachedMetaData metaData(Player player) {
        return LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
    }
}
