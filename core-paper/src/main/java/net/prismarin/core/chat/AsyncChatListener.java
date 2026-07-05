package net.prismarin.core.chat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.core.permission.CorePermissions;
import net.prismarin.core.permission.PermissionSnapshots;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@Singleton
public final class AsyncChatListener implements Listener {

    private final RankFormatProvider ranks;
    private final LocaleService locale;
    private final MiniMessage miniMessage;
    private final CoreConfig config;
    private final PermissionSnapshots snapshots;
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    @Inject
    public AsyncChatListener(RankFormatProvider ranks, LocaleService locale, MiniMessage miniMessage,
                             CoreConfig config, PermissionSnapshots snapshots) {
        this.ranks = ranks;
        this.locale = locale;
        this.miniMessage = miniMessage;
        this.config = config;
        this.snapshots = snapshots;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = plainText.serialize(event.message());
        Component content = snapshots.has(player, CorePermissions.CHAT_FORMAT)
                ? miniMessage.deserialize(plain)
                : Component.text(miniMessage.stripTags(plain));
        event.message(content);
        event.renderer(this::render);
    }

    private Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
        Locale viewerLocale = viewer instanceof Player player
                ? player.locale()
                : Locale.forLanguageTag(config.defaultLocale());
        return locale.render("chat.format", viewerLocale,
                Placeholder.component("prefix", ranks.prefix(source)),
                Placeholder.component("name", Component.text(source.getName(), ranks.nameColor(source))),
                Placeholder.component("message", message));
    }
}
