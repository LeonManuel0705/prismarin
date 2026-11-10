package net.prismarin.core.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.shared.text.PrefixStyle;
import net.prismarin.shared.text.TextFormatter;
import org.bukkit.entity.Player;

final class LuckPermsGroupRank implements RankFormatProvider {

    private final TextFormatter formatter;
    private final RankFormatProvider fallback;
    private final Map<String, Component> cache = new ConcurrentHashMap<>();

    LuckPermsGroupRank(TextFormatter formatter, RankFormatProvider fallback) {
        this.formatter = formatter;
        this.fallback = fallback;
    }

    @Override
    public Component prefix(Player player) {
        Group group = primaryGroup(player);
        if (group != null) {
            Component prefix = visible(group.getCachedData().getMetaData().getPrefix());
            if (prefix != null) {
                return prefix;
            }
            Component display = visible(group.getDisplayName());
            if (display != null) {
                return display;
            }
        }
        return fallback.prefix(player);
    }

    @Override
    public Component suffix(Player player) {
        Group group = primaryGroup(player);
        String suffix = group == null ? null : group.getCachedData().getMetaData().getSuffix();
        return suffix == null || suffix.isBlank() ? Component.empty() : format(suffix);
    }

    @Override
    public TextColor chatColor(Player player) {
        Group group = primaryGroup(player);
        if (group == null) {
            return fallback.chatColor(player);
        }
        CachedMetaData meta = group.getCachedData().getMetaData();
        return PrefixStyle.parse(meta.getMetaValue("chat-color"))
                .or(() -> PrefixStyle.lastColor(meta.getPrefix()))
                .orElse(null);
    }

    @Override
    public int weight(Player player) {
        Group group = primaryGroup(player);
        return group == null ? fallback.weight(player) : group.getWeight().orElse(fallback.weight(player));
    }

    @Override
    public NamedTextColor nameColor(Player player) {
        Group group = primaryGroup(player);
        if (group == null) {
            return fallback.nameColor(player);
        }
        CachedMetaData meta = group.getCachedData().getMetaData();
        return PrefixStyle.parse(meta.getMetaValue("name-color"))
                .or(() -> PrefixStyle.lastColor(meta.getPrefix()))
                .or(() -> PrefixStyle.lastColor(group.getDisplayName()))
                .map(PrefixStyle::nearest)
                .orElseGet(() -> fallback.nameColor(player));
    }

    private Component visible(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Component rendered = format(raw);
        return PrefixStyle.visible(rendered) ? rendered : null;
    }

    private Component format(String raw) {
        return cache.computeIfAbsent(raw, text -> formatter.format(text));
    }

    private static Group primaryGroup(Player player) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        return luckPerms.getGroupManager().getGroup(user.getPrimaryGroup());
    }
}
