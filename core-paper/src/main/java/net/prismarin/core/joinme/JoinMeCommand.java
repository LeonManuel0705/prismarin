package net.prismarin.core.joinme;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.joinme.JoinMeCodec;
import net.prismarin.shared.joinme.JoinMePlaceholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class JoinMeCommand implements TabExecutor {

    private final PrismarinCorePlugin plugin;
    private final PaperJoinMe joinMe;
    private final JoinMeView view;
    private final ServerDirectory directory;
    private final CoreConfig config;

    @Inject
    public JoinMeCommand(PrismarinCorePlugin plugin, PaperJoinMe joinMe, JoinMeView view, ServerDirectory directory,
                         CoreConfig config) {
        this.plugin = plugin;
        this.joinMe = joinMe;
        this.view = view;
        this.directory = directory;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        JoinMeSubCommands commands = joinMe.settings().subCommands();
        Predicate<String> permissions = sender::hasPermission;
        String action = args.length == 0 ? commands.fallback(permissions) : args[0].toLowerCase(Locale.ROOT);
        if (JoinMeSubCommands.hidden(action)) {
            join(sender, args);
            return true;
        }
        if (!commands.allowed(action, permissions)) {
            help(sender, commands, permissions);
            return true;
        }
        switch (action) {
            case JoinMeSubCommands.CREATE -> create(sender);
            case JoinMeSubCommands.PREVIEW -> preview(sender);
            case JoinMeSubCommands.LIST -> list(sender);
            case JoinMeSubCommands.CANCEL -> cancel(sender, args, commands, permissions);
            default -> help(sender, commands, permissions);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        JoinMeSubCommands commands = joinMe.settings().subCommands();
        Predicate<String> permissions = sender::hasPermission;
        if (args.length <= 1) {
            return commands.suggestions(permissions, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && JoinMeSubCommands.CANCEL.equalsIgnoreCase(args[0])
                && commands.cancelsOthers(permissions)) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            Set<String> found = new LinkedHashSet<>();
            for (JoinMe known : joinMe.known()) {
                for (String option : List.of(known.id(), known.name(true), known.name(false))) {
                    if (!option.isEmpty() && option.toLowerCase(Locale.ROOT).startsWith(typed)) {
                        found.add(option);
                    }
                }
            }
            return found.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        return List.of();
    }

    private void create(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.players-only");
            return;
        }
        joinMe.create(player.getUniqueId()).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "JoinMe of " + player.getName() + " failed", error);
                send(player, "joinme.failed");
                return;
            }
            JoinMeResult.Status status = result.status();
            TagResolver resolver = result.joinMe().map(found -> view.resolver(player, found))
                    .orElseGet(() -> TagResolver.resolver(Placeholder.unparsed("time",
                            JoinMePlaceholders.clock(result.waitMillis())),
                            Placeholder.unparsed("group", ownGroup())));
            send(player, JoinMeTexts.created(status), resolver);
        });
    }

    private void preview(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.players-only");
            return;
        }
        joinMe.preview(player.getUniqueId()).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "JoinMe preview of " + player.getName() + " failed", error);
                send(player, "joinme.failed");
                return;
            }
            send(player, JoinMeTexts.previewed(result.status()));
        });
    }

    private void list(CommandSender sender) {
        CompletableFuture<List<JoinMe>> found = sender instanceof Player player
                ? joinMe.visibleTo(player.getUniqueId()) : joinMe.active();
        found.whenComplete((joinMes, error) -> {
            if (error != null) {
                send(sender, "joinme.unavailable");
                return;
            }
            if (joinMes.isEmpty()) {
                send(sender, "joinme.list-empty");
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(view.render(sender, "joinme.list-header", Placeholder.unparsed("count",
                    Integer.toString(joinMes.size()))));
            for (JoinMe entry : joinMes) {
                lines.add(view.entry(sender, entry));
            }
            deliver(sender, lines);
        });
    }

    private void cancel(CommandSender sender, String[] args, JoinMeSubCommands commands,
                        Predicate<String> permissions) {
        if (args.length < 2) {
            if (!(sender instanceof Player player) || !permissions.test(JoinMeSubCommands.CREATE_PERMISSION)) {
                send(sender, commands.cancelsOthers(permissions) ? "joinme.cancel-usage" : "general.players-only");
                return;
            }
            joinMe.cancelFor(player.getUniqueId()).whenComplete((done, error) ->
                    send(sender, error != null ? "joinme.unavailable"
                            : Boolean.TRUE.equals(done) ? "joinme.cancel-own" : "joinme.cancel-none"));
            return;
        }
        if (!commands.cancelsOthers(permissions)) {
            send(sender, "general.no-permission");
            return;
        }
        String typed = args[1];
        joinMe.active().thenCompose(all -> {
            Optional<JoinMe> target = match(all, typed);
            if (target.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<JoinMe>empty());
            }
            return joinMe.cancel(target.get().id()).thenApply(done -> done ? target : Optional.<JoinMe>empty());
        }).whenComplete((cancelled, error) -> {
            if (error != null) {
                send(sender, "joinme.unavailable");
            } else if (cancelled.isEmpty()) {
                send(sender, "joinme.cancel-unknown", Placeholder.unparsed("target", typed));
            } else {
                send(sender, "joinme.cancel-done", view.resolver(sender, cancelled.get()));
            }
        });
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.players-only");
            return;
        }
        if (args.length < 2 || !JoinMeCodec.validId(args[1].toLowerCase(Locale.ROOT))) {
            send(player, "joinme.join-not-found");
            return;
        }
        joinMe.join(player.getUniqueId(), args[1].toLowerCase(Locale.ROOT)).whenComplete((result, error) -> {
            if (error != null) {
                send(player, "joinme.failed");
                return;
            }
            TagResolver resolver = result.joinMe().map(found -> view.resolver(player, found))
                    .orElseGet(TagResolver::empty);
            send(player, JoinMeTexts.joined(result.status()), resolver);
        });
    }

    private void help(CommandSender sender, JoinMeSubCommands commands, Predicate<String> permissions) {
        List<Component> lines = new ArrayList<>();
        lines.add(view.render(sender, "joinme.help-header"));
        for (String action : commands.visible(permissions)) {
            if (JoinMeSubCommands.HELP.equals(action)) {
                continue;
            }
            if (JoinMeSubCommands.CANCEL.equals(action)) {
                if (permissions.test(JoinMeSubCommands.CREATE_PERMISSION)) {
                    lines.add(view.render(sender, "joinme.help-cancel-own"));
                }
                if (commands.cancelsOthers(permissions)) {
                    lines.add(view.render(sender, "joinme.help-cancel"));
                }
                continue;
            }
            lines.add(view.render(sender, "joinme.help-" + action));
        }
        deliver(sender, lines);
    }

    private static Optional<JoinMe> match(List<JoinMe> all, String typed) {
        String wanted = typed.toLowerCase(Locale.ROOT);
        for (JoinMe candidate : all) {
            if (candidate.id().equals(wanted)) {
                return Optional.of(candidate);
            }
        }
        for (JoinMe candidate : all) {
            if (candidate.creatorName().equalsIgnoreCase(typed) || candidate.shownName().equalsIgnoreCase(typed)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String ownGroup() {
        return directory.byName(config.serverId()).map(NetworkServer::group).orElse(NetworkServer.GROUP_DEFAULT);
    }

    private void send(CommandSender sender, String key, TagResolver... resolvers) {
        deliver(sender, List.of(view.render(sender, key, resolvers)));
    }

    private void deliver(CommandSender sender, List<Component> lines) {
        Runnable send = () -> lines.forEach(sender::sendMessage);
        if (plugin.getServer().isPrimaryThread()) {
            send.run();
        } else {
            try {
                plugin.getServer().getScheduler().runTask(plugin, send);
            } catch (RuntimeException e) {
                send.run();
            }
        }
    }
}
