package net.prismarin.lobby;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import net.prismarin.lobby.command.LobbyBuildCommand;
import net.prismarin.lobby.command.SetSpawnCommand;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.listener.DoubleJumpListener;
import net.prismarin.lobby.listener.HotbarListener;
import net.prismarin.lobby.listener.JoinListener;
import net.prismarin.lobby.listener.ProtectionListener;
import net.prismarin.lobby.menu.LobbyMenus;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.server.ServerConnector;
import net.prismarin.lobby.sidebar.LobbySidebarProvider;
import net.prismarin.lobby.world.LobbyWorlds;

public final class LobbyModule extends AbstractModule {

    private final LobbyPlugin plugin;

    public LobbyModule(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(LobbyPlugin.class).toInstance(plugin);
        bind(LobbyWorlds.class);
        bind(LobbyItems.class);
        bind(BuildModeService.class);
        bind(PlayerHiderService.class);
        bind(ServerConnector.class);
        bind(LobbyMenus.class);
        bind(LobbySidebarProvider.class);
        bind(JoinListener.class);
        bind(HotbarListener.class);
        bind(ProtectionListener.class);
        bind(DoubleJumpListener.class);
        bind(LobbyBuildCommand.class);
        bind(SetSpawnCommand.class);
    }

    @Provides
    @Singleton
    LobbySettings lobbySettings() {
        return LobbySettings.load(plugin.getDataFolder().toPath().resolve("config.yml"));
    }

    @Provides
    @Singleton
    LocationsFile locationsFile() {
        return LocationsFile.load(plugin.getDataFolder().toPath().resolve("locations.yml"));
    }
}
