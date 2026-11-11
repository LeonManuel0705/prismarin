package net.prismarin.core.rank;

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
import net.prismarin.shared.text.PrefixStyle;
import net.prismarin.shared.text.TextFormatter;
import org.bukkit.entity.Player;

public final class LuckPermsRankFormatProvider implements RankFormatProvider {

    private final TextFormatter formatter;
    private final RankFormatProvider fallback;
    private final String labelMeta;
    private final Map<String, Component> prefixCache = new ConcurrentHashMap<>();

    public LuckPermsRankFormatProvider(TextFormatter formatter, RankFormatProvider fallback) {
        this(formatter, fallback, "");
    }

    public LuckPermsRankFormatProvider(TextFormatter formatter, RankFormatProvider fallback, String labelMeta) {
        this.formatter = formatter;
        this.fallback = fallback;
        this.labelMeta = labelMeta == null ? "" : labelMeta;
    }

    @Override
    public Component label(Player player) {
        if (!labelMeta.isBlank()) {
            String label = metaData(player).getMetaValue(labelMeta);
            if (label != null && !label.isBlank()) {
                Component rendered = format(label);
                if (PrefixStyle.visible(rendered)) {
                    return rendered;
                }
            }
        }
        return prefix(player);
    }

    @Override
    public Component prefix(Player player) {
        String prefix = metaData(player).getPrefix();
        if (prefix != null && !prefix.isBlank()) {
            Component rendered = format(prefix);
            if (PrefixStyle.visible(rendered)) {
                return rendered;
            }
        }
        String display = displayName(player);
        if (display != null) {
            Component rendered = format(display);
            if (PrefixStyle.visible(rendered)) {
                return rendered;
            }
        }
        return fallback.prefix(player);
    }

    @Override
    public Component suffix(Player player) {
        String suffix = metaData(player).getSuffix();
        if (suffix == null || suffix.isBlank()) {
            return Component.empty();
        }
        return format(suffix);
    }

    @Override
    public TextColor chatColor(Player player) {
        CachedMetaData meta = metaData(player);
        return PrefixStyle.parse(meta.getMetaValue("chat-color"))
                .or(() -> PrefixStyle.lastColor(meta.getPrefix()))
                .orElse(null);
    }

    private Component format(String text) {
        return prefixCache.computeIfAbsent(text, raw -> formatter.format(raw));
    }

    private static String displayName(Player player) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        Group group = luckPerms.getGroupManager().getGroup(user.getPrimaryGroup());
        if (group == null || group.getDisplayName() == null || group.getDisplayName().isBlank()) {
            return null;
        }
        return group.getDisplayName();
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
                .or(() -> PrefixStyle.lastColor(displayName(player)))
                .map(PrefixStyle::nearest)
                .orElseGet(() -> fallback.nameColor(player));
    }

    private static CachedMetaData metaData(Player player) {
        return LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
    }
}
