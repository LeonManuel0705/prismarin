package net.prismarin.core.moderation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;

@Singleton
public final class OperatorReporter implements Listener {

    private static final long JOIN_DELAY_TICKS = 20L;
    private static final long COMMAND_DELAY_TICKS = 2L;

    private final PrismarinCorePlugin plugin;
    private final ModerationChannel channel;

    @Inject
    public OperatorReporter(PrismarinCorePlugin plugin, ModerationChannel channel) {
        this.plugin = plugin;
        this.channel = channel;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                channel.sendOperator(player);
            }
        }, JOIN_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        refreshIfOperatorCommand(event.getMessage().substring(1));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsoleCommand(ServerCommandEvent event) {
        refreshIfOperatorCommand(event.getCommand());
    }

    private void refreshIfOperatorCommand(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("op ") && !lower.startsWith("deop ") && !lower.startsWith("minecraft:op ")
                && !lower.startsWith("minecraft:deop ")) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(channel::sendOperator), COMMAND_DELAY_TICKS);
    }
}
