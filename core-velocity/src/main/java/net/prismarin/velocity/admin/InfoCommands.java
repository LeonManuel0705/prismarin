package net.prismarin.velocity.admin;

import com.google.gson.JsonObject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.shared.admin.AuditLog;

public final class InfoCommands {

    public static final String NETEXEC_CHANNEL = "prismarin:netexec";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private InfoCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand playerInfo(AdminContext context) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!context.allowed(source, "prismarin.staff.pinfo")) {
                    return;
                }
                if (invocation.arguments().length != 1) {
                    context.send(source, "info.usage");
                    return;
                }
                String input = invocation.arguments()[0];
                context.resolve(input).thenAccept(found -> {
                    if (found.isEmpty()) {
                        context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                        return;
                    }
                    AdminContext.Target target = found.get();
                    Optional<Player> online = context.proxy().getPlayer(target.uuid());
                    CompletableFuture<Optional<List<AltAccount>>> alts = AltCommands.lookup(target.uuid());
                    context.services().profiles().find(target.uuid()).thenCombine(
                            context.services().punishments().history(target.uuid()), Map::entry)
                            .thenCombine(alts, (record, accounts) -> {
                                Optional<PlayerProfile> profile = record.getKey();
                                List<Punishment> history = record.getValue();
                                context.send(source, "info.header", Placeholder.unparsed("player", target.name()),
                                        Placeholder.unparsed("uuid", target.uuid().toString()));
                                context.send(source, online.isPresent() ? "info.online" : "info.offline",
                                        Placeholder.unparsed("server", online.map(StaffCommands::serverOf).orElse("-")),
                                        Placeholder.unparsed("date", profile.map(value -> DATE.format(
                                                value.getLastSeen())).orElse("-")));
                                profile.ifPresent(value -> {
                                    context.send(source, "info.profile",
                                            Placeholder.unparsed("first", DATE.format(value.getFirstJoin())),
                                            Placeholder.unparsed("level", String.valueOf(value.getLevel())),
                                            Placeholder.unparsed("locale", value.getLocale()));
                                    EconomyService economy = context.services().economy();
                                    if (economy.enabled()) {
                                        context.send(source, "info.balances", Placeholder.unparsed("balances",
                                                economy.currencies().stream()
                                                        .map(currency -> currency.id() + " " + value.balance(currency))
                                                        .collect(Collectors.joining(", "))));
                                    }
                                });
                                PlayerInfoLines.render(source, target.uuid()).forEach(source::sendMessage);
                                long now = System.currentTimeMillis();
                                context.send(source, "info.punishments",
                                        Placeholder.unparsed("count", String.valueOf(history.size())),
                                        Placeholder.unparsed("banned", String.valueOf(history.stream().anyMatch(entry ->
                                                entry.type() == PunishmentType.BAN && entry.inForce(now)))),
                                        Placeholder.unparsed("muted", String.valueOf(history.stream().anyMatch(entry ->
                                                entry.type() == PunishmentType.MUTE && entry.inForce(now)))));
                                accounts.ifPresent(linked -> context.send(source, "info.alts",
                                        Placeholder.component("alts", AltCommands.names(context, source, linked))));
                                return null;
                            });
                });
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return StaffCommands.playerNames(context, invocation.source(), invocation.arguments());
            }
        };
    }

    public static SimpleCommand audit(AdminContext context) {
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff.audit")) {
                return;
            }
            String target = invocation.arguments().length > 0 ? invocation.arguments()[0] : null;
            context.services().audit().latest(target, 15).thenAccept(entries -> {
                context.send(source, "audit.header", Placeholder.unparsed("count", String.valueOf(entries.size())));
                for (AuditLog.Entry entry : entries) {
                    context.send(source, "audit.entry",
                            Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(entry.at()))),
                            Placeholder.unparsed("actor", entry.actor()),
                            Placeholder.unparsed("action", entry.action()),
                            Placeholder.unparsed("target", entry.target()),
                            Placeholder.unparsed("details", entry.details()));
                }
            });
        };
    }

    public static SimpleCommand alert(AdminContext context) {
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff.alert")) {
                return;
            }
            String[] args = invocation.arguments();
            if (args.length == 0) {
                context.send(source, "alert.usage");
                return;
            }
            int start = 0;
            boolean title = false;
            String scope = "all";
            while (start < args.length && (args[start].equals("-t") || args[start].startsWith("server:")
                    || args[start].startsWith("group:"))) {
                if (args[start].equals("-t")) {
                    title = true;
                } else {
                    scope = args[start];
                }
                start++;
            }
            if (start >= args.length) {
                context.send(source, "alert.usage");
                return;
            }
            String text = String.join(" ", Arrays.copyOfRange(args, start, args.length));
            String selectedScope = scope;
            List<Player> receivers = context.proxy().getAllPlayers().stream()
                    .filter(player -> matches(context, player, selectedScope))
                    .toList();
            Component message = context.services().locale().format(text);
            for (Player player : receivers) {
                context.send(player, "alert.message", Placeholder.component("message", message));
                if (title) {
                    player.showTitle(Title.title(context.messages().render(player, "alert.title"), message,
                            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
                }
            }
            context.services().audit().record(AdminContext.actor(source), "alert", scope, text);
            context.send(source, "alert.sent", Placeholder.unparsed("count", String.valueOf(receivers.size())));
        };
    }

    public static SimpleCommand netExec(AdminContext context) {
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff.netexec")) {
                return;
            }
            String[] args = invocation.arguments();
            if (args.length < 2) {
                context.send(source, "netexec.usage");
                return;
            }
            if (!context.services().config().redisEnabled()) {
                context.send(source, "netexec.needs-redis");
                return;
            }
            String command = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            JsonObject payload = new JsonObject();
            payload.addProperty("target", args[0]);
            payload.addProperty("command", command);
            payload.addProperty("actor", AdminContext.actor(source));
            context.services().messageBus().publish(NETEXEC_CHANNEL, payload.toString());
            context.services().audit().record(AdminContext.actor(source), "netexec", args[0], command);
            context.send(source, "netexec.sent", Placeholder.unparsed("target", args[0]),
                    Placeholder.unparsed("command", command));
        };
    }

    static boolean matches(AdminContext context, Player player, String scope) {
        if (scope.equals("all")) {
            return true;
        }
        String server = StaffCommands.serverOf(player);
        if (scope.startsWith("server:")) {
            return server.equalsIgnoreCase(scope.substring("server:".length()));
        }
        String group = scope.substring("group:".length());
        return context.directory().byName(server).map(NetworkServer::group).filter(group::equalsIgnoreCase)
                .isPresent();
    }
}
