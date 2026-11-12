package net.prismarin.core.stats;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.stats.DocumentStatsService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class StatsCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.command.stats";
    public static final String OTHERS_PERMISSION = "prismarin.command.stats.others";

    static final int HOVER_STATS = 6;

    private record Target(UUID uuid, String shown, String command) {
    }

    private final PrismarinCorePlugin plugin;
    private final PaperStats stats;
    private final StatsSettings settings;
    private final StatTexts texts;
    private final PlayerService players;
    private final ProfileService profiles;
    private final LocaleService locale;
    private final CoreConfig config;

    @Inject
    public StatsCommand(PrismarinCorePlugin plugin, PaperStats stats, StatsSettings settings, StatTexts texts,
                        PlayerService players, ProfileService profiles, LocaleService locale, CoreConfig config) {
        this.plugin = plugin;
        this.stats = stats;
        this.settings = settings;
        this.texts = texts;
        this.players = players;
        this.profiles = profiles;
        this.locale = locale;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION) || stats.service().isEmpty()) {
            send(sender, "general.no-permission");
            return true;
        }
        DocumentStatsService service = stats.service().get();
        Set<String> games = gameIds(service);
        StatsArguments.Result parsed = StatsArguments.parse(args, games::contains,
                name -> Bukkit.getPlayerExact(name) != null);
        if (!(parsed instanceof StatsArguments.Show show)) {
            usage(sender);
            return true;
        }
        UUID viewer = sender instanceof Player player ? player.getUniqueId() : null;
        if (show.self() || sender instanceof Player own && show.player().equalsIgnoreCase(own.getName())) {
            if (!(sender instanceof Player self)) {
                usage(sender);
                return true;
            }
            show(sender, service, new Target(self.getUniqueId(), players.displayName(viewer, self.getUniqueId()),
                    self.getName()), show.game(), show.page());
            return true;
        }
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            send(sender, "general.no-permission");
            return true;
        }
        String typed = show.player();
        players.resolve(viewer, typed).whenComplete((found, error) -> {
            if (error != null || found == null || found.isEmpty()) {
                send(sender, "general.player-not-found", Placeholder.unparsed("player", typed));
                return;
            }
            NetworkPlayer target = found.get();
            String shown = players.displayName(viewer, target);
            if (target.nicked() && !players.revealsRealName(viewer, target.uuid())) {
                send(sender, "stats.none", Placeholder.unparsed("player", shown));
                return;
            }
            show(sender, service, new Target(target.uuid(), shown, typed), show.game(), show.page());
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION) || stats.service().isEmpty()) {
            return List.of();
        }
        UUID viewer = sender instanceof Player player ? player.getUniqueId() : null;
        List<String> games = List.copyOf(gameIds(stats.service().get()));
        return switch (args.length) {
            case 1 -> {
                List<String> options = new ArrayList<>(sender.hasPermission(OTHERS_PERMISSION)
                        ? players.suggestions(viewer, args[0])
                        : sender instanceof Player player ? filter(List.of(player.getName()), args[0]) : List.of());
                if (sender instanceof Player) {
                    options.addAll(filter(games, args[0]));
                }
                yield List.copyOf(new LinkedHashSet<>(options));
            }
            case 2 -> filter(games, args[1]);
            default -> List.of();
        };
    }

    private void show(CommandSender sender, DocumentStatsService service, Target target, String game, int page) {
        Locale language = language(sender);
        stats.fresh(target.uuid()).thenCompose(values -> {
            List<StatsView.Group> groups = StatsView.groups(values, service.definitions(), service::definition,
                    service.games(), game);
            if (groups.isEmpty()) {
                send(sender, game == null ? "stats.none" : "stats.none-game",
                        Placeholder.unparsed("player", target.shown()),
                        Placeholder.component("game", texts.game(game == null ? "" : game, language)));
                return CompletableFuture.completedFuture(null);
            }
            StatsView.Sheet sheet = StatsView.sheet(groups, game, settings.command().pageSize(),
                    settings.command().gameListAbove(), page);
            if (sheet.layout() == StatsView.Layout.GAMES) {
                deliver(sender, gameList(service, values, target, groups.size(), sheet, language));
                return CompletableFuture.completedFuture(null);
            }
            return service.ranks(values, sheet.keys(), StatPeriod.ALL_TIME).thenAccept(ranks ->
                    deliver(sender, statLines(service, values, target, game, sheet, ranks, language,
                            groups.size())));
        }).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not show the stats of " + target.uuid(), error);
            send(sender, "stats.failed");
            return null;
        });
    }

    private Component statLines(DocumentStatsService service, PlayerStats values, Target target, String game,
                                StatsView.Sheet sheet, Map<String, OptionalInt> ranks, Locale language,
                                int allGames) {
        List<Component> lines = new ArrayList<>();
        lines.add(game == null
                ? locale.render("stats.header", language, Placeholder.unparsed("player", target.shown()))
                : locale.render("stats.header-game", language, Placeholder.unparsed("player", target.shown()),
                Placeholder.component("game", texts.game(game, language))));
        for (StatsView.Group group : sheet.groups()) {
            if (game == null) {
                lines.add(locale.render("stats.game", language,
                        Placeholder.component("game", texts.game(group.game(), language))));
            }
            for (StatDefinition definition : group.stats()) {
                lines.add(line(service, values, definition, ranks, language));
            }
        }
        if (sheet.pages() > 1) {
            lines.add(pager(target, game, sheet, language));
        }
        if (game != null && allGames > settings.command().gameListAbove()) {
            lines.add(locale.render("stats.back", language)
                    .clickEvent(ClickEvent.runCommand(StatsArguments.command(target.command(), null, 1)))
                    .hoverEvent(HoverEvent.showText(locale.render("stats.back-hover", language))));
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private Component gameList(DocumentStatsService service, PlayerStats values, Target target, int count,
                               StatsView.Sheet sheet, Locale language) {
        List<Component> lines = new ArrayList<>();
        lines.add(locale.render("stats.games-header", language, Placeholder.unparsed("player", target.shown()),
                Placeholder.unparsed("count", Integer.toString(count))));
        for (StatsView.Group group : sheet.groups()) {
            Component name = texts.game(group.game(), language);
            StatDefinition first = group.stats().getFirst();
            Component entry = locale.render("stats.game-entry", language, Placeholder.component("game", name),
                    Placeholder.unparsed("count", Integer.toString(group.stats().size())),
                    Placeholder.component("first", locale.render("stats.game-first", language,
                            Placeholder.component("stat", texts.name(first, language)),
                            Placeholder.unparsed("value", value(service, values, first, StatPeriod.ALL_TIME,
                                    language)))));
            List<Component> hover = new ArrayList<>();
            for (StatDefinition definition : group.stats().stream().limit(HOVER_STATS).toList()) {
                hover.add(locale.render("stats.game-hover-line", language,
                        Placeholder.component("stat", texts.name(definition, language)),
                        Placeholder.unparsed("value", value(service, values, definition, StatPeriod.ALL_TIME,
                                language))));
            }
            if (group.stats().size() > HOVER_STATS) {
                hover.add(locale.render("stats.game-hover-more", language,
                        Placeholder.unparsed("count", Integer.toString(group.stats().size() - HOVER_STATS))));
            }
            hover.add(Component.empty());
            hover.add(locale.render("stats.game-hover-click", language, Placeholder.component("game", name)));
            lines.add(entry.clickEvent(ClickEvent.runCommand(StatsArguments.command(target.command(), group.game(),
                    1))).hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), hover))));
        }
        if (sheet.pages() > 1) {
            lines.add(pager(target, null, sheet, language));
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private Component pager(Target target, String game, StatsView.Sheet sheet, Locale language) {
        Component previous = sheet.page() > 1
                ? link("stats.page-previous", target, game, sheet.page() - 1, language)
                : locale.render("stats.page-previous-none", language);
        Component next = sheet.page() < sheet.pages()
                ? link("stats.page-next", target, game, sheet.page() + 1, language)
                : locale.render("stats.page-next-none", language);
        return locale.render("stats.page", language, Placeholder.component("previous", previous),
                Placeholder.component("next", next), Placeholder.unparsed("page", Integer.toString(sheet.page())),
                Placeholder.unparsed("pages", Integer.toString(sheet.pages())));
    }

    private Component link(String key, Target target, String game, int page, Locale language) {
        return locale.render(key, language)
                .clickEvent(ClickEvent.runCommand(StatsArguments.command(target.command(), game, page)))
                .hoverEvent(HoverEvent.showText(locale.render("stats.page-hover", language,
                        Placeholder.unparsed("page", Integer.toString(page)))));
    }

    private Component line(DocumentStatsService service, PlayerStats values, StatDefinition definition,
                           Map<String, OptionalInt> ranks, Locale language) {
        OptionalInt rank = ranks.getOrDefault(definition.key(), OptionalInt.empty());
        Component rankText = rank.isPresent()
                ? locale.render("stats.rank", language, Placeholder.unparsed("rank", Integer.toString(rank.getAsInt())))
                : Component.empty();
        List<Component> hover = new ArrayList<>();
        for (StatPeriod period : List.of(StatPeriod.WEEKLY, StatPeriod.MONTHLY)) {
            if (service.periods().contains(period)) {
                hover.add(locale.render("stats.hover", language,
                        Placeholder.component("period", texts.period(period, language)),
                        Placeholder.unparsed("value", value(service, values, definition, period, language))));
            }
        }
        Component line = locale.render("stats.line", language,
                Placeholder.component("stat", texts.name(definition, language)),
                Placeholder.unparsed("value", value(service, values, definition, StatPeriod.ALL_TIME, language)),
                Placeholder.component("rank", rankText));
        return hover.isEmpty() ? line
                : line.hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), hover)));
    }

    private static String value(DocumentStatsService service, PlayerStats values, StatDefinition definition,
                                StatPeriod period, Locale language) {
        return service.format(definition.key(), values.value(definition.key(), period), language);
    }

    private Set<String> gameIds(DocumentStatsService service) {
        Set<String> games = new LinkedHashSet<>();
        for (StatGame game : service.games()) {
            games.add(game.id());
        }
        games.remove(StatKeys.GENERAL);
        games.add(StatKeys.GENERAL);
        return games;
    }

    private void usage(CommandSender sender) {
        send(sender, sender.hasPermission(OTHERS_PERMISSION) ? "stats.usage" : "stats.usage-self");
    }

    private Locale language(CommandSender sender) {
        return sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
    }

    private void send(CommandSender sender, String key, TagResolver... resolvers) {
        deliver(sender, locale.render(key, language(sender), resolvers));
    }

    private void deliver(CommandSender sender, Component message) {
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
