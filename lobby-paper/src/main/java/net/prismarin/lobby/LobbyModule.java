package net.prismarin.lobby;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.List;
import net.prismarin.lobby.action.ActionRunner;
import net.prismarin.lobby.command.LobbyBuildCommand;
import net.prismarin.lobby.command.SetSpawnCommand;
import net.prismarin.lobby.command.SetVoidCommand;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.extension.LobbyExtensions;
import net.prismarin.lobby.extension.PaperLobbyService;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.leaderboard.LeaderboardCommand;
import net.prismarin.lobby.leaderboard.LeaderboardDisplays;
import net.prismarin.lobby.leaderboard.LeaderboardsFile;
import net.prismarin.lobby.listener.DoubleJumpListener;
import net.prismarin.lobby.listener.HotbarListener;
import net.prismarin.lobby.listener.JoinListener;
import net.prismarin.lobby.listener.ProtectionListener;
import net.prismarin.lobby.listener.VoidTeleportListener;
import net.prismarin.lobby.menu.LanguageMenu;
import net.prismarin.lobby.menu.LobbyMenus;
import net.prismarin.lobby.menu.LobbySelectorMenu;
import net.prismarin.lobby.menu.MenuKit;
import net.prismarin.lobby.menu.NickToolMenu;
import net.prismarin.lobby.menu.SocialMenus;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.network.LobbyTravel;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.LobbyPlayerSetup;
import net.prismarin.lobby.player.LobbyVisibility;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.player.SilentLobbyService;
import net.prismarin.lobby.joinme.JoinMeEntries;
import net.prismarin.lobby.profile.ProfileEntries;
import net.prismarin.lobby.server.ServerConnector;
import net.prismarin.lobby.sidebar.LobbySidebarProvider;
import net.prismarin.lobby.util.ConfigText;
import net.prismarin.lobby.util.CurrencyDisplay;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.World;

public final class LobbyModule extends AbstractModule {

    private final LobbyPlugin plugin;

    public LobbyModule(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(LobbyPlugin.class).toInstance(plugin);
        bind(LobbyWorlds.class);
        bind(LobbyDirectory.class);
        bind(LobbyTravel.class);
        bind(ConfigText.class);
        bind(CurrencyDisplay.class);
        bind(LobbyItems.class);
        bind(BuildModeService.class);
        bind(PlayerHiderService.class);
        bind(LobbyVisibility.class);
        bind(SilentLobbyService.class);
        bind(LobbyPlayerSetup.class);
        bind(ServerConnector.class);
        bind(MenuKit.class);
        bind(LanguageMenu.class);
        bind(LobbySelectorMenu.class);
        bind(LobbyMenus.class);
        bind(SocialMenus.class);
        bind(NickToolMenu.class);
        bind(ActionRunner.class);
        bind(LobbySidebarProvider.class);
        bind(JoinListener.class);
        bind(HotbarListener.class);
        bind(ProtectionListener.class);
        bind(VoidTeleportListener.class);
        bind(DoubleJumpListener.class);
        bind(LobbyBuildCommand.class);
        bind(SetSpawnCommand.class);
        bind(SetVoidCommand.class);
        bind(LobbyExtensions.class);
        bind(PaperLobbyService.class);
        bind(LeaderboardDisplays.class);
        bind(LeaderboardCommand.class);
        bind(ProfileEntries.class);
        bind(JoinMeEntries.class);
    }

    @Provides
    @Singleton
    LobbySettings lobbySettings(LocationsFile locations) {
        String defaultWorld = locations.firstSpawnWorld().orElseGet(this::mainWorld);
        return LobbySettings.load(plugin.getDataFolder().toPath().resolve("config.yml"), defaultWorld);
    }

    @Provides
    @Singleton
    LocationsFile locationsFile() {
        return LocationsFile.load(plugin.getDataFolder().toPath().resolve("locations.yml"));
    }

    @Provides
    @Singleton
    LeaderboardsFile leaderboardsFile() {
        return LeaderboardsFile.load(plugin.getDataFolder().toPath().resolve(LeaderboardsFile.FILE_NAME));
    }

    private String mainWorld() {
        List<World> worlds = plugin.getServer().getWorlds();
        return worlds.isEmpty() ? LobbySettings.DEFAULT_WORLD : worlds.getFirst().getName();
    }
}
