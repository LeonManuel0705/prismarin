package net.prismarin.lobby.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.util.CooldownTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@Singleton
public final class PlayerHiderService implements Listener {

    private static final Duration TOGGLE_COOLDOWN = Duration.ofSeconds(3);

    private final LobbyPlugin plugin;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbyItems items;
    private final Set<UUID> hiding = ConcurrentHashMap.newKeySet();
    private final CooldownTracker cooldown = new CooldownTracker(TOGGLE_COOLDOWN);

    @Inject
    public PlayerHiderService(LobbyPlugin plugin, LocaleService locale, ProfileService profiles, LobbyItems items) {
        this.plugin = plugin;
        this.locale = locale;
        this.profiles = profiles;
        this.items = items;
    }

    public boolean hiding(Player player) {
        return hiding.contains(player.getUniqueId());
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
        boolean nowHidden = !hiding.contains(player.getUniqueId());
        if (nowHidden) {
            hiding.add(player.getUniqueId());
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.getUniqueId().equals(player.getUniqueId())) {
                    player.hidePlayer(plugin, other);
                }
            }
            player.sendActionBar(locale.render("lobby.hider.hidden", playerLocale));
        } else {
            hiding.remove(player.getUniqueId());
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.getUniqueId().equals(player.getUniqueId())) {
                    player.showPlayer(plugin, other);
                }
            }
            player.sendActionBar(locale.render("lobby.hider.shown", playerLocale));
        }
        player.getInventory().setItem(LobbyItems.HIDER_SLOT, items.hiderItem(playerLocale, nowHidden));
        player.playSound(Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER,
                0.5f, nowHidden ? 0.8f : 1.4f), Sound.Emitter.self());
    }

    public void clear(Player player) {
        hiding.remove(player.getUniqueId());
        cooldown.clear(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID id : hiding) {
            if (id.equals(joined.getUniqueId())) {
                continue;
            }
            Player viewer = plugin.getServer().getPlayer(id);
            if (viewer != null) {
                viewer.hidePlayer(plugin, joined);
            }
        }
    }
}
