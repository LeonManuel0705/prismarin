package net.prismarin.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import net.prismarin.velocity.locale.ProxyMessages;
import net.prismarin.velocity.server.LobbyBalancer;

public final class HubCommand implements SimpleCommand {

    private final LobbyBalancer lobbies;
    private final ProxyMessages messages;

    public HubCommand(LobbyBalancer lobbies, ProxyMessages messages) {
        this.lobbies = lobbies;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            messages.send(invocation.source(), "general.players-only");
            return;
        }
        boolean alreadyThere = player.getCurrentServer()
                .map(current -> lobbies.isLobby(current.getServerInfo().getName()))
                .orElse(false);
        if (alreadyThere) {
            messages.send(player, "hub.already-there");
            return;
        }
        Optional<RegisteredServer> lobby = lobbies.pick(player, null);
        if (lobby.isEmpty()) {
            messages.send(player, "hub.unreachable");
            return;
        }
        player.createConnectionRequest(lobby.get()).connect().whenComplete((result, throwable) -> {
            if (throwable != null || result == null) {
                messages.send(player, "hub.unreachable");
                return;
            }
            switch (result.getStatus()) {
                case SUCCESS -> messages.send(player, "hub.connected");
                case ALREADY_CONNECTED -> messages.send(player, "hub.already-there");
                default -> messages.send(player, "hub.unreachable");
            }
        });
    }
}
