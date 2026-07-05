package net.prismarin.core;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.chat.AsyncChatListener;
import net.prismarin.core.command.CoinsCommand;
import net.prismarin.core.command.EconomyCommand;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.profile.ConnectionListener;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.core.storage.ProfileStorage;
import net.prismarin.core.tablist.TablistManager;
import net.prismarin.core.task.PeriodicSaveTask;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class PrismarinCorePlugin extends JavaPlugin {

    private static final long SAVE_INTERVAL_TICKS = 20L * 60L * 5L;
    private static final long SHUTDOWN_SAVE_TIMEOUT_SECONDS = 15L;

    private Injector injector;
    private SidebarManager sidebarManager;
    private TablistManager tablistManager;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        injector = Guice.createInjector(new CoreModule(this));
        injector.getInstance(CoreConfig.class);
        injector.getInstance(LocaleService.class);
        injector.getInstance(ServerDirectory.class);
        injector.getInstance(MessageBus.class);
        sidebarManager = injector.getInstance(SidebarManager.class);
        tablistManager = injector.getInstance(TablistManager.class);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(injector.getInstance(AsyncChatListener.class), this);
        pluginManager.registerEvents(injector.getInstance(ConnectionListener.class), this);
        pluginManager.registerEvents(injector.getInstance(MenuService.class), this);

        registerCommand("coins", injector.getInstance(CoinsCommand.class));
        registerCommand("economy", injector.getInstance(EconomyCommand.class));

        sidebarManager.start();
        tablistManager.start();
        saveTask = getServer().getScheduler()
                .runTaskTimer(this, injector.getInstance(PeriodicSaveTask.class), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        CoreApi.register(injector);
    }

    @Override
    public void onDisable() {
        CoreApi.unregister();
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
            injector.getInstance(ProfileStorage.class).close();
            injector.getInstance(MessageBus.class).close();
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

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("command " + name + " is missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
