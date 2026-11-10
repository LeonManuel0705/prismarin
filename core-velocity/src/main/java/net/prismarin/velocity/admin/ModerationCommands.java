package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.moderation.Durations;

public final class ModerationCommands {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private ModerationCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand punish(AdminContext context, PunishmentType type) {
        String name = AdminContext.lower(type.name());
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!context.allowed(source, "prismarin.staff." + name)) {
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length < 2) {
                    context.send(source, "moderation." + name + "-usage");
                    return;
                }
                Optional<Duration> duration = type.lasting() ? Durations.parse(args[1]) : Optional.empty();
                int reasonStart = duration.isPresent() ? 2 : 1;
                if (args.length <= reasonStart) {
                    context.send(source, "moderation." + name + "-usage");
                    return;
                }
                String reason = String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length));
                if (type == PunishmentType.KICK && context.proxy().getPlayer(args[0]).isEmpty()) {
                    context.send(source, "staff.not-online", Placeholder.unparsed("player", args[0]));
                    return;
                }
                context.resolve(args[0]).thenAccept(found -> {
                    if (found.isEmpty()) {
                        context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", args[0]));
                        return;
                    }
                    AdminContext.Target target = found.get();
                    context.services().punishments().punish(target.uuid(), target.name(), type, reason,
                            AdminContext.actor(source), duration.orElse(null)).thenAccept(punishment -> {
                                apply(context, punishment);
                                context.services().audit().record(AdminContext.actor(source), name, target.name(),
                                        reason + duration.map(value -> " (" + args[1] + ")").orElse(""));
                                context.notifyStaff(StaffCommands.STAFF, "moderation.issued",
                                        resolvers(context, source, punishment));
                            });
                });
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return invocation.arguments().length <= 1
                        ? StaffCommands.playerNames(context, invocation.source(), invocation.arguments())
                        : List.of();
            }
        };
    }

    public static SimpleCommand revoke(AdminContext context, PunishmentType type) {
        String name = "un" + AdminContext.lower(type.name());
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff." + AdminContext.lower(type.name()))) {
                return;
            }
            if (invocation.arguments().length != 1) {
                context.send(source, "moderation." + name + "-usage");
                return;
            }
            String input = invocation.arguments()[0];
            context.resolve(input).thenAccept(found -> {
                if (found.isEmpty()) {
                    context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                    return;
                }
                AdminContext.Target target = found.get();
                context.services().punishments().revoke(target.uuid(), type, AdminContext.actor(source))
                        .thenAccept(revoked -> {
                            if (revoked.isEmpty()) {
                                context.send(source, "moderation.nothing-active",
                                        Placeholder.unparsed("player", target.name()));
                                return;
                            }
                            if (type == PunishmentType.MUTE) {
                                context.proxy().getPlayer(target.uuid()).ifPresent(context.channel()::sendUnmute);
                            }
                            context.services().audit().record(AdminContext.actor(source), name, target.name(), "");
                            context.notifyStaff(StaffCommands.STAFF, "moderation.revoked",
                                    Placeholder.unparsed("player", target.name()),
                                    Placeholder.unparsed("type", AdminContext.lower(type.name())),
                                    Placeholder.unparsed("actor", AdminContext.actor(source)));
                        });
            });
        };
    }

    public static SimpleCommand history(AdminContext context) {
        return invocation -> {
            CommandSource source = invocation.source();
            if (!context.allowed(source, "prismarin.staff.history")) {
                return;
            }
            if (invocation.arguments().length != 1) {
                context.send(source, "moderation.history-usage");
                return;
            }
            String input = invocation.arguments()[0];
            context.resolve(input).thenAccept(found -> {
                if (found.isEmpty()) {
                    context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                    return;
                }
                context.services().punishments().history(found.get().uuid()).thenAccept(entries -> {
                    context.send(source, "moderation.history-header",
                            Placeholder.unparsed("player", found.get().name()),
                            Placeholder.unparsed("count", String.valueOf(entries.size())));
                    entries.stream().limit(10).forEach(entry -> context.send(source, entry.inForce(now())
                            ? "moderation.history-entry-active" : "moderation.history-entry",
                            resolvers(context, source, entry)));
                });
            });
        };
    }

    static void apply(AdminContext context, Punishment punishment) {
        Optional<Player> online = context.proxy().getPlayer(punishment.target());
        if (online.isEmpty()) {
            return;
        }
        Player player = online.get();
        switch (punishment.type()) {
            case BAN -> player.disconnect(banScreen(context, player, punishment));
            case KICK -> player.disconnect(context.messages().render(player, "moderation.kick-screen",
                    resolvers(context, player, punishment)));
            case MUTE -> {
                context.channel().sendMute(player, punishment.expiresAt(), punishment.reason());
                context.send(player, "moderation.muted-notice", resolvers(context, player, punishment));
            }
            case WARN -> context.send(player, "moderation.warn-notice", resolvers(context, player, punishment));
        }
    }

    static Component banScreen(AdminContext context, CommandSource viewer, Punishment punishment) {
        return context.messages().render(viewer, punishment.permanent() ? "moderation.ban-screen"
                : "moderation.tempban-screen", resolvers(context, viewer, punishment));
    }

    static TagResolver[] resolvers(AdminContext context, CommandSource viewer, Punishment punishment) {
        return new TagResolver[]{
                Placeholder.unparsed("player", punishment.targetName()),
                Placeholder.unparsed("type", AdminContext.lower(punishment.type().name())),
                Placeholder.unparsed("reason", punishment.reason()),
                Placeholder.unparsed("actor", punishment.actor()),
                Placeholder.unparsed("id", punishment.id()),
                Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(punishment.createdAt()))),
                Placeholder.unparsed("remaining", punishment.permanent()
                        ? context.messages().locale().raw("moderation.permanent", context.messages().localeOf(viewer))
                        : Durations.format(punishment.expiresAt() - now()))
        };
    }

    static long now() {
        return System.currentTimeMillis();
    }

    static String typeKey(PunishmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
