package net.prismarin.core.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.social.FriendInfo;
import net.prismarin.api.social.FriendService;
import net.prismarin.api.social.SocialResult;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.social.FriendDocuments;
import org.bukkit.entity.Player;

@Singleton
public final class BackendFriendService implements FriendService {

    private final PrismarinCorePlugin plugin;
    private final SocialClient client;
    private final FriendDocuments documents;
    private final ProfileService profiles;
    private final ExecutorService executor;
    private final boolean shared;

    @Inject
    public BackendFriendService(PrismarinCorePlugin plugin, SocialClient client, CoreServices services,
                                CoreConfig config, ExecutorService executor) {
        this.plugin = plugin;
        this.client = client;
        this.documents = new FriendDocuments(services.documents());
        this.profiles = services.profiles();
        this.executor = executor;
        this.shared = sharedStorage(config.storageMode());
    }

    public static boolean sharedStorage(CoreConfig.StorageMode mode) {
        return mode == CoreConfig.StorageMode.MONGO || mode == CoreConfig.StorageMode.MYSQL;
    }

    @Override
    public CompletableFuture<Boolean> areFriends(UUID first, UUID second) {
        if (first.equals(second)) {
            return CompletableFuture.completedFuture(false);
        }
        Player carrier = shared ? null : carrier(first, second);
        if (carrier == null) {
            return CompletableFuture.supplyAsync(() -> documents.areFriends(first, second), executor);
        }
        return client.query(carrier, "are-friends", first, second)
                .thenApply(answer -> flag(answer.get("friends")))
                .exceptionally(error -> false);
    }

    @Override
    public CompletableFuture<List<FriendInfo>> friends(UUID player) {
        Player carrier = shared ? null : carrier(player, null);
        if (carrier == null) {
            return CompletableFuture.supplyAsync(() -> documents.friends(player, this::profileName), executor);
        }
        return client.query(carrier, "friends", player, null)
                .thenApply(BackendFriendService::friendList)
                .exceptionally(error -> List.of());
    }

    @Override
    public CompletableFuture<SocialResult> sendRequest(UUID sender, UUID target) {
        return act(sender, "friend-request", target);
    }

    @Override
    public CompletableFuture<SocialResult> remove(UUID player, UUID friend) {
        return act(player, "friend-remove", friend);
    }

    static List<FriendInfo> friendList(JsonObject answer) {
        List<FriendInfo> friends = new ArrayList<>();
        JsonElement raw = answer.get("friends");
        if (raw == null || !raw.isJsonArray()) {
            return friends;
        }
        for (JsonElement entry : raw.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject item = entry.getAsJsonObject();
            try {
                UUID uuid = UUID.fromString(item.get("uuid").getAsString());
                String name = item.has("name") ? item.get("name").getAsString() : "";
                long since = item.has("since") ? item.get("since").getAsLong() : 0L;
                friends.add(new FriendInfo(uuid, name, since));
            } catch (RuntimeException ignored) {
                continue;
            }
        }
        return List.copyOf(friends);
    }

    private Optional<String> profileName(UUID uuid) {
        try {
            return profiles.find(uuid).get(2, TimeUnit.SECONDS).map(PlayerProfile::getName);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private CompletableFuture<SocialResult> act(UUID actor, String action, UUID target) {
        Player player = plugin.getServer().getPlayer(actor);
        if (player == null) {
            return CompletableFuture.completedFuture(SocialResult.UNAVAILABLE);
        }
        return client.perform(player, action, target, null);
    }

    private Player carrier(UUID first, UUID second) {
        Player own = plugin.getServer().getPlayer(first);
        if (own == null && second != null) {
            own = plugin.getServer().getPlayer(second);
        }
        if (own != null) {
            return own;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            return online;
        }
        return null;
    }

    private static boolean flag(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }
}
