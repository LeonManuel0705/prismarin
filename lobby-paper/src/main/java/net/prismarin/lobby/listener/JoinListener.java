package net.prismarin.lobby.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class JoinListener implements Listener {

    private static final Duration TITLE_FADE_IN = Duration.ofMillis(400);
    private static final Duration TITLE_STAY = Duration.ofMillis(2600);
    private static final Duration TITLE_FADE_OUT = Duration.ofMillis(600);

    private final LobbyWorlds worlds;
    private final LobbyItems items;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final BuildModeService buildMode;
    private final PlayerHiderService hider;
    private final LobbySettings settings;

    @Inject
    public JoinListener(LobbyWorlds worlds, LobbyItems items, LocaleService locale, ProfileService profiles,
                        BuildModeService buildMode, PlayerHiderService hider, LobbySettings settings) {
        this.worlds = worlds;
        this.items = items;
        this.locale = locale;
        this.profiles = profiles;
        this.buildMode = buildMode;
        this.hider = hider;
        this.settings = settings;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean first = !player.hasPlayedBefore();
        worlds.teleportToSpawn(player);
        if (worlds.isLobbyWorld(player.getWorld())) {
            player.getInventory().clear();
        }
        if (!buildMode.active(player)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        restore(player);
        if (settings.enabled(LobbySettings.DOUBLE_JUMP) && player.getGameMode() != GameMode.CREATIVE) {
            player.setAllowFlight(true);
        }
        items.give(player, hider.hiding(player));
        if (settings.enabled(LobbySettings.JOIN_TITLE)) {
            welcome(player, first);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hider.clear(event.getPlayer());
        buildMode.clear(event.getPlayer().getUniqueId());
    }

    private void restore(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
    }

    private void welcome(Player player, boolean first) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        TagResolver name = Placeholder.unparsed("player", player.getName());
        Component titleLine = locale.render(first ? "lobby.join.first-title" : "lobby.join.title",
                playerLocale, name);
        Component subtitle = locale.render(first ? "lobby.join.first-subtitle" : "lobby.join.subtitle",
                playerLocale, name);
        player.showTitle(Title.title(titleLine, subtitle,
                Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT)));
        player.playSound(Sound.sound(Key.key("block.amethyst_block.chime"), Sound.Source.MASTER, 0.6f, 1.3f),
                Sound.Emitter.self());
    }
}
