package net.prismarin.core.bridge;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

public final class VaultBridge {

    private static final String LEGACY_ECONOMY = "net.milkbowl.vault.economy.Economy";
    private static final String MODERN_ECONOMY = "net.milkbowl.vault2.economy.Economy";

    private VaultBridge() {
        throw new AssertionError();
    }

    public static boolean available(Plugin plugin) {
        return present(plugin, LEGACY_ECONOMY);
    }

    public static boolean register(Plugin plugin) {
        plugin.getServer().getServicesManager()
                .register(Economy.class, new VaultEconomy(plugin), plugin, ServicePriority.High);
        if (!present(plugin, MODERN_ECONOMY)) {
            return false;
        }
        VaultModernBridge.register(plugin);
        return true;
    }

    private static boolean present(Plugin plugin, String className) {
        try {
            Class.forName(className, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
