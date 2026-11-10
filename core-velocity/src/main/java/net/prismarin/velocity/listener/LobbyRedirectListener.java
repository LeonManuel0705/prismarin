package net.prismarin.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.velocity.locale.ProxyMessages;
import net.prismarin.velocity.server.LobbyBalancer;

public final class LobbyRedirectListener {

    private final LobbyBalancer lobbies;
    private final ProxyMessages messages;

    public LobbyRedirectListener(LobbyBalancer lobbies, ProxyMessages messages) {
        this.lobbies = lobbies;
        this.messages = messages;
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }
        String kickedFrom = event.getServer().getServerInfo().getName();
        Player player = event.getPlayer();
        Optional<RegisteredServer> lobby = lobbies.pick(player, kickedFrom);
        if (lobby.isEmpty()) {
            return;
        }
        Component message = event.getServerKickReason()
                .map(reason -> messages.render(player, "kick.redirected-reason",
                        Placeholder.component("reason", reason)))
                .orElseGet(() -> messages.render(player, "kick.redirected"));
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(lobby.get(), message));
    }
}
