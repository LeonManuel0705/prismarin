package net.prismarin.core.profile;

import com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.CoreModules;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.permission.PermissionSnapshots;
import net.prismarin.core.scoreboard.PlayerBoards;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.core.tablist.TablistManager;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class ConnectionListener implements Listener {

    private static final long LOAD_TIMEOUT_SECONDS = 10L;

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;
    private final LocaleService locale;
    private final CoreConfig config;
    private final SidebarManager sidebar;
    private final TablistManager tablist;
    private final PlayerBoards boards;
    private final PermissionSnapshots snapshots;

    @Inject
    public ConnectionListener(PrismarinCorePlugin plugin, ProfileService profiles, LocaleService locale,
                              CoreConfig config, SidebarManager sidebar, TablistManager tablist,
                              PlayerBoards boards, PermissionSnapshots snapshots) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.locale = locale;
        this.config = config;
        this.sidebar = sidebar;
        this.tablist = tablist;
        this.boards = boards;
        this.snapshots = snapshots;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            profiles.load(event.getUniqueId(), event.getName()).get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            disallow(event);
        } catch (ExecutionException | TimeoutException e) {
            disallow(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            discard(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (event.isAllowed()) {
            return;
        }
        UUID uuid = connectionUuid(event.getConnection());
        if (uuid != null) {
            discard(uuid);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoginResult(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            discard(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        snapshots.refresh(player);
        if (config.moduleEnabled(CoreModules.JOIN_MESSAGES)) {
            String key = player.hasPlayedBefore() ? "join.message" : "join.first-message";
            event.joinMessage(null);
            broadcast(key, player.getName(), null);
        }
        if (config.moduleEnabled(CoreModules.TABLIST)) {
            tablist.handleJoin(player);
        }
    }

    @EventHandler
    public void onClientOptions(PlayerClientOptionsChangeEvent event) {
        String language = languageOf(event.getLocale());
        if (language.isEmpty()) {
            return;
        }
        profiles.getCached(event.getPlayer().getUniqueId()).ifPresent(profile -> {
            if (!Boolean.parseBoolean(profile.getSettings().get(PlayerProfile.SETTING_LOCALE_OVERRIDE))) {
                profile.setLocale(language);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (config.moduleEnabled(CoreModules.JOIN_MESSAGES)) {
            event.quitMessage(null);
            broadcast("quit.message", player.getName(), player.getUniqueId());
        }
        sidebar.handleQuit(player);
        if (config.moduleEnabled(CoreModules.TABLIST)) {
            tablist.handleQuit(player);
        }
        boards.remove(player.getUniqueId());
        snapshots.forget(player.getUniqueId());
        profiles.unload(player.getUniqueId()).whenComplete((unused, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not save profile of " + player.getName() + " on quit", error);
            }
        });
    }

    private void broadcast(String key, String playerName, UUID excluded) {
        TagResolver name = Placeholder.unparsed("player", playerName);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(excluded)) {
                continue;
            }
            viewer.sendMessage(locale.render(key, PlayerLocales.of(viewer, profiles), name));
        }
        plugin.getServer().getConsoleSender().sendMessage(
                locale.render(key, Locale.forLanguageTag(config.defaultLocale()), name));
    }

    private void discard(UUID uuid) {
        profiles.unload(uuid).whenComplete((unused, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not discard profile of " + uuid + " after denied login", error);
            }
        });
    }

    private void disallow(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                locale.render("join.profile-error", Locale.forLanguageTag(config.defaultLocale())));
    }

    private static UUID connectionUuid(PlayerConnection connection) {
        if (connection instanceof PlayerLoginConnection login) {
            return login.getAuthenticatedProfile() == null ? null : login.getAuthenticatedProfile().getId();
        }
        if (connection instanceof PlayerConfigurationConnection configuration) {
            return configuration.getProfile().getId();
        }
        return null;
    }

    private static String languageOf(String clientLocale) {
        if (clientLocale == null || clientLocale.isEmpty()) {
            return "";
        }
        int separator = clientLocale.indexOf('_');
        String language = separator > 0 ? clientLocale.substring(0, separator) : clientLocale;
        return language.toLowerCase(Locale.ROOT);
    }
}
