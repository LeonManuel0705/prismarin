package net.prismarin.lobby;

import com.google.inject.Injector;
import net.prismarin.core.CoreApi;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.lobby.command.LobbyBuildCommand;
import net.prismarin.lobby.command.SetSpawnCommand;
import net.prismarin.lobby.command.SetVoidCommand;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.listener.DoubleJumpListener;
import net.prismarin.lobby.listener.HotbarListener;
import net.prismarin.lobby.listener.JoinListener;
import net.prismarin.lobby.listener.ProtectionListener;
import net.prismarin.lobby.listener.VoidTeleportListener;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.player.SilentLobbyService;
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
        LobbySettings settings = injector.getInstance(LobbySettings.class);
        settings.warnings().forEach(warning -> getLogger().warning("config.yml: " + warning));
        LobbyWorlds worlds = injector.getInstance(LobbyWorlds.class);
        worlds.lockWeather();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(injector.getInstance(JoinListener.class), this);
        pluginManager.registerEvents(injector.getInstance(HotbarListener.class), this);
        if (settings.enabled(LobbySettings.PROTECTION)) {
            pluginManager.registerEvents(injector.getInstance(ProtectionListener.class), this);
        }
        if (settings.enabled(LobbySettings.VOID_TELEPORT)) {
            pluginManager.registerEvents(injector.getInstance(VoidTeleportListener.class), this);
        }
        if (settings.enabled(LobbySettings.DOUBLE_JUMP)) {
            pluginManager.registerEvents(injector.getInstance(DoubleJumpListener.class), this);
        }
        if (settings.enabled(LobbySettings.PLAYER_HIDER)) {
            pluginManager.registerEvents(injector.getInstance(PlayerHiderService.class), this);
        }
        if (settings.enabled(LobbySettings.SILENT_LOBBY)) {
            pluginManager.registerEvents(injector.getInstance(SilentLobbyService.class), this);
        }

        registerCommand("lobbybuild", injector.getInstance(LobbyBuildCommand.class));
        registerCommand("setspawn", injector.getInstance(SetSpawnCommand.class));
        registerCommand("setvoid", injector.getInstance(SetVoidCommand.class));

        if (settings.enabled(LobbySettings.SIDEBAR)) {
            sidebarManager = injector.getInstance(SidebarManager.class);
            sidebarProvider = injector.getInstance(LobbySidebarProvider.class);
            sidebarManager.provider(sidebarProvider);
        }
        getLogger().info("Lobby features " + LobbySettings.ALL.stream().filter(settings::enabled).toList()
                + " in worlds " + worlds.names());
        LocationsFile locations = injector.getInstance(LocationsFile.class);
        if (locations.spawns().isEmpty()) {
            getLogger().info("No lobby spawn is set yet, players stay where they join. Use /setspawn in the lobby.");
        } else if (settings.enabled(LobbySettings.VOID_TELEPORT)) {
            locations.spawns().keySet().stream()
                    .filter(world -> locations.voidHeight(world).isEmpty())
                    .forEach(world -> getLogger().info("Void teleport in " + world
                            + " is off until /setvoid <height> is used there"));
        }
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
