package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class StaffCommands {

    public static final String STAFF = "prismarin.staff";

    private StaffCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand staffChat(AdminContext context) {
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff.chat")) {
                return;
            }
            if (invocation.arguments().length == 0) {
                context.send(source, "staff.chat-usage");
                return;
            }
            String server = source instanceof Player player
                    ? player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("proxy")
                    : "console";
            context.notifyStaff("prismarin.staff.chat", "staff.chat",
                    Placeholder.unparsed("player", AdminContext.actor(source)),
                    Placeholder.unparsed("server", server),
                    Placeholder.unparsed("message", String.join(" ", invocation.arguments())));
        };
    }

    public static SimpleCommand team(AdminContext context) {
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff.list")) {
                return;
            }
            List<Player> staff = context.proxy().getAllPlayers().stream()
                    .filter(player -> player.hasPermission(STAFF))
                    .filter(player -> context.vanish().canSee(source, player))
                    .sorted((first, second) -> first.getUsername().compareToIgnoreCase(second.getUsername()))
                    .toList();
            context.send(source, "staff.team-header", Placeholder.unparsed("count", String.valueOf(staff.size())));
            for (Player player : staff) {
                context.send(source, context.vanish().vanished(player.getUniqueId()) ? "staff.team-entry-vanished"
                                : "staff.team-entry",
                        Placeholder.unparsed("player", player.getUsername()),
                        Placeholder.unparsed("server", serverOf(player)));
            }
        };
    }

    public static SimpleCommand find(AdminContext context) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!context.allowed(source, "prismarin.staff.find")) {
                    return;
                }
                if (invocation.arguments().length != 1) {
                    context.send(source, "staff.find-usage");
                    return;
                }
                Optional<Player> target = context.proxy().getPlayer(invocation.arguments()[0])
                        .filter(player -> context.vanish().canSee(source, player));
                if (target.isEmpty()) {
                    context.send(source, "staff.not-online",
                            Placeholder.unparsed("player", invocation.arguments()[0]));
                    return;
                }
                context.send(source, "staff.find", Placeholder.unparsed("player", target.get().getUsername()),
                        Placeholder.unparsed("server", serverOf(target.get())));
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return playerNames(context, invocation.source(), invocation.arguments());
            }
        };
    }

    public static SimpleCommand goTo(AdminContext context) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!(source instanceof Player self)) {
                    context.send(source, "general.players-only");
                    return;
                }
                if (!context.allowed(source, "prismarin.staff.goto")) {
                    return;
                }
                if (invocation.arguments().length != 1) {
                    context.send(source, "staff.goto-usage");
                    return;
                }
                Optional<RegisteredServer> target = context.proxy().getPlayer(invocation.arguments()[0])
                        .filter(player -> context.vanish().canSee(source, player))
                        .flatMap(Player::getCurrentServer)
                        .map(connection -> connection.getServer());
                if (target.isEmpty()) {
                    context.send(source, "staff.not-online",
                            Placeholder.unparsed("player", invocation.arguments()[0]));
                    return;
                }
                self.createConnectionRequest(target.get()).fireAndForget();
                context.send(source, "staff.goto", Placeholder.unparsed("server",
                        target.get().getServerInfo().getName()));
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return playerNames(context, invocation.source(), invocation.arguments());
            }
        };
    }

    public static SimpleCommand send(AdminContext context) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!context.allowed(source, "prismarin.staff.send")) {
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length != 2) {
                    context.send(source, "staff.send-usage");
                    return;
                }
                Collection<Player> players = selectPlayers(context, args[0]);
                if (players.isEmpty()) {
                    context.send(source, "staff.not-online", Placeholder.unparsed("player", args[0]));
                    return;
                }
                String destination = args[1];
                boolean group = destination.startsWith("group:");
                String name = group ? destination.substring("group:".length()) : destination;
                if (!group && context.proxy().getServer(name).isEmpty()) {
                    context.send(source, "staff.unknown-server", Placeholder.unparsed("server", name));
                    return;
                }
                for (Player player : players) {
                    if (group) {
                        context.connector().connectToGroup(player.getUniqueId(), name);
                    } else {
                        context.connector().connect(player.getUniqueId(), name);
                    }
                }
                context.services().audit().record(AdminContext.actor(source), "send", args[0], destination);
                context.send(source, "staff.sent", Placeholder.unparsed("count", String.valueOf(players.size())),
                        Placeholder.unparsed("server", destination));
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                String[] args = invocation.arguments();
                if (args.length <= 1) {
                    List<String> options = new ArrayList<>(List.of("all"));
                    context.proxy().getAllServers().forEach(server ->
                            options.add("server:" + server.getServerInfo().getName()));
                    options.addAll(playerNames(context, invocation.source(), args));
                    return filter(options, args.length == 0 ? "" : args[0]);
                }
                List<String> options = new ArrayList<>();
                context.proxy().getAllServers().forEach(server -> options.add(server.getServerInfo().getName()));
                context.directory().groups().forEach(group -> options.add("group:" + group.name()));
                return filter(options, args[1]);
            }
        };
    }

    static Collection<Player> selectPlayers(AdminContext context, String selector) {
        if (selector.equalsIgnoreCase("all")) {
            return context.proxy().getAllPlayers();
        }
        if (selector.startsWith("server:")) {
            return context.proxy().getServer(selector.substring("server:".length()))
                    .map(RegisteredServer::getPlayersConnected)
                    .orElse(List.of());
        }
        return context.proxy().getPlayer(selector).map(List::of).orElse(List.of());
    }

    static String serverOf(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("-");
    }

    static List<String> playerNames(AdminContext context, CommandSource source, String[] args) {
        String input = args.length == 0 ? "" : args[args.length - 1];
        return filter(context.proxy().getAllPlayers().stream()
                .filter(player -> context.vanish().canSee(source, player))
                .map(Player::getUsername)
                .toList(), input);
    }

    static List<String> filter(List<String> options, String input) {
        String lower = AdminContext.lower(input);
        return options.stream().filter(option -> AdminContext.lower(option).startsWith(lower)).sorted().toList();
    }
}
