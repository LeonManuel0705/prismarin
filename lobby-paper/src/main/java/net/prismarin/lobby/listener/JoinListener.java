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
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.network.ArrivalTracker;
import net.prismarin.lobby.api.LobbyEnteredEvent;
import net.prismarin.lobby.api.LobbyLeftEvent;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.LobbyPlayerSetup;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class JoinListener implements Listener {

    private static final Duration TITLE_FADE_IN = Duration.ofMillis(400);
    private static final Duration TITLE_STAY = Duration.ofMillis(2600);
    private static final Duration TITLE_FADE_OUT = Duration.ofMillis(600);

    private final LobbyWorlds worlds;
    private final LobbyDirectory lobbies;
    private final LobbyPlayerSetup setup;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final BuildModeService buildMode;
    private final PlayerHiderService hider;
    private final LobbySettings settings;
    private final ArrivalTracker arrivals;
    private final EventBus events;

    @Inject
    public JoinListener(LobbyWorlds worlds, LobbyDirectory lobbies, LobbyPlayerSetup setup, LocaleService locale,
                        ProfileService profiles, BuildModeService buildMode, PlayerHiderService hider,
                        LobbySettings settings, ArrivalTracker arrivals, EventBus events) {
        this.arrivals = arrivals;
        this.events = events;
        this.worlds = worlds;
        this.lobbies = lobbies;
        this.setup = setup;
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
        boolean startedInLobby = worlds.isLobbyWorld(player.getWorld());
        if (settings.enabled(LobbySettings.SPAWN_ON_JOIN)) {
            lobbies.joinSpawn(player).ifPresent(player::teleport);
        }
        if (!worlds.isLobbyWorld(player.getWorld())) {
            setup.removeItems(player);
            return;
        }
        setup.enter(player, startedInLobby);
        events.publish(new LobbyEnteredEvent(player.getUniqueId(), player.getWorld().getName(), "",
                LobbyEnteredEvent.Cause.JOIN));
        if (settings.enabled(LobbySettings.JOIN_TITLE)) {
            arrivals.whenKnown(player, previous -> {
                if (previous.isEmpty()) {
                    welcome(player, first);
                }
            });
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean wasLobby = worlds.isLobbyWorld(event.getFrom());
        boolean isLobby = worlds.isLobbyWorld(player.getWorld());
        if (isLobby && !wasLobby) {
            setup.enter(player, false);
        } else if (wasLobby && !isLobby) {
            setup.leave(player);
        }
        String from = event.getFrom().getName();
        String to = player.getWorld().getName();
        if (wasLobby) {
            events.publish(new LobbyLeftEvent(player.getUniqueId(), from, to, LobbyLeftEvent.Cause.WORLD_CHANGE));
        }
        if (isLobby) {
            events.publish(new LobbyEnteredEvent(player.getUniqueId(), to, from, LobbyEnteredEvent.Cause.WORLD_CHANGE));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (worlds.isLobbyWorld(event.getPlayer().getWorld())) {
            events.publish(new LobbyLeftEvent(event.getPlayer().getUniqueId(), event.getPlayer().getWorld().getName(),
                    "", LobbyLeftEvent.Cause.QUIT));
        }
        hider.clear(event.getPlayer());
        buildMode.clear(event.getPlayer().getUniqueId());
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
