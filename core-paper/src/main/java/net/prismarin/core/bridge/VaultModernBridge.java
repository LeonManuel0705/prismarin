package net.prismarin.core.bridge;

import net.milkbowl.vault2.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

final class VaultModernBridge {

    private VaultModernBridge() {
        throw new AssertionError();
    }

    static void register(Plugin plugin) {
        plugin.getServer().getServicesManager()
                .register(Economy.class, new VaultModernEconomy(plugin), plugin, ServicePriority.High);
    }
}
