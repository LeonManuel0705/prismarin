package net.prismarin.core.scoreboard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.api.SidebarProvider;
import net.prismarin.core.api.SidebarService;
import net.prismarin.core.display.PriorityRegistry;
import net.prismarin.core.locale.PlayerLocales;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

@Singleton
public final class SidebarManager implements SidebarService {

    private static final String OBJECTIVE_NAME = "prismarin";
    private static final int MAX_LINES = 15;
    private static final long UPDATE_INTERVAL_TICKS = 10L;

    private final PrismarinCorePlugin plugin;
    private final PlayerBoards boards;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final Map<UUID, Integer> lineCounts = new ConcurrentHashMap<>();
    private final PriorityRegistry<SidebarProvider> providers = new PriorityRegistry<>("sidebar provider");
    private BukkitTask task;

    @Inject
    public SidebarManager(PrismarinCorePlugin plugin, PlayerBoards boards, LocaleService locale,
                          ProfileService profiles) {
        this.plugin = plugin;
        this.boards = boards;
        this.locale = locale;
        this.profiles = profiles;
    }

    public void start() {
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::updateAll, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public Subscription register(SidebarProvider provider, int priority) {
        return providers.register(provider, priority);
    }

    @Override
    public Optional<SidebarProvider> active(Player player) {
        return providers.first(provider -> provider.appliesTo(player));
    }

    @Override
    public void refresh(Player player) {
        if (task != null && player.isOnline()) {
            update(player);
        }
    }

    public void handleQuit(Player player) {
        lineCounts.remove(player.getUniqueId());
    }

    private void updateAll() {
        if (providers.isEmpty() && lineCounts.isEmpty()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        Optional<SidebarProvider> provider = active(player);
        List<Component> lines = provider.flatMap(value -> providers.call(value, () -> value.lines(player)))
                .orElse(List.of());
        if (lines.isEmpty()) {
            hide(player);
            return;
        }
        Scoreboard board = boards.board(player);
        Component title = providers.call(provider.get(), () -> provider.get().title(player))
                .orElseGet(() -> locale.render("sidebar.title", PlayerLocales.of(player, profiles),
                        player.getUniqueId()));
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
        }
        objective.displayName(title);
        int size = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < size; i++) {
            Score score = objective.getScore(entryFor(i));
            score.setScore(size - i);
            Component line = lines.get(i);
            score.customName(line == null ? Component.empty() : line);
        }
        int previous = lineCounts.getOrDefault(player.getUniqueId(), 0);
        for (int i = size; i < previous; i++) {
            board.resetScores(entryFor(i));
        }
        lineCounts.put(player.getUniqueId(), size);
    }

    private void hide(Player player) {
        if (lineCounts.remove(player.getUniqueId()) == null) {
            return;
        }
        boards.existing(player.getUniqueId())
                .map(board -> board.getObjective(OBJECTIVE_NAME))
                .ifPresent(Objective::unregister);
    }

    private static String entryFor(int index) {
        return "line-" + index;
    }
}
