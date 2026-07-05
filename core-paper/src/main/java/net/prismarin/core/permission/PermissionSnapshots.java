package net.prismarin.core.permission;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Singleton
public final class PermissionSnapshots {

    private static final List<String> TRACKED = List.of(
            CorePermissions.CHAT_FORMAT,
            CorePermissions.RANK_ADMIN
    );

    private final Map<UUID, Set<String>> granted = new ConcurrentHashMap<>();

    public boolean has(Player player, String permission) {
        if (Bukkit.isPrimaryThread()) {
            boolean value = player.hasPermission(permission);
            Set<String> snapshot = granted.computeIfAbsent(player.getUniqueId(),
                    uuid -> ConcurrentHashMap.newKeySet());
            if (value) {
                snapshot.add(permission);
            } else {
                snapshot.remove(permission);
            }
            return value;
        }
        Set<String> snapshot = granted.get(player.getUniqueId());
        return snapshot != null && snapshot.contains(permission);
    }

    public void refresh(Player player) {
        Set<String> snapshot = ConcurrentHashMap.newKeySet();
        for (String permission : TRACKED) {
            if (player.hasPermission(permission)) {
                snapshot.add(permission);
            }
        }
        granted.put(player.getUniqueId(), snapshot);
    }

    public void forget(UUID uuid) {
        granted.remove(uuid);
    }
}
