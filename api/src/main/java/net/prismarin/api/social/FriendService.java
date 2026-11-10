package net.prismarin.api.social;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public interface FriendService {

    CompletableFuture<Boolean> areFriends(UUID first, UUID second);

    CompletableFuture<List<FriendInfo>> friends(UUID player);

    default CompletableFuture<Set<UUID>> friendIds(UUID player) {
        return friends(player).thenApply(list -> list.stream().map(FriendInfo::uuid)
                .collect(Collectors.toUnmodifiableSet()));
    }

    CompletableFuture<SocialResult> sendRequest(UUID sender, UUID target);

    CompletableFuture<SocialResult> remove(UUID player, UUID friend);
}
