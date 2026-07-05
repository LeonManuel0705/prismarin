package net.prismarin.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.locale.ProxyMessages;

public final class HubCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final ProxyConfigStore configStore;
    private final ProxyMessages messages;

    public HubCommand(ProxyServer proxy, ProxyConfigStore configStore, ProxyMessages messages) {
        this.proxy = proxy;
        this.configStore = configStore;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            messages.send(invocation.source(), "general.players-only");
            return;
        }
        String lobbyName = configStore.config().lobbyServer();
        Optional<RegisteredServer> lobby = proxy.getServer(lobbyName);
        if (lobby.isEmpty()) {
            messages.send(player, "hub.unreachable");
            return;
        }
        boolean alreadyThere = player.getCurrentServer()
                .map(current -> current.getServerInfo().getName().equalsIgnoreCase(lobbyName))
                .orElse(false);
        if (alreadyThere) {
            messages.send(player, "hub.already-there");
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
