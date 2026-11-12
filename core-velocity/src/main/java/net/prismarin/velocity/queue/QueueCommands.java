package net.prismarin.velocity.queue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.queue.QueueEntry;
import net.prismarin.api.queue.QueueInfo;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.velocity.admin.DurationText;
import net.prismarin.velocity.locale.ProxyMessages;
import net.prismarin.velocity.social.SubCommands;

public final class QueueCommands {

    static final int LIST_LIMIT = 15;
    static final Set<String> STAFF_ACTIONS = Set.of("list", "pause", "resume", "clear", "send");
    static final Set<String> PLAYER_ACTIONS = Set.of("join", "leave");

    private QueueCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand queue(QueueManager queue, ProxyMessages messages, Clock clock) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                String[] args = invocation.arguments();
                String label = "/" + invocation.alias();
                SubCommands sub = queue.settings().subCommands();
                if (args.length == 0) {
                    status(queue, messages, source, label);
                    return;
                }
                String action = args[0].toLowerCase(Locale.ROOT);
                if (!sub.actions().contains(action) || !allowed(sub, source, action)) {
                    help(messages, source, label, visible(sub, source));
                    return;
                }
                switch (action) {
                    case "join" -> join(queue, messages, source, args, label);
                    case "leave" -> leave(queue, messages, source, args.length > 1 ? args[1] : null);
                    case "list" -> list(queue, messages, source, args.length > 1 ? args[1] : null, clock);
                    case "pause", "resume" -> pause(queue, messages, source, args, label, "pause".equals(action));
                    case "clear" -> clear(queue, messages, source, args, label);
                    case "send" -> sendNext(queue, messages, source, args, label);
                    default -> help(messages, source, label, visible(sub, source));
                }
            }

            @Override
            public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
                CommandSource source = invocation.source();
                String[] args = invocation.arguments();
                SubCommands sub = queue.settings().subCommands();
                if (args.length <= 1) {
                    return CompletableFuture.completedFuture(filter(visible(sub, source),
                            args.length == 0 ? "" : args[0]));
                }
                String action = args[0].toLowerCase(Locale.ROOT);
                if (args.length > 2 || !sub.actions().contains(action) || !allowed(sub, source, action)) {
                    return CompletableFuture.completedFuture(List.of());
                }
                String prefix = args[1];
                return CompletableFuture.completedFuture(switch (action) {
                    case "join" -> filter(queue.targetNames(), prefix);
                    case "leave" -> source instanceof Player player
                            ? filter(queue.positions(player.getUniqueId()).join().stream()
                                    .map(position -> position.target().name()).distinct().toList(), prefix)
                            : List.of();
                    case "list", "pause", "resume", "clear", "send" -> {
                        Set<String> options = new LinkedHashSet<>(queue.queuedNames());
                        options.addAll(queue.targetNames());
                        yield filter(List.copyOf(options), prefix);
                    }
                    default -> List.<String>of();
                });
            }
        };
    }

    public static SimpleCommand leaveShortcut(QueueManager queue, ProxyMessages messages) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                leave(queue, messages, invocation.source(), null);
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                return List.of();
            }
        };
    }

    static boolean allowed(SubCommands sub, CommandSource source, String action) {
        if (!sub.enabled(action)) {
            return false;
        }
        if (STAFF_ACTIONS.contains(action)) {
            return source.hasPermission(QueueSettings.staffPermission(action));
        }
        return !PLAYER_ACTIONS.contains(action) || source instanceof Player;
    }

    static List<String> visible(SubCommands sub, CommandSource source) {
        List<String> visible = new ArrayList<>();
        for (String action : sub.visible()) {
            if (allowed(sub, source, action)) {
                visible.add(action);
            }
        }
        return List.copyOf(visible);
    }

    private static void status(QueueManager queue, ProxyMessages messages, CommandSource source, String label) {
        if (!(source instanceof Player player)) {
            help(messages, source, label, visible(queue.settings().subCommands(), source));
            return;
        }
        List<QueuePosition> positions = queue.positions(player.getUniqueId()).join();
        if (positions.isEmpty()) {
            messages.send(player, "queue.status-none");
            help(messages, source, label, visible(queue.settings().subCommands(), source));
            return;
        }
        for (QueuePosition position : positions) {
            TagResolver[] resolvers = queue.resolvers(position);
            String key = position.paused() ? "queue.status-paused" : "queue.status";
            messages.send(player, key, resolvers);
            if (!position.party().isEmpty()) {
                messages.send(player, position.owner().equals(player.getUniqueId()) ? "queue.status-party"
                        : "queue.status-member", with(resolvers,
                        Placeholder.unparsed("player", queue.nameOf(position.owner()))));
            }
        }
    }

    private static void join(QueueManager queue, ProxyMessages messages, CommandSource source, String[] args,
                             String label) {
        Player player = (Player) source;
        if (args.length != 2) {
            messages.send(player, "queue.usage-join", Placeholder.unparsed("command", label));
            return;
        }
        Optional<QueueTarget> target = queue.resolve(args[1]);
        if (target.isEmpty()) {
            messages.send(player, "queue.unknown-target", Placeholder.unparsed("target", args[1]));
            return;
        }
        QueueResult result = queue.join(player.getUniqueId(), target.get()).join();
        TagResolver name = Placeholder.component("target", queue.targetName(target.get()));
        switch (result.status()) {
            case SENT -> messages.send(player, "queue.sending", Placeholder.component("server",
                    queue.targetName(QueueTarget.server(result.server().orElse(target.get().name())))));
            case ALREADY_THERE -> messages.send(player, "queue.already-there", name);
            case NO_SERVER -> messages.send(player, "queue.no-server", name);
            case UNKNOWN_TARGET -> messages.send(player, "queue.unknown-target",
                    Placeholder.unparsed("target", args[1]));
            case QUEUED, ALREADY_QUEUED, NOT_LEADER -> {
            }
            default -> messages.send(player, "queue.failed", name);
        }
    }

    static void leave(QueueManager queue, ProxyMessages messages, CommandSource source, String input) {
        if (!(source instanceof Player player)) {
            messages.send(source, "general.players-only");
            return;
        }
        List<QueuePosition> positions = queue.positions(player.getUniqueId()).join();
        List<QueueTarget> targets = new ArrayList<>();
        for (QueuePosition position : positions) {
            if (input == null || position.target().name().equalsIgnoreCase(input)
                    || position.target().id().equalsIgnoreCase(input)) {
                targets.add(position.target());
            }
        }
        if (targets.isEmpty()) {
            messages.send(player, "queue.not-queued");
            return;
        }
        for (QueueTarget target : targets) {
            if (queue.leave(player.getUniqueId(), target).join()) {
                messages.send(player, "queue.left", Placeholder.component("target", queue.targetName(target)));
            }
        }
    }

    private static void list(QueueManager queue, ProxyMessages messages, CommandSource source, String input,
                             Clock clock) {
        if (input == null) {
            List<QueueInfo> infos = queue.queues().join();
            if (infos.isEmpty()) {
                messages.send(source, "queue.list-empty");
                return;
            }
            messages.send(source, "queue.list-header", Placeholder.unparsed("count", String.valueOf(infos.size())));
            for (QueueInfo info : infos) {
                messages.send(source, info.paused() ? "queue.list-entry-paused" : "queue.list-entry",
                        Placeholder.component("target", queue.targetName(info.target())),
                        Placeholder.unparsed("id", info.target().id()),
                        Placeholder.unparsed("size", String.valueOf(info.size())),
                        Placeholder.unparsed("players", String.valueOf(info.players())));
            }
            return;
        }
        Optional<QueueInfo> found = target(queue, input).flatMap(target -> queue.queue(target).join());
        if (found.isEmpty()) {
            messages.send(source, "queue.unknown-target", Placeholder.unparsed("target", input));
            return;
        }
        QueueInfo info = found.get();
        messages.send(source, info.paused() ? "queue.list-target-paused" : "queue.list-target",
                Placeholder.component("target", queue.targetName(info.target())),
                Placeholder.unparsed("id", info.target().id()),
                Placeholder.unparsed("size", String.valueOf(info.size())),
                Placeholder.unparsed("players", String.valueOf(info.players())));
        long now = clock.millis();
        int shown = 0;
        for (QueueEntry entry : info.entries()) {
            if (shown++ >= LIST_LIMIT) {
                break;
            }
            messages.send(source, entry.online() ? "queue.list-player" : "queue.list-player-away",
                    Placeholder.unparsed("position", String.valueOf(entry.position())),
                    Placeholder.unparsed("player", entry.name()),
                    Placeholder.unparsed("priority", String.valueOf(entry.priority())),
                    Placeholder.unparsed("party", String.valueOf(entry.unitSize())),
                    Placeholder.unparsed("waited", DurationText.compact(Math.max(1_000L, now - entry.joinedAt()))));
        }
        if (info.entries().size() > LIST_LIMIT) {
            messages.send(source, "queue.list-more",
                    Placeholder.unparsed("count", String.valueOf(info.entries().size() - LIST_LIMIT)));
        }
    }

    private static void pause(QueueManager queue, ProxyMessages messages, CommandSource source, String[] args,
                              String label, boolean pause) {
        if (args.length != 2) {
            messages.send(source, pause ? "queue.usage-pause" : "queue.usage-resume",
                    Placeholder.unparsed("command", label));
            return;
        }
        Optional<QueueTarget> target = target(queue, args[1]);
        if (target.isEmpty()) {
            messages.send(source, "queue.unknown-target", Placeholder.unparsed("target", args[1]));
            return;
        }
        String actor = source instanceof Player player ? player.getUsername() : "Console";
        boolean changed = (pause ? queue.pause(target.get(), actor) : queue.resume(target.get(), actor)).join();
        TagResolver name = Placeholder.component("target", queue.targetName(target.get()));
        if (pause) {
            messages.send(source, changed ? "queue.paused-staff" : "queue.already-paused", name);
        } else {
            messages.send(source, changed ? "queue.resumed-staff" : "queue.not-paused", name);
        }
    }

    private static void clear(QueueManager queue, ProxyMessages messages, CommandSource source, String[] args,
                              String label) {
        if (args.length != 2) {
            messages.send(source, "queue.usage-clear", Placeholder.unparsed("command", label));
            return;
        }
        Optional<QueueTarget> target = target(queue, args[1]);
        if (target.isEmpty()) {
            messages.send(source, "queue.unknown-target", Placeholder.unparsed("target", args[1]));
            return;
        }
        int count = queue.clear(target.get()).join();
        messages.send(source, "queue.cleared", Placeholder.component("target", queue.targetName(target.get())),
                Placeholder.unparsed("count", String.valueOf(count)));
    }

    private static void sendNext(QueueManager queue, ProxyMessages messages, CommandSource source, String[] args,
                                 String label) {
        if (args.length != 2) {
            messages.send(source, "queue.usage-send", Placeholder.unparsed("command", label));
            return;
        }
        Optional<QueueTarget> target = target(queue, args[1]);
        if (target.isEmpty()) {
            messages.send(source, "queue.unknown-target", Placeholder.unparsed("target", args[1]));
            return;
        }
        TagResolver name = Placeholder.component("target", queue.targetName(target.get()));
        queue.sendNext(target.get()).join().ifPresentOrElse(
                player -> messages.send(source, "queue.sent-next", name,
                        Placeholder.unparsed("player", queue.nameOf(player))),
                () -> messages.send(source, "queue.nothing-to-send", name));
    }

    private static Optional<QueueTarget> target(QueueManager queue, String input) {
        Optional<QueueTarget> resolved = queue.resolve(input);
        if (resolved.isPresent()) {
            return resolved;
        }
        return QueueTarget.parse(input).filter(target -> queue.queuedNames().contains(target.id()));
    }

    static void help(ProxyMessages messages, CommandSource source, String label, List<String> actions) {
        messages.send(source, "queue.help-header");
        if (source instanceof Player) {
            messages.send(source, "queue.help.status", Placeholder.unparsed("command", label));
        }
        for (String action : actions) {
            messages.send(source, "queue.help." + action, Placeholder.unparsed("command", label));
        }
    }

    private static TagResolver[] with(TagResolver[] resolvers, TagResolver extra) {
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        System.arraycopy(resolvers, 0, all, 0, resolvers.length);
        all[resolvers.length] = extra;
        return all;
    }

    static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
