package net.prismarin.lobby.leaderboard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.api.stats.StatsApi;
import net.prismarin.api.stats.StatsService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.stats.StatTexts;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class LeaderboardCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.lobby.leaderboard";

    private static final List<String> ACTIONS = List.of("create", "remove", "move", "list");
    private static final List<String> SIZES = List.of("5", "10", "15", "20");

    private final LobbyPlugin plugin;
    private final LeaderboardDisplays displays;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final StatTexts texts;
    private final CoreConfig config;
    private final ExecutorService executor;

    @Inject
    public LeaderboardCommand(LobbyPlugin plugin, LeaderboardDisplays displays, LocaleService locale,
                              ProfileService profiles, StatTexts texts, CoreConfig config, ExecutorService executor) {
        this.plugin = plugin;
        this.displays = displays;
        this.locale = locale;
        this.profiles = profiles;
        this.texts = texts;
        this.config = config;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Optional<StatsService> stats = StatsApi.service();
        if (!sender.hasPermission(PERMISSION) || stats.isEmpty()) {
            send(sender, "general.no-permission");
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> create(sender, args, stats.get());
            case "remove" -> remove(sender, args);
            case "move" -> move(sender, args);
            case "list" -> list(sender);
            default -> send(sender, "lobby.leaderboard.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Optional<StatsService> stats = StatsApi.service();
        if (!sender.hasPermission(PERMISSION) || stats.isEmpty()) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(ACTIONS, args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if ((action.equals("remove") || action.equals("move")) && args.length == 2) {
            return filter(displays.file().boards().stream().map(LeaderboardBoard::id).toList(), args[1]);
        }
        if (!action.equals("create")) {
            return List.of();
        }
        return switch (args.length) {
            case 3 -> filter(statKeys(stats.get()), args[2]);
            case 4, 5 -> filter(Stream.concat(periods(stats.get()).stream(), SIZES.stream()).toList(),
                    args[args.length - 1]);
            default -> List.of();
        };
    }

    private void create(CommandSender sender, String[] args, StatsService stats) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.players-only");
            return;
        }
        LeaderboardArguments.Result result = LeaderboardArguments.create(args, stats.periods());
        if (result instanceof LeaderboardArguments.Invalid invalid) {
            send(sender, invalid.messageKey(), Placeholder.unparsed("id", invalid.value()),
                    Placeholder.unparsed("stat", invalid.value()),
                    Placeholder.unparsed("periods", String.join(", ", periods(stats))),
                    Placeholder.unparsed("max", Integer.toString(LeaderboardBoard.MAX_SIZE)));
            return;
        }
        LeaderboardArguments.Create create = (LeaderboardArguments.Create) result;
        if (displays.file().board(create.id()).isPresent()) {
            send(sender, "lobby.leaderboard.exists", Placeholder.unparsed("id", create.id()));
            return;
        }
        Location location = player.getLocation();
        LeaderboardBoard board = new LeaderboardBoard(create.id(), create.stat(), create.period(), create.size(),
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw() + 180.0f, "", "");
        displays.place(board);
        Locale target = language(sender);
        save(sender, "lobby.leaderboard.created", Placeholder.unparsed("id", board.id()),
                Placeholder.component("stat", texts.name(stats.definition(board.stat()), target)),
                Placeholder.component("period", texts.period(board.period(), target)),
                Placeholder.unparsed("size", Integer.toString(board.size())));
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 2) {
            send(sender, "lobby.leaderboard.usage");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (displays.file().board(id).isEmpty()) {
            send(sender, "lobby.leaderboard.unknown", Placeholder.unparsed("id", args[1]));
            return;
        }
        displays.remove(id);
        save(sender, "lobby.leaderboard.removed", Placeholder.unparsed("id", id));
    }

    private void move(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "general.players-only");
            return;
        }
        if (args.length != 2) {
            send(sender, "lobby.leaderboard.usage");
            return;
        }
        Optional<LeaderboardBoard> found = displays.file().board(args[1]);
        if (found.isEmpty()) {
            send(sender, "lobby.leaderboard.unknown", Placeholder.unparsed("id", args[1]));
            return;
        }
        Location location = player.getLocation();
        LeaderboardBoard moved = found.get().at(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw() + 180.0f);
        displays.place(moved);
        save(sender, "lobby.leaderboard.moved", Placeholder.unparsed("id", moved.id()));
    }

    private void list(CommandSender sender) {
        List<LeaderboardBoard> boards = displays.file().boards();
        if (boards.isEmpty()) {
            send(sender, "lobby.leaderboard.list-empty");
            return;
        }
        Locale target = language(sender);
        List<Component> lines = new ArrayList<>();
        lines.add(locale.render("lobby.leaderboard.list-header", target,
                Placeholder.unparsed("count", Integer.toString(boards.size()))));
        for (LeaderboardBoard board : boards) {
            lines.add(locale.render("lobby.leaderboard.list-entry", target, Placeholder.unparsed("id", board.id()),
                    Placeholder.unparsed("stat", board.stat()),
                    Placeholder.component("period", texts.period(board.period(), target)),
                    Placeholder.unparsed("size", Integer.toString(board.size())),
                    Placeholder.unparsed("world", board.world()),
                    Placeholder.unparsed("x", Long.toString(Math.round(board.x()))),
                    Placeholder.unparsed("y", Long.toString(Math.round(board.y()))),
                    Placeholder.unparsed("z", Long.toString(Math.round(board.z())))));
        }
        lines.forEach(sender::sendMessage);
    }

    private void save(CommandSender sender, String success, TagResolver... resolvers) {
        CompletableFuture.runAsync(displays.file()::save, executor).whenComplete((done, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not save " + LeaderboardsFile.FILE_NAME, error);
                        send(sender, "lobby.leaderboard.failed");
                    } else {
                        send(sender, success, resolvers);
                    }
                }));
    }

    private static List<String> statKeys(StatsService stats) {
        Set<String> keys = new LinkedHashSet<>();
        stats.definitions().forEach(definition -> keys.add(definition.key()));
        stats.games().forEach(game -> keys.addAll(game.featured()));
        return List.copyOf(keys);
    }

    private static List<String> periods(StatsService stats) {
        Set<StatPeriod> enabled = stats.periods();
        return Stream.of(StatPeriod.values()).filter(enabled::contains).map(StatPeriod::id)
                .collect(Collectors.toList());
    }

    private Locale language(CommandSender sender) {
        return sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }

    private void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(locale.render(key, language(sender), resolvers));
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
