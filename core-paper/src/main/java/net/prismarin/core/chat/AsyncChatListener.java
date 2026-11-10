package net.prismarin.core.chat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.nick.NickPerks;
import net.prismarin.core.permission.CorePermissions;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.text.PrefixStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@Singleton
public final class AsyncChatListener implements Listener {

    private final RankFormatProvider ranks;
    private final LocaleService locale;
    private final MiniMessage miniMessage;
    private final CoreConfig config;
    private final NickPerks perks;
    private final ProfileService profiles;
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    @Inject
    public AsyncChatListener(RankFormatProvider ranks, LocaleService locale, MiniMessage miniMessage,
                             CoreConfig config, NickPerks perks, ProfileService profiles) {
        this.ranks = ranks;
        this.locale = locale;
        this.miniMessage = miniMessage;
        this.config = config;
        this.perks = perks;
        this.profiles = profiles;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = plainText.serialize(event.message());
        Component content = perks.has(player, CorePermissions.CHAT_FORMAT)
                ? locale.format(plain)
                : Component.text(miniMessage.stripTags(plain));
        event.message(content);
        event.renderer(this::render);
    }

    private Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
        Locale viewerLocale = viewer instanceof Player player
                ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
        Component prefix = ranks.prefix(source);
        TagResolver[] resolvers = {
                Placeholder.component("prefix", prefix),
                Placeholder.component("name", Component.text(source.getName(), ranks.nameColor(source))),
                Placeholder.component("message", message)};
        if (PrefixStyle.visible(prefix)) {
            return locale.render("chat.format", viewerLocale, source.getUniqueId(), resolvers);
        }
        String format = locale.raw("chat.format", viewerLocale).replace("%prefix% ", "").replace("<prefix> ", "");
        return locale.format(source.getUniqueId(), format, resolvers);
    }
}
