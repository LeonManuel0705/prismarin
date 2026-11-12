package net.prismarin.core.platform;

import net.prismarin.shared.config.CoreConfig;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

final class PaperMetrics {

    static final int PLUGIN_ID = 35277;

    private PaperMetrics() {
        throw new AssertionError();
    }

    static Metrics start(JavaPlugin plugin, CoreConfig config) {
        if (PLUGIN_ID <= 0) {
            return null;
        }
        Metrics metrics = new Metrics(plugin, PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("storage_mode", () -> config.storageMode().name()));
        metrics.addCustomChart(new SimplePie("redis", () -> config.redisEnabled() ? "on" : "off"));
        metrics.addCustomChart(new SimplePie("default_language", config::defaultLocale));
        metrics.addCustomChart(new SimplePie("lobby", () ->
                plugin.getServer().getPluginManager().getPlugin("PrismarinLobby") != null ? "installed" : "not installed"));
        return metrics;
    }
}
