package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import net.prismarin.api.queue.QueueApi;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

@Singleton
public final class QueueBypassListener implements Listener {

    private final CoreConfig config;
    private final ServerDirectory directory;

    @Inject
    public QueueBypassListener(CoreConfig config, ServerDirectory directory) {
        this.config = config;
        this.directory = directory;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.KICK_FULL) {
            return;
        }
        Player player = event.getPlayer();
        String server = config.serverId();
        String group = directory.byName(server).map(NetworkServer::group).orElse("");
        if (player.hasPermission(QueueApi.BYPASS_PERMISSION)
                || player.hasPermission(QueueApi.BYPASS_PERMISSION + "." + server.toLowerCase(Locale.ROOT))
                || !group.isEmpty() && player.hasPermission(QueueApi.BYPASS_PERMISSION + "." + group)) {
            event.allow();
        }
    }
}
