package net.prismarin.core.tablist;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.api.TablistContributor;
import net.prismarin.core.api.TablistService;
import net.prismarin.core.display.DisplayParts;
import net.prismarin.core.display.PriorityRegistry;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.permission.PermissionSnapshots;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.core.scoreboard.AnimatedTitle;
import net.prismarin.core.scoreboard.PlayerBoards;
import net.prismarin.core.vanish.VanishService;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

@Singleton
public final class TablistManager implements TablistService {

    private static final long REFRESH_INTERVAL_TICKS = 100L;

    private final PrismarinCorePlugin plugin;
    private final PlayerBoards boards;
    private final RankFormatProvider ranks;
    private final LocaleService locale;
    private final PermissionSnapshots snapshots;
    private final ProfileService profiles;
    private final VanishService vanish;
    private final AnimatedTitle title;
    private final PriorityRegistry<TablistContributor> contributors = new PriorityRegistry<>("tab list contributor");
    private BukkitTask task;

    @Inject
    public TablistManager(PrismarinCorePlugin plugin, PlayerBoards boards, RankFormatProvider ranks,
                          LocaleService locale, PermissionSnapshots snapshots, MiniMessage miniMessage,
                          Branding branding, ProfileService profiles, VanishService vanish) {
        this.plugin = plugin;
        this.boards = boards;
        this.ranks = ranks;
        this.locale = locale;
        this.snapshots = snapshots;
        this.profiles = profiles;
        this.vanish = vanish;
        this.title = AnimatedTitle.brand(miniMessage, branding);
    }

    public void start() {
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public Subscription register(TablistContributor contributor, int priority) {
        return contributors.register(contributor, priority);
    }

    @Override
    public void refresh() {
        if (task != null) {
            refreshAll();
        }
    }

    public void handleJoin(Player player) {
        refreshAll();
    }

    public void handleQuit(Player player) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            removeEntry(boards.board(viewer), player.getName());
        }
    }

    public void handleRename(Player player, String previousName) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            Scoreboard board = boards.board(viewer);
            if (!previousName.equals(player.getName())) {
                removeEntry(board, previousName);
            }
            syncTeams(viewer, board);
        }
    }

    public void refreshAll() {
        Component frame = title.next();
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            String online = String.valueOf(vanish.visibleCount(null));
            snapshots.refresh(viewer);
            Component header = locale.render("tablist.header", PlayerLocales.of(viewer, profiles),
                    viewer.getUniqueId(), Placeholder.component("title", frame),
                    Placeholder.unparsed("online", online));
            Component footer = locale.render("tablist.footer", PlayerLocales.of(viewer, profiles),
                    viewer.getUniqueId(), Placeholder.unparsed("online", online));
            viewer.sendPlayerListHeaderAndFooter(
                    DisplayParts.lines(header, contributors.collectAll(contributor -> contributor.header(viewer))),
                    DisplayParts.lines(footer, contributors.collectAll(contributor -> contributor.footer(viewer))));
            syncTeams(viewer, boards.board(viewer));
        }
    }

    private void syncTeams(Player viewer, Scoreboard board) {
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            String key = TeamSortKeys.sortKey(ranks.weight(target), target.getName());
            Team current = board.getEntryTeam(target.getName());
            if (current != null && !current.getName().equals(key) && TeamSortKeys.isManaged(current.getName())) {
                current.removeEntry(target.getName());
                if (current.getEntries().isEmpty()) {
                    current.unregister();
                }
            }
            Team team = board.getTeam(key);
            if (team == null) {
                team = board.registerNewTeam(key);
            }
            List<Component> prefixes = new ArrayList<>(contributors.collect(
                    contributor -> contributor.prefix(viewer, target)));
            prefixes.add(ranks.prefix(target));
            List<Component> suffixes = new ArrayList<>();
            suffixes.add(ranks.suffix(target));
            suffixes.addAll(contributors.collect(contributor -> contributor.suffix(viewer, target)));
            team.prefix(DisplayParts.prefix(prefixes));
            team.suffix(DisplayParts.suffix(suffixes));
            team.color(ranks.nameColor(target));
            if (!team.hasEntry(target.getName())) {
                team.addEntry(target.getName());
            }
        }
    }

    private static void removeEntry(Scoreboard board, String entry) {
        Team team = board.getEntryTeam(entry);
        if (team == null || !TeamSortKeys.isManaged(team.getName())) {
            return;
        }
        team.removeEntry(entry);
        if (team.getEntries().isEmpty()) {
            team.unregister();
        }
    }
}
