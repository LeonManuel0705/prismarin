package net.prismarin.core.api;

import java.util.Optional;
import net.prismarin.api.event.Subscription;
import org.bukkit.entity.Player;

public interface SidebarService {

    int LOBBY_PRIORITY = 0;

    Subscription register(SidebarProvider provider, int priority);

    default Subscription register(SidebarProvider provider) {
        return register(provider, LOBBY_PRIORITY);
    }

    Optional<SidebarProvider> active(Player player);

    void refresh(Player player);
}
