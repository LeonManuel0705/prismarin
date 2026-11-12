package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.AltService;
import net.prismarin.api.moderation.ModerationApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AltCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");
    private static final String FALLBACK_DATE = "yyyy-MM-dd HH:mm";
    private static final int INLINE_LIMIT = 6;

    private AltCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand alts(AdminContext context, AltService service) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                if (!context.allowed(source, AltTracker.STAFF)) {
                    return;
                }
                if (invocation.arguments().length != 1) {
                    context.send(source, "alts.usage");
                    return;
                }
                String input = invocation.arguments()[0];
                context.resolve(input).thenAccept(found -> {
                    if (found.isEmpty()) {
                        context.send(source, "moderation.unknown-player", Placeholder.unparsed("player", input));
                        return;
                    }
                    AdminContext.Target target = found.get();
                    service.alts(target.uuid()).thenAccept(alts -> show(context, source, target, alts))
                            .exceptionally(error -> {
                                LOGGER.warn("Looking up the alts of {} failed", target.name(), error);
                                context.send(source, "punishment.failed");
                                return null;
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

    static CompletableFuture<Optional<List<AltAccount>>> lookup(UUID player) {
        return ModerationApi.alts()
                .map(service -> service.alts(player).<Optional<List<AltAccount>>>thenApply(Optional::of)
                        .exceptionally(error -> Optional.of(List.of())))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
    }

    static Component names(AdminContext context, CommandSource viewer, List<AltAccount> alts) {
        if (alts.isEmpty()) {
            return context.messages().render(viewer, "alts.inline-none");
        }
        Component line = Component.empty();
        boolean check = context.proxy().getCommandManager().hasCommand("check");
        for (int index = 0; index < Math.min(INLINE_LIMIT, alts.size()); index++) {
            AltAccount alt = alts.get(index);
            if (index > 0) {
                line = line.append(context.messages().render(viewer, "alts.inline-separator"));
            }
            String key = alt.banned() ? "alts.inline-banned" : alt.muted() ? "alts.inline-muted" : "alts.inline";
            Component name = context.messages().render(viewer, key, Placeholder.unparsed("player", name(alt)));
            line = line.append(check ? name.clickEvent(ClickEvent.runCommand("/check " + name(alt))) : name);
        }
        if (alts.size() > INLINE_LIMIT) {
            line = line.append(context.messages().render(viewer, "alts.inline-more",
                    Placeholder.unparsed("count", String.valueOf(alts.size() - INLINE_LIMIT))));
        }
        return line;
    }

    static String name(AltAccount alt) {
        return alt.name().isBlank() ? alt.uuid().toString().substring(0, 8) : alt.name();
    }

    static String date(AdminContext context, CommandSource viewer, long millis) {
        if (millis <= 0L) {
            return "-";
        }
        DateTimeFormatter format;
        try {
            format = DateTimeFormatter.ofPattern(context.messages().locale().raw("punishment.date-format",
                    context.messages().localeOf(viewer)));
        } catch (IllegalArgumentException e) {
            format = DateTimeFormatter.ofPattern(FALLBACK_DATE);
        }
        return format.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
    }

    private static void show(AdminContext context, CommandSource source, AdminContext.Target target,
                             List<AltAccount> alts) {
        if (alts.isEmpty()) {
            context.send(source, "alts.none", Placeholder.unparsed("player", target.name()));
            return;
        }
        context.send(source, "alts.header", Placeholder.unparsed("player", target.name()),
                Placeholder.unparsed("count", String.valueOf(alts.size())));
        boolean check = context.proxy().getCommandManager().hasCommand("check");
        for (AltAccount alt : alts) {
            Optional<Player> online = context.proxy().getPlayer(alt.uuid())
                    .filter(player -> context.vanish().canSee(source, player));
            Component status = online.isPresent()
                    ? context.messages().render(source, "alts.online", Placeholder.unparsed("server",
                            StaffCommands.serverOf(online.get())))
                    : context.messages().render(source, "alts.offline", Placeholder.unparsed("date",
                            date(context, source, alt.lastSeen())));
            Component marks = Component.empty();
            if (alt.banned()) {
                marks = marks.append(context.messages().render(source, "alts.banned"));
            }
            if (alt.muted()) {
                marks = marks.append(context.messages().render(source, "alts.muted"));
            }
            Component line = context.messages().render(source, "alts.entry", Placeholder.component("status", status),
                            Placeholder.unparsed("player", name(alt)),
                            Placeholder.unparsed("shared", String.valueOf(alt.sharedAddresses())),
                            Placeholder.component("marks", marks))
                    .hoverEvent(HoverEvent.showText(context.messages().render(source, "alts.hover",
                            Placeholder.unparsed("first", date(context, source, alt.firstSeen())),
                            Placeholder.unparsed("last", date(context, source, alt.lastSeen())),
                            Placeholder.unparsed("shared", String.valueOf(alt.sharedAddresses())))));
            source.sendMessage(check ? line.clickEvent(ClickEvent.runCommand("/check " + name(alt))) : line);
        }
    }
}
