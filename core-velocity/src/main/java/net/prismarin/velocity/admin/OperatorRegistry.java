package net.prismarin.velocity.admin;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OperatorRegistry {

    static final String PREFIX = "prismarin.";

    private final Set<UUID> operators = ConcurrentHashMap.newKeySet();

    public void update(UUID player, boolean operator) {
        if (operator) {
            operators.add(player);
        } else {
            operators.remove(player);
        }
    }

    public boolean operator(UUID player) {
        return operators.contains(player);
    }

    public int count() {
        return operators.size();
    }

    static Tristate decide(String permission, boolean operator, Tristate fallback) {
        return operator && permission.startsWith(PREFIX) ? Tristate.TRUE : fallback;
    }

    @Subscribe(order = PostOrder.LAST)
    public void onPermissionsSetup(PermissionsSetupEvent event) {
        if (!(event.getSubject() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        PermissionProvider previous = event.getProvider();
        event.setProvider(subject -> {
            PermissionFunction original = previous.createFunction(subject);
            return permission -> decide(permission, operators.contains(id), original.getPermissionValue(permission));
        });
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        operators.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        operators.remove(event.getPlayer().getUniqueId());
    }
}
