package net.prismarin.core.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.scoreboard.PlayerBoards;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.core.tablist.TablistManager;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class ConnectionListener implements Listener {

    private static final long LOAD_TIMEOUT_SECONDS = 10L;

    private final ProfileService profiles;
    private final LocaleService locale;
    private final CoreConfig config;
    private final SidebarManager sidebar;
    private final TablistManager tablist;
    private final PlayerBoards boards;

    @Inject
    public ConnectionListener(ProfileService profiles, LocaleService locale, CoreConfig config,
                              SidebarManager sidebar, TablistManager tablist, PlayerBoards boards) {
        this.profiles = profiles;
        this.locale = locale;
        this.config = config;
        this.sidebar = sidebar;
        this.tablist = tablist;
        this.boards = boards;
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();
        profiles.getCached(player.getUniqueId())
                .ifPresent(profile -> profile.setLocale(player.locale().getLanguage()));
        String key = firstJoin ? "join.first-message" : "join.message";
        event.joinMessage(locale.render(key, player.locale(),
                Placeholder.unparsed("player", player.getName())));
        tablist.handleJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(locale.render("quit.message", player.locale(),
                Placeholder.unparsed("player", player.getName())));
        sidebar.handleQuit(player);
        tablist.handleQuit(player);
        boards.remove(player.getUniqueId());
        profiles.unload(player.getUniqueId());
    }

    private void disallow(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                locale.render("join.profile-error", Locale.forLanguageTag(config.defaultLocale())));
    }
}
