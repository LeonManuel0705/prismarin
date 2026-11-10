package net.prismarin.velocity.social;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.velocity.admin.ModerationChannel;

public final class SocialChannel implements ModerationChannel.SocialHandler {

    private final SocialContext context;
    private final FriendService friends;
    private final PartyService party;
    private final SocialViews views;

    public SocialChannel(SocialContext context, FriendService friends, PartyService party, SocialViews views) {
        this.context = context;
        this.friends = friends;
        this.party = party;
        this.views = views;
    }

    @Override
    public void handle(Player player, JsonObject message) {
        Optional<SocialRequest> parsed = SocialRequest.parse(message);
        if (parsed.isEmpty()) {
            return;
        }
        SocialRequest request = parsed.get();
        perform(player, request)
                .thenCompose(unused -> context.supply(() -> views.build(player, request)))
                .thenAccept(view -> {
                    if (player.isActive()) {
                        context.channel().sendSocial(player, view);
                    }
                })
                .exceptionally(error -> null);
    }

    private CompletableFuture<Void> perform(Player player, SocialRequest request) {
        UUID target = request.target();
        boolean friendsOn = context.friendsEnabled();
        boolean partyOn = context.partyEnabled();
        return switch (request.action()) {
            case HELLO, VIEW -> CompletableFuture.completedFuture(null);
            case FRIEND_ADD -> friendsOn ? addFromParty(player, target) : done();
            case FRIEND_ACCEPT -> friendsOn ? friends.accept(player, target) : done();
            case FRIEND_DENY -> friendsOn ? friends.deny(player, target) : done();
            case FRIEND_CANCEL -> friendsOn ? friends.cancel(player, target) : done();
            case FRIEND_REMOVE -> friendsOn ? friends.remove(player, target) : done();
            case FRIEND_JUMP -> friendsOn ? friends.jump(player, target) : done();
            case PARTY_INVITE -> partyOn ? party.invite(player, target) : done();
            case PARTY_ACCEPT -> partyOn ? party.accept(player, target) : done();
            case PARTY_DENY -> partyOn ? party.deny(player, target) : done();
            case PARTY_LEAVE -> partyOn ? party.leave(player) : done();
            case PARTY_KICK -> partyOn ? party.kick(player, target) : done();
            case PARTY_PROMOTE -> partyOn ? party.promote(player, target) : done();
            case PARTY_DISBAND -> partyOn ? party.disband(player) : done();
            case PARTY_WARP -> partyOn ? party.warp(player) : done();
            case SETTING -> context.enabled(request.setting().feature())
                    ? friends.change(player, request.setting(), request.value()) : done();
        };
    }

    private CompletableFuture<Void> addFromParty(Player player, UUID target) {
        boolean together = party.parties().partyOf(player.getUniqueId())
                .map(current -> current.member(target)).orElse(false);
        return together ? friends.add(player, target) : done();
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
