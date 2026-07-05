package net.prismarin.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.locale.ProxyMessages;

public final class LobbyRedirectListener {

    private final ProxyServer proxy;
    private final ProxyConfigStore configStore;
    private final ProxyMessages messages;

    public LobbyRedirectListener(ProxyServer proxy, ProxyConfigStore configStore, ProxyMessages messages) {
        this.proxy = proxy;
        this.configStore = configStore;
        this.messages = messages;
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }
        String lobbyName = configStore.config().lobbyServer();
        if (event.getServer().getServerInfo().getName().equalsIgnoreCase(lobbyName)) {
            return;
        }
        Optional<RegisteredServer> lobby = proxy.getServer(lobbyName);
        if (lobby.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Component message = event.getServerKickReason()
                .map(reason -> messages.render(player, "kick.redirected-reason",
                        Placeholder.component("reason", reason)))
                .orElseGet(() -> messages.render(player, "kick.redirected"));
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(lobby.get(), message));
    }
}
