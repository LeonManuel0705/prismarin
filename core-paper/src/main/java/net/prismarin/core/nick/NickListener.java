package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class NickListener implements Listener {

    private static final String JOINED = "multiplayer.player.joined";
    private static final String JOINED_RENAMED = "multiplayer.player.joined.renamed";

    private final PrismarinCorePlugin plugin;
    private final NickManager nicks;

    @Inject
    public NickListener(PrismarinCorePlugin plugin, NickManager nicks) {
        this.plugin = plugin;
        this.nicks = nicks;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            nicks.handlePreLogin(event);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not prepare the nick of " + event.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            nicks.discardLogin(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (event.isAllowed()) {
            return;
        }
        UUID uuid = connectionUuid(event.getConnection());
        if (uuid != null) {
            nicks.discardLogin(uuid);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        hideFormerName(event);
        nicks.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        nicks.handleQuit(event.getPlayer());
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

    private static void hideFormerName(PlayerJoinEvent event) {
        Component message = event.joinMessage();
        if (message instanceof TranslatableComponent translatable && JOINED_RENAMED.equals(translatable.key())
                && !translatable.arguments().isEmpty()) {
            event.joinMessage(translatable.key(JOINED).arguments(translatable.arguments().subList(0, 1)));
        }
    }
}
