package net.prismarin.lobby.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.util.CooldownTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@Singleton
public final class PlayerHiderService implements Listener {

    private static final Duration TOGGLE_COOLDOWN = Duration.ofSeconds(3);

    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbyItems items;
    private final LobbyVisibility visibility;
    private final CooldownTracker cooldown = new CooldownTracker(TOGGLE_COOLDOWN);

    @Inject
    public PlayerHiderService(LocaleService locale, ProfileService profiles, LobbyItems items,
                              LobbyVisibility visibility) {
        this.locale = locale;
        this.profiles = profiles;
        this.items = items;
        this.visibility = visibility;
    }

    public boolean hiding(Player player) {
        return visibility.hiding(player.getUniqueId());
    }

    public void toggle(Player player) {
        long now = System.currentTimeMillis();
        Locale playerLocale = PlayerLocales.of(player, profiles);
        if (!cooldown.tryUse(player.getUniqueId(), now)) {
            long seconds = (cooldown.remainingMillis(player.getUniqueId(), now) + 999L) / 1000L;
            player.sendActionBar(locale.render("lobby.hider.cooldown", playerLocale,
                    Placeholder.unparsed("seconds", String.valueOf(seconds))));
            return;
        }
        boolean nowHidden = !visibility.hiding(player.getUniqueId());
        visibility.hiding(player, nowHidden);
        player.sendActionBar(locale.render(nowHidden ? "lobby.hider.hidden" : "lobby.hider.shown", playerLocale));
        items.refreshHider(player, nowHidden);
        player.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER,
                0.5f, nowHidden ? 0.8f : 1.4f), Sound.Emitter.self());
    }

    public void clear(Player player) {
        visibility.forgetHiding(player.getUniqueId());
        cooldown.clear(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        visibility.refresh(event.getPlayer());
    }
}
