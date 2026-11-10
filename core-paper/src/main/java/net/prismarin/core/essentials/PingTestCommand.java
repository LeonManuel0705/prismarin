package net.prismarin.core.essentials;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

@Singleton
public final class PingTestCommand implements TabExecutor {

    private static final int BAR_SEGMENTS = 20;

    private final EssentialsSupport support;
    private final Map<UUID, BukkitTask> running = new ConcurrentHashMap<>();
    private volatile int seconds = -1;

    @Inject
    public PingTestCommand(EssentialsSupport support) {
        this.support = support;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!support.allowed(sender, EssentialsFeature.PINGTEST.permission())) {
            return true;
        }
        if (args.length == 0 && !(sender instanceof Player)) {
            support.send(sender, "essentials.pingtest.usage");
            return true;
        }
        Optional<Player> target = support.target(sender, args, 0, EssentialsFeature.PINGTEST.permission("others"));
        if (target.isEmpty()) {
            return true;
        }
        UUID key = sender instanceof Player player ? player.getUniqueId() : UUID.nameUUIDFromBytes(new byte[0]);
        if (running.containsKey(key)) {
            support.send(sender, "essentials.pingtest.running");
            return true;
        }
        start(sender, key, target.get());
        return true;
    }

    private void start(CommandSender sender, UUID key, Player target) {
        int duration = duration();
        PingStats stats = new PingStats();
        support.send(sender, "essentials.pingtest.started", Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("seconds", String.valueOf(duration)));
        int[] elapsed = {0};
        BukkitTask task = support.plugin().getServer().getScheduler().runTaskTimer(support.plugin(), () -> {
            if (!target.isOnline()) {
                stop(key);
                support.send(sender, "essentials.pingtest.gone", Placeholder.unparsed("player", target.getName()));
                return;
            }
            stats.add(target.getPing());
            elapsed[0]++;
            if (sender instanceof Player viewer) {
                viewer.sendActionBar(support.render(viewer, "essentials.pingtest.progress",
                        Placeholder.component("bar", bar(elapsed[0], duration)),
                        Placeholder.unparsed("elapsed", String.valueOf(elapsed[0])),
                        Placeholder.unparsed("seconds", String.valueOf(duration)),
                        Placeholder.unparsed("ping", String.valueOf(target.getPing()))));
            }
            if (elapsed[0] >= duration) {
                stop(key);
                finish(sender, target, stats, duration);
            }
        }, 20L, 20L);
        running.put(key, task);
    }

    private void finish(CommandSender sender, Player target, PingStats stats, int duration) {
        support.send(sender, "essentials.pingtest.result", Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("seconds", String.valueOf(duration)),
                Placeholder.component("average", quality(sender, stats.average())),
                Placeholder.component("lowest", quality(sender, stats.lowest())),
                Placeholder.component("peak", quality(sender, stats.peak())),
                Placeholder.unparsed("spread", String.valueOf(stats.spread())),
                Placeholder.unparsed("samples", String.valueOf(stats.count())));
        support.send(sender, "essentials.pingtest.note");
    }

    private Component quality(CommandSender sender, int ping) {
        return support.render(sender, "essentials.ping." + PingCommand.quality(ping),
                Placeholder.unparsed("ms", String.valueOf(ping)));
    }

    private void stop(UUID key) {
        BukkitTask task = running.remove(key);
        if (task != null) {
            task.cancel();
        }
    }

    private int duration() {
        if (seconds < 0) {
            Path config = support.plugin().getDataFolder().toPath().resolve("config.yml");
            seconds = EssentialsSettings.pingTestSeconds(config);
        }
        return seconds;
    }

    private static Component bar(int elapsed, int duration) {
        int filled = (int) Math.round((double) elapsed / duration * BAR_SEGMENTS);
        return Component.text("|".repeat(filled), NamedTextColor.AQUA)
                .append(Component.text("|".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(EssentialsFeature.PINGTEST.permission("others"))) {
            return List.of();
        }
        return support.names(sender, args[0]);
    }
}
