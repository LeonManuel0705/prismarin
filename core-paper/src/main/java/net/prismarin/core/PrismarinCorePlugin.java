package net.prismarin.core;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.prismarin.api.Prismarin;
import net.prismarin.api.PrismarinProvider;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.bridge.PlaceholderBridge;
import net.prismarin.core.bridge.VaultBridge;
import net.prismarin.core.chat.AsyncChatListener;
import net.prismarin.core.command.CoinsCommand;
import net.prismarin.core.command.EconomyCommand;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.network.HeartbeatTask;
import net.prismarin.core.network.NetworkCommand;
import net.prismarin.core.network.NetworkMenu;
import net.prismarin.core.profile.ConnectionListener;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.core.tablist.TablistManager;
import net.prismarin.core.task.PeriodicSaveTask;
import net.prismarin.shared.config.ConfigLoader;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.status.NetworkStatusService;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PrismarinCorePlugin extends JavaPlugin {

    private static final long SAVE_INTERVAL_TICKS = 20L * 60L * 5L;
    private static final long SHUTDOWN_SAVE_TIMEOUT_SECONDS = 15L;
    private static final long HEARTBEAT_INTERVAL_TICKS = 100L;
    private static final long STATUS_INTERVAL_TICKS = 200L;

    private Injector injector;
    private SidebarManager sidebarManager;
    private TablistManager tablistManager;
    private BukkitTask saveTask;
    private BukkitTask heartbeatTask;
    private BukkitTask statusTask;
    private PlaceholderBridge placeholderBridge;

    @Override
    public void onLoad() {
        CoreConfig config = ConfigLoader.load(getDataFolder().toPath().resolve("config.yml"), CoreModules.ALL);
        if (ModuleState.compute(config, name -> false).enabled(CoreModules.VAULT) && VaultBridge.available(this)) {
            VaultBridge.register(this);
            getLogger().info("Registered as Vault economy provider");
        }
    }

    @Override
    public void onEnable() {
        injector = Guice.createInjector(new CoreModule(this));
        CoreConfig config = injector.getInstance(CoreConfig.class);
        CoreServices services = injector.getInstance(CoreServices.class);
        ModuleState modules = injector.getInstance(ModuleState.class);
        Prismarin prismarin = injector.getInstance(Prismarin.class);

        PluginManager pluginManager = getServer().getPluginManager();
        if (modules.enabled(CoreModules.CHAT)) {
            pluginManager.registerEvents(injector.getInstance(AsyncChatListener.class), this);
        }
        pluginManager.registerEvents(injector.getInstance(ConnectionListener.class), this);
        pluginManager.registerEvents(injector.getInstance(MenuService.class), this);

        if (modules.enabled(CoreModules.ECONOMY_COMMANDS)) {
            registerCommand("coins", injector.getInstance(CoinsCommand.class));
            registerCommand("economy", injector.getInstance(EconomyCommand.class));
        } else {
            removeCommand("coins");
            removeCommand("economy");
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, NetworkMenu.CHANNEL);
        if (modules.enabled(CoreModules.NETWORK_MENU)) {
            registerCommand("network", injector.getInstance(NetworkCommand.class));
        } else {
            removeCommand("network");
        }
        if (config.systemEnabled(PrismarinSystems.NETWORK_STATUS)) {
            NetworkStatusService status = injector.getInstance(NetworkStatusService.class);
            heartbeatTask = getServer().getScheduler()
                    .runTaskTimer(this, injector.getInstance(HeartbeatTask.class), 20L, HEARTBEAT_INTERVAL_TICKS);
            statusTask = getServer().getScheduler()
                    .runTaskTimerAsynchronously(this, () -> status.refresh(), 40L, STATUS_INTERVAL_TICKS);
        }

        if (modules.enabled(CoreModules.SIDEBAR)) {
            sidebarManager = injector.getInstance(SidebarManager.class);
            sidebarManager.start();
        }
        if (modules.enabled(CoreModules.TABLIST)) {
            tablistManager = injector.getInstance(TablistManager.class);
            tablistManager.start();
        }
        saveTask = getServer().getScheduler()
                .runTaskTimer(this, injector.getInstance(PeriodicSaveTask.class), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        CoreApi.register(injector);
        PrismarinProvider.register(prismarin);
        getServer().getServicesManager().register(Prismarin.class, prismarin, this, ServicePriority.Normal);
        if (modules.enabled(CoreModules.PLACEHOLDER_API) && pluginManager.getPlugin("PlaceholderAPI") != null) {
            placeholderBridge = PlaceholderBridge.enable(prismarin, services.formatter(), getPluginMeta().getVersion());
            getLogger().info("Registered PlaceholderAPI expansion %prismarin_...%");
        }
        getLogger().info("Enabled as '" + config.serverId() + "' with systems " + prismarin.enabledSystems().stream()
                .sorted().toList() + " and modules " + modules.enabledModules());
        modules.disabledReasons().forEach((module, reason) -> {
            if (config.moduleEnabled(module)) {
                getLogger().info("Module " + module + " is off because " + reason);
            }
        });
    }

    @Override
    public void onDisable() {
        if (placeholderBridge != null) {
            placeholderBridge.disable();
            placeholderBridge = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        PrismarinProvider.unregister();
        CoreApi.unregister();
        for (BukkitTask task : new BukkitTask[]{heartbeatTask, statusTask}) {
            if (task != null) {
                task.cancel();
            }
        }
        heartbeatTask = null;
        statusTask = null;
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (tablistManager != null) {
            tablistManager.stop();
            tablistManager = null;
        }
        if (sidebarManager != null) {
            sidebarManager.stop();
            sidebarManager = null;
        }
        if (injector != null) {
            flushProfiles(injector.getInstance(ProfileService.class));
            shutdownExecutor(injector.getInstance(ExecutorService.class));
            injector.getInstance(CoreServices.class).close();
            injector = null;
        }
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void flushProfiles(ProfileService profiles) {
        CompletableFuture<?>[] pending = getServer().getOnlinePlayers().stream()
                .map(player -> profiles.unload(player.getUniqueId()))
                .toArray(CompletableFuture[]::new);
        if (pending.length == 0) {
            return;
        }
        try {
            CompletableFuture.allOf(pending).get(SHUTDOWN_SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().warning("Interrupted while saving " + pending.length + " profiles on shutdown");
        } catch (ExecutionException | TimeoutException e) {
            getLogger().warning("Could not save all profiles on shutdown: " + e.getMessage());
        }
    }

    private void removeCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }
        command.unregister(getServer().getCommandMap());
        getServer().getCommandMap().getKnownCommands().values().removeIf(known -> known == command);
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("command " + name + " is missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
