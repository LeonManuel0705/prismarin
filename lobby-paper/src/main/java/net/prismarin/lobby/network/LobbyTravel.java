package net.prismarin.lobby.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.WorldLobby;
import net.prismarin.lobby.network.LobbyTarget.ServerTarget;
import net.prismarin.lobby.network.LobbyTarget.WorldTarget;
import net.prismarin.lobby.server.ServerConnector;
import net.prismarin.lobby.world.LobbyWorlds;
import net.prismarin.shared.status.QueueingConnector;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Singleton
public final class LobbyTravel {

    private static final Sound TELEPORT = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER,
            0.7f, 1.2f);
    private static final Sound DENY = Sound.sound(Key.key("block.note_block.bass"), Sound.Source.MASTER,
            0.6f, 0.7f);

    private final LobbyPlugin plugin;
    private final LobbyDirectory lobbies;
    private final LobbyWorlds worlds;
    private final ServerConnector connector;
    private final QueueingConnector network;
    private final ServerDirectory directory;
    private final LocaleService locale;
    private final ProfileService profiles;

    @Inject
    public LobbyTravel(LobbyPlugin plugin, LobbyDirectory lobbies, LobbyWorlds worlds, ServerConnector connector,
                       QueueingConnector network, ServerDirectory directory, LocaleService locale,
                       ProfileService profiles) {
        this.plugin = plugin;
        this.lobbies = lobbies;
        this.worlds = worlds;
        this.connector = connector;
        this.network = network;
        this.directory = directory;
        this.locale = locale;
        this.profiles = profiles;
    }

    public void travel(Player player, LobbyTarget target) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Component name = name(target);
        if (lobbies.isCurrent(player, target)) {
            player.sendActionBar(locale.render("lobby.selector.already-here", playerLocale,
                    Placeholder.component("lobby", name)));
            return;
        }
        if (!lobbies.permitted(player, target)) {
            player.sendMessage(locale.render("lobby.selector.no-permission", playerLocale,
                    Placeholder.component("lobby", name)));
            deny(player);
            return;
        }
        switch (target) {
            case WorldTarget world -> teleport(player, world.lobby());
            case ServerTarget server -> {
                if (!lobbies.reachable(server.server())) {
                    player.sendMessage(locale.render("lobby.selector.unreachable", playerLocale,
                            Placeholder.component("lobby", name)));
                    deny(player);
                    return;
                }
                connect(player, server.server());
            }
        }
    }

    public void teleport(Player player, WorldLobby lobby) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Component name = locale.format(lobby.displayName());
        Optional<Location> spawn = worlds.spawn(lobby.world());
        if (spawn.isEmpty()) {
            player.sendMessage(locale.render("lobby.selector.no-spawn", playerLocale,
                    Placeholder.component("lobby", name)));
            deny(player);
            return;
        }
        player.teleport(spawn.get());
        player.playSound(TELEPORT, Sound.Emitter.self());
        player.sendMessage(locale.render("lobby.selector.teleported", playerLocale,
                Placeholder.component("lobby", name)));
    }

    public void connect(Player player, NetworkServer server) {
        network.travel(player.getUniqueId(), QueueTarget.server(server.name())).thenAccept(result -> plugin
                .getServer().getScheduler().runTask(plugin, () -> arrived(player, server.name(), result)));
    }

    public void connect(Player player, String serverName) {
        Optional<NetworkServer> known = directory.byName(serverName);
        if (known.isPresent()) {
            connect(player, known.get());
            return;
        }
        Locale playerLocale = PlayerLocales.of(player, profiles);
        player.sendMessage(locale.render("lobby.navigator.connecting", playerLocale,
                Placeholder.unparsed("server", serverName)));
        player.playSound(TELEPORT, Sound.Emitter.self());
        connector.connect(player, serverName);
    }

    public void playGroup(Player player, String group) {
        network.travel(player.getUniqueId(), QueueTarget.group(group)).thenAccept(result -> plugin.getServer()
                .getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || result.waiting() || result.status() == QueueStatus.NOT_LEADER) {
                        return;
                    }
                    if (!result.sent()) {
                        player.sendMessage(locale.render("play.no-server", PlayerLocales.of(player, profiles),
                                Placeholder.unparsed("group", group)));
                        deny(player);
                        return;
                    }
                    arrived(player, result.server().orElse(group), result);
                }));
    }

    private void arrived(Player player, String fallback, QueueResult result) {
        if (!player.isOnline() || result.waiting() || result.status() == QueueStatus.NOT_LEADER) {
            return;
        }
        Locale playerLocale = PlayerLocales.of(player, profiles);
        if (result.status() == QueueStatus.ALREADY_THERE) {
            player.sendActionBar(locale.render("lobby.navigator.already-here", playerLocale));
            return;
        }
        String name = result.server().orElse(fallback);
        Component shown = directory.byName(name).map(server -> locale.format(server.displayName()))
                .orElseGet(() -> Component.text(name));
        if (!result.sent()) {
            player.sendMessage(locale.render("lobby.selector.unreachable", playerLocale,
                    Placeholder.component("lobby", shown)));
            deny(player);
            return;
        }
        player.sendMessage(locale.render("lobby.navigator.connecting", playerLocale,
                Placeholder.component("server", shown)));
        player.playSound(TELEPORT, Sound.Emitter.self());
    }

    public void premium(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Optional<LobbyTarget> target = lobbies.premium(player);
        if (target.isEmpty()) {
            player.sendMessage(locale.render("lobby.premium.none", playerLocale));
            deny(player);
            return;
        }
        if (lobbies.isCurrent(player, target.get())) {
            player.sendActionBar(locale.render("lobby.premium.already-here", playerLocale));
            return;
        }
        if (!lobbies.permitted(player, target.get())) {
            player.sendMessage(locale.render("lobby.premium.locked", playerLocale));
            deny(player);
            return;
        }
        travel(player, target.get());
    }

    public Component name(LobbyTarget target) {
        return locale.format(target.displayName());
    }

    public void deny(Player player) {
        player.playSound(DENY, Sound.Emitter.self());
    }
}
