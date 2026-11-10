package net.prismarin.core.tablist;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
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
public final class TablistManager {

    private static final long REFRESH_INTERVAL_TICKS = 100L;

    private final PrismarinCorePlugin plugin;
    private final PlayerBoards boards;
    private final RankFormatProvider ranks;
    private final LocaleService locale;
    private final PermissionSnapshots snapshots;
    private final ProfileService profiles;
    private final VanishService vanish;
    private final AnimatedTitle title;
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

    public void refreshAll() {
        Component frame = title.next();
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            String online = String.valueOf(vanish.visibleCount(viewer));
            snapshots.refresh(viewer);
            viewer.sendPlayerListHeaderAndFooter(
                    locale.render("tablist.header", PlayerLocales.of(viewer, profiles), viewer.getUniqueId(),
                            Placeholder.component("title", frame),
                            Placeholder.unparsed("online", online)),
                    locale.render("tablist.footer", PlayerLocales.of(viewer, profiles), viewer.getUniqueId(),
                            Placeholder.unparsed("online", online)));
            syncTeams(boards.board(viewer));
        }
    }

    private void syncTeams(Scoreboard board) {
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
            team.prefix(ranks.prefix(target).append(Component.space()));
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
