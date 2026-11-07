package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.Heartbeat;
import net.prismarin.shared.status.NetworkStatusService;
import org.bukkit.Server;

@Singleton
public final class HeartbeatTask implements Runnable {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final PrismarinCorePlugin plugin;
    private final NetworkStatusService status;
    private final CoreConfig config;
    private final ExecutorService executor;

    @Inject
    public HeartbeatTask(PrismarinCorePlugin plugin, NetworkStatusService status, CoreConfig config,
                         ExecutorService executor) {
        this.plugin = plugin;
        this.status = status;
        this.config = config;
        this.executor = executor;
    }

    @Override
    public void run() {
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
                System.currentTimeMillis());
        executor.execute(() -> status.publish(heartbeat));
    }
}
