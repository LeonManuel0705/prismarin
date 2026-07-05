package net.prismarin.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.velocity.locale.ProxyMessages;

public final class SendAllCommand implements SimpleCommand {

    private static final String PERMISSION = "prismarin.admin.sendall";

    private final ProxyServer proxy;
    private final ProxyMessages messages;

    public SendAllCommand(ProxyServer proxy, ProxyMessages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(PERMISSION)) {
            messages.send(source, "general.no-permission");
            return;
        }
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messages.send(source, "sendall.usage");
            return;
        }
        Optional<RegisteredServer> target = proxy.getServer(args[0]);
        if (target.isEmpty()) {
            messages.send(source, "sendall.unknown-server", Placeholder.unparsed("server", args[0]));
            return;
        }
        RegisteredServer server = target.get();
        String serverName = server.getServerInfo().getName();
        int moved = 0;
        for (Player player : proxy.getAllPlayers()) {
            boolean alreadyThere = player.getCurrentServer()
                    .map(current -> current.getServerInfo().getName().equalsIgnoreCase(serverName))
                    .orElse(false);
            if (alreadyThere) {
                continue;
            }
            player.createConnectionRequest(server).fireAndForget();
            moved++;
        }
        messages.send(source, "sendall.success",
                Placeholder.unparsed("count", Integer.toString(moved)),
                Placeholder.unparsed("server", serverName));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission(PERMISSION)) {
            return List.of();
        }
        String[] args = invocation.arguments();
        if (args.length > 1) {
            return List.of();
        }
        String current = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return proxy.getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(current))
                .sorted()
                .toList();
    }
}
