package net.prismarin.core.moderation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.moderation.Durations;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class MuteListener implements Listener {

    private final ModerationChannel channel;
    private final LocaleService locale;
    private final ProfileService profiles;

    @Inject
    public MuteListener(ModerationChannel channel, LocaleService locale, ProfileService profiles) {
        this.channel = channel;
        this.locale = locale;
        this.profiles = profiles;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ModerationChannel.Mute mute = channel.mute(event.getPlayer().getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        String remaining = mute.until() <= 0L ? "∞" : Durations.format(mute.until() - System.currentTimeMillis());
        event.getPlayer().sendMessage(locale.render("moderation.muted", PlayerLocales.of(event.getPlayer(), profiles),
                Placeholder.unparsed("reason", mute.reason()), Placeholder.unparsed("remaining", remaining),
                Placeholder.unparsed("id", mute.id()), Placeholder.unparsed("appeal", mute.appeal())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        channel.forget(event.getPlayer().getUniqueId());
    }
}
