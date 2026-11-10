package net.prismarin.velocity.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.social.FriendInfo;
import net.prismarin.api.social.SocialResult;

public final class ProxyFriendApi implements net.prismarin.api.social.FriendService {

    private final SocialContext context;
    private final FriendService friends;

    public ProxyFriendApi(SocialContext context, FriendService friends) {
        this.context = context;
        this.friends = friends;
    }

    @Override
    public CompletableFuture<Boolean> areFriends(UUID first, UUID second) {
        return context.supply(() -> !first.equals(second) && context.store().cached(first).friendsWith(second));
    }

    @Override
    public CompletableFuture<List<FriendInfo>> friends(UUID player) {
        return context.supply(() -> {
            List<FriendInfo> list = new ArrayList<>();
            context.store().cached(player).friends().forEach((uuid, since) ->
                    list.add(new FriendInfo(uuid, context.nameOf(uuid), since)));
            list.sort(Comparator.comparing((FriendInfo info) -> info.name().toLowerCase(Locale.ROOT))
                    .thenComparing(info -> info.uuid().toString()));
            return List.copyOf(list);
        });
    }

    @Override
    public CompletableFuture<SocialResult> sendRequest(UUID sender, UUID target) {
        return context.proxy().getPlayer(sender).map(player -> friends.addAnywhere(player, target))
                .orElseGet(() -> CompletableFuture.completedFuture(SocialResult.UNAVAILABLE));
    }

    @Override
    public CompletableFuture<SocialResult> remove(UUID player, UUID friend) {
        return context.proxy().getPlayer(player).map(online -> friends.remove(online, friend))
                .orElseGet(() -> friends.unfriend(player, friend));
    }
}
