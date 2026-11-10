package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.core.PrismarinCorePlugin;

@Singleton
public final class NickPermissions {

    private final LuckPermsNickHook luckPerms;

    @Inject
    public NickPermissions(PrismarinCorePlugin plugin) {
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

    public boolean groupHas(String group, String permission) {
        return luckPerms != null && group != null && !group.isBlank() && luckPerms.groupHas(group, permission);
    }

    public boolean mayAtLogin(UUID player, List<String> permissions) {
        if (luckPerms == null) {
            return true;
        }
        return luckPerms.hasAny(player, permissions).orElse(true);
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
