package net.prismarin.core.rank;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.moderation.Durations;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class RankCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.admin.rank";

    private final PrismarinCorePlugin plugin;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final CoreConfig config;

    public RankCommand(PrismarinCorePlugin plugin, LocaleService locale, ProfileService profiles, CoreConfig config) {
        this.plugin = plugin;
        this.locale = locale;
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            list(sender);
            return true;
        }
        if (args.length == 1) {
            withUser(sender, args[0], user -> info(sender, user, args[0]));
            return true;
        }
        if (args.length < 3) {
            send(sender, "rank.usage");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Group group = luckPerms().getGroupManager().getGroup(args[2].toLowerCase(Locale.ROOT));
        if (group == null) {
            send(sender, "rank.unknown-group", Placeholder.unparsed("group", args[2]));
            return true;
        }
        Optional<Duration> duration = Optional.empty();
        if (args.length >= 4) {
            duration = Durations.parse(args[3]);
            if (duration.isEmpty()) {
                send(sender, "rank.invalid-duration", Placeholder.unparsed("duration", args[3]));
                return true;
            }
        }
        Optional<Duration> lasting = duration;
        switch (action) {
            case "set" -> modify(sender, args[0], group, user -> {
                user.data().clear(NodeType.INHERITANCE::matches);
                user.data().add(node(group, lasting));
                user.setPrimaryGroup(group.getName());
            }, lasting.isPresent() ? "rank.set-temporary" : "rank.set", lasting);
            case "add" -> modify(sender, args[0], group, user -> user.data().add(node(group, lasting)),
                    lasting.isPresent() ? "rank.added-temporary" : "rank.added", lasting);
            case "remove" -> modify(sender, args[0], group, user -> user.data().clear(node ->
                            node instanceof InheritanceNode inheritance
                                    && inheritance.getGroupName().equalsIgnoreCase(group.getName())),
                    "rank.removed", Optional.empty());
            default -> send(sender, "rank.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> filter(Stream.concat(Stream.of("list"),
                    Bukkit.getOnlinePlayers().stream().map(Player::getName)).toList(), args[0]);
            case 2 -> filter(List.of("set", "add", "remove"), args[1]);
            case 3 -> filter(luckPerms().getGroupManager().getLoadedGroups().stream().map(Group::getName).toList(),
                    args[2]);
            case 4 -> filter(List.of("1h", "1d", "7d", "30d"), args[3]);
            default -> List.of();
        };
    }

    private static InheritanceNode node(Group group, Optional<Duration> duration) {
        InheritanceNode.Builder builder = InheritanceNode.builder(group);
        duration.ifPresent(builder::expiry);
        return builder.build();
    }

    private void modify(CommandSender sender, String name, Group group, Consumer<User> change,
                        String messageKey, Optional<Duration> duration) {
        resolve(name).thenCompose(uuid -> {
            if (uuid.isEmpty()) {
                send(sender, "general.player-not-found", Placeholder.unparsed("player", name));
                return CompletableFuture.completedFuture(null);
            }
            return luckPerms().getUserManager().modifyUser(uuid.get(), change).thenRun(() -> {
                send(sender, messageKey, Placeholder.unparsed("player", name),
                        Placeholder.component("group", locale.format(displayName(group))),
                        Placeholder.unparsed("duration", duration.map(value -> Durations.format(value.toMillis()))
                                .orElse("")));
                Player online = Bukkit.getPlayer(uuid.get());
                if (online != null && !messageKey.equals("rank.removed")) {
                    Bukkit.getScheduler().runTask(plugin, () -> online.sendMessage(locale.render("rank.received",
                            PlayerLocales.of(online, profiles),
                            Placeholder.component("group", locale.format(displayName(group))))));
                }
            });
        }).exceptionally(error -> {
            send(sender, "rank.failed", Placeholder.unparsed("reason", String.valueOf(error.getMessage())));
            return null;
        });
    }

    private void withUser(CommandSender sender, String name, Consumer<User> action) {
        resolve(name).thenCompose(uuid -> uuid.isEmpty()
                        ? CompletableFuture.completedFuture((User) null)
                        : luckPerms().getUserManager().loadUser(uuid.get()))
                .thenAccept(user -> {
                    if (user == null) {
                        send(sender, "general.player-not-found", Placeholder.unparsed("player", name));
                        return;
                    }
                    action.accept(user);
                });
    }

    private void info(CommandSender sender, User user, String typed) {
        Player online = Bukkit.getPlayer(user.getUniqueId());
        String name = online != null ? online.getName() : typed;
        send(sender, "rank.info-header", Placeholder.unparsed("player", name),
                Placeholder.component("group", groupName(user.getPrimaryGroup())));
        Instant now = Instant.now();
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            Instant expiry = node.getExpiry();
            if (expiry == null) {
                send(sender, "rank.info-entry", Placeholder.component("group", groupName(node.getGroupName())));
            } else {
                send(sender, "rank.info-temporary", Placeholder.component("group", groupName(node.getGroupName())),
                        Placeholder.unparsed("duration", Durations.format(expiry.toEpochMilli() - now.toEpochMilli())));
            }
        }
    }

    private void list(CommandSender sender) {
        List<Group> groups = luckPerms().getGroupManager().getLoadedGroups().stream()
                .sorted(Comparator.comparingInt((Group group) -> group.getWeight().orElse(0)).reversed())
                .toList();
        send(sender, "rank.list-header", Placeholder.unparsed("count", String.valueOf(groups.size())));
        for (Group group : groups) {
            String prefix = group.getCachedData().getMetaData().getPrefix();
            send(sender, "rank.list-entry", Placeholder.component("group", locale.format(displayName(group))),
                    Placeholder.unparsed("weight", String.valueOf(group.getWeight().orElse(0))),
                    Placeholder.component("prefix", prefix == null ? Component.empty() : locale.format(prefix)));
        }
    }

    private CompletableFuture<Optional<UUID>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }
        return luckPerms().getUserManager().lookupUniqueId(name).thenCompose(uuid -> uuid != null
                ? CompletableFuture.completedFuture(Optional.of(uuid))
                : profiles.lookup(name));
    }

    private Component groupName(String name) {
        Group group = luckPerms().getGroupManager().getGroup(name);
        return locale.format(group == null ? name : displayName(group));
    }

    private static String displayName(Group group) {
        return group.getDisplayName() != null ? group.getDisplayName() : group.getName();
    }

    private static LuckPerms luckPerms() {
        return LuckPermsProvider.get();
    }

    private void send(CommandSender sender, String key, TagResolver... resolvers) {
        Locale target = sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
        Component message = locale.render(key, target, resolvers);
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(message);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
