package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.config.GameStateSettings;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.GameStateParser;
import net.prismarin.shared.status.Heartbeat;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.shared.status.SimpleGameStateService;
import net.prismarin.shared.text.TextFormatter;
import org.bukkit.Server;

@Singleton
public final class HeartbeatTask implements Runnable {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final PrismarinCorePlugin plugin;
    private final NetworkStatusService status;
    private final CoreConfig config;
    private final ExecutorService executor;
    private final SimpleGameStateService gameState;
    private final GameStateSettings settings;
    private final TextFormatter formatter;

    @Inject
    public HeartbeatTask(PrismarinCorePlugin plugin, NetworkStatusService status, CoreConfig config,
                         ExecutorService executor, SimpleGameStateService gameState, GameStateSettings settings,
                         TextFormatter formatter) {
        this.plugin = plugin;
        this.status = status;
        this.config = config;
        this.executor = executor;
        this.gameState = gameState;
        this.settings = settings;
        this.formatter = formatter;
    }

    @Override
    public void run() {
        detectState();
        Server server = plugin.getServer();
        Runtime runtime = Runtime.getRuntime();
        Heartbeat heartbeat = new Heartbeat(
                config.serverId(),
                server.getOnlinePlayers().size(),
                server.getMaxPlayers(),
                Math.min(20D, server.getTPS()[0]),
                server.getAverageTickTime(),
                (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
                runtime.maxMemory() / BYTES_PER_MB,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000L,
                server.getMinecraftVersion(),
                System.currentTimeMillis(),
                gameState.state().name(),
                gameState.stateText());
        executor.execute(() -> status.publish(heartbeat));
    }

    private void detectState() {
        if (gameState.manual() || settings.placeholder().isBlank()) {
            return;
        }
        String expanded = formatter.expandExternal(null, settings.placeholder());
        if (expanded.equals(settings.placeholder())) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(formatter.format(expanded));
        gameState.detected(GameStateParser.parse(text, settings.mapping()), text);
    }
}
