package net.prismarin.core.nick;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import net.prismarin.core.permission.PermissionSnapshots;
import org.bukkit.entity.Player;

@Singleton
public final class NickPerks {

    private final NickRegistry registry;
    private final Provider<FakeRankFormat> fake;
    private final PermissionSnapshots snapshots;

    @Inject
    public NickPerks(NickRegistry registry, Provider<FakeRankFormat> fake, PermissionSnapshots snapshots) {
        this.registry = registry;
        this.fake = fake;
        this.snapshots = snapshots;
    }

    public boolean has(Player player, String permission) {
        if (registry.nicked(player.getUniqueId())) {
            return fake.get().hasPerk(permission);
        }
        return snapshots.has(player, permission);
    }
}
