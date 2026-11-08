package net.prismarin.core.bridge;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

public final class VaultBridge {

    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private VaultBridge() {
        throw new AssertionError();
    }

    public static boolean available(Plugin plugin) {
        try {
            Class.forName(ECONOMY_CLASS, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static void register(Plugin plugin) {
        plugin.getServer().getServicesManager()
                .register(Economy.class, new VaultEconomy(plugin), plugin, ServicePriority.High);
    }
}
