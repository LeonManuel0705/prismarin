package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

@Singleton
public final class NickPermissions {

    private final LuckPermsNickHook luckPerms;
    private final PrismarinCorePlugin plugin;

    @Inject
    public NickPermissions(PrismarinCorePlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null
                ? new LuckPermsNickHook()
                : null;
    }

    public boolean luckPerms() {
        return luckPerms != null;
    }

    public Optional<FakeRankResolver.GroupLook> group(String name) {
        if (luckPerms == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return luckPerms.group(name);
    }

    public List<FakeRankResolver.GroupLook> groups() {
        return luckPerms == null ? List.of() : luckPerms.groups();
    }

    public boolean groupHas(String group, String permission) {
        return luckPerms != null && group != null && !group.isBlank() && luckPerms.groupHas(group, permission);
    }

    public boolean mayAtLogin(UUID player, List<String> permissions) {
        if (luckPerms == null) {
            return true;
        }
        if (luckPerms.hasAny(player, permissions).orElse(true)) {
            return true;
        }
        boolean operator = plugin.getServer().getOperators().stream()
                .anyMatch(op -> player.equals(op.getUniqueId()));
        return permissions.stream().anyMatch(node -> grantedByDefault(node, operator));
    }

    private boolean grantedByDefault(String node, boolean operator) {
        Permission permission = plugin.getServer().getPluginManager().getPermission(node);
        PermissionDefault value = permission == null ? Permission.DEFAULT_PERMISSION : permission.getDefault();
        return value.getValue(operator);
    }

    void disguise(UUID player, String prefix, int priority, String nameColor) {
        if (luckPerms != null) {
            luckPerms.disguise(player, prefix, priority, nameColor);
        }
    }

    void undisguise(UUID player) {
        if (luckPerms != null) {
            luckPerms.undisguise(player);
        }
    }

    void undisguiseAll() {
        if (luckPerms != null) {
            luckPerms.undisguiseAll();
        }
    }
}
