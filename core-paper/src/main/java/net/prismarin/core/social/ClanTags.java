package net.prismarin.core.social;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.prismarin.api.nick.NickApi;
import net.prismarin.core.api.ChatDecorator;
import net.prismarin.core.api.TablistContributor;
import net.prismarin.shared.social.ClanTagFormat;
import org.bukkit.entity.Player;

@Singleton
public final class ClanTags {

    public static final String PLACEHOLDER = "clan_tag";

    private final ClanMirror mirror;
    private final BackendClanService clans;

    @Inject
    public ClanTags(ClanMirror mirror, BackendClanService clans) {
        this.mirror = mirror;
        this.clans = clans;
    }

    public Optional<Component> tag(Player player) {
        if (NickApi.service().map(nicks -> nicks.isNicked(player.getUniqueId())).orElse(false)) {
            return Optional.empty();
        }
        return mirror.clanOf(player.getUniqueId()).map(clans::formatTag);
    }

    public TablistContributor tablist() {
        return new TablistContributor() {
            @Override
            public Component prefix(Player viewer, Player target) {
                return shown(target, display -> display.tablist() && display.before());
            }

            @Override
            public Component suffix(Player viewer, Player target) {
                return shown(target, display -> display.tablist() && !display.before());
            }
        };
    }

    public ChatDecorator chat() {
        return new ChatDecorator() {
            @Override
            public Component prefix(Player source, Audience viewer) {
                return shown(source, display -> display.chat() && display.before());
            }

            @Override
            public Component suffix(Player source, Audience viewer) {
                return shown(source, display -> display.chat() && !display.before());
            }

            @Override
            public Map<String, Component> placeholders(Player source, Audience viewer) {
                return Map.of(PLACEHOLDER, tag(source).orElse(Component.empty()));
            }
        };
    }

    private Component shown(Player player, Predicate<ClanTagFormat> where) {
        return where.test(mirror.display()) ? tag(player).orElse(Component.empty()) : Component.empty();
    }
}
