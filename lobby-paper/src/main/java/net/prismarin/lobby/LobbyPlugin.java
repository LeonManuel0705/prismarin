package net.prismarin.lobby;

import com.google.inject.Injector;
import net.prismarin.core.CoreApi;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.lobby.command.LobbyBuildCommand;
import net.prismarin.lobby.command.SetSpawnCommand;
import net.prismarin.lobby.listener.DoubleJumpListener;
import net.prismarin.lobby.listener.HotbarListener;
import net.prismarin.lobby.listener.JoinListener;
import net.prismarin.lobby.listener.ProtectionListener;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.server.ServerConnector;
import net.prismarin.lobby.sidebar.LobbySidebarProvider;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    private Injector injector;
    private SidebarManager sidebarManager;
    private LobbySidebarProvider sidebarProvider;

    @Override
    public void onEnable() {
        injector = CoreApi.get().injector().createChildInjector(new LobbyModule(this));
        getServer().getMessenger().registerOutgoingPluginChannel(this, ServerConnector.CHANNEL);
        injector.getInstance(LobbyWorlds.class).lockWeather();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(injector.getInstance(JoinListener.class), this);
        pluginManager.registerEvents(injector.getInstance(HotbarListener.class), this);
        pluginManager.registerEvents(injector.getInstance(ProtectionListener.class), this);
        pluginManager.registerEvents(injector.getInstance(DoubleJumpListener.class), this);
        pluginManager.registerEvents(injector.getInstance(PlayerHiderService.class), this);

        registerCommand("lobbybuild", injector.getInstance(LobbyBuildCommand.class));
        registerCommand("setspawn", injector.getInstance(SetSpawnCommand.class));

        sidebarManager = injector.getInstance(SidebarManager.class);
        sidebarProvider = injector.getInstance(LobbySidebarProvider.class);
        sidebarManager.provider(sidebarProvider);
    }

    @Override
    public void onDisable() {
        if (sidebarManager != null && sidebarManager.provider() == sidebarProvider) {
            sidebarManager.provider(null);
        }
        sidebarManager = null;
        sidebarProvider = null;
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        injector = null;
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
