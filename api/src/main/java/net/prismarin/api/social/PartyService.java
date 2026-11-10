package net.prismarin.api.social;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PartyService {

    Optional<Party> partyOf(UUID player);

    Optional<Party> party(UUID partyId);

    CompletableFuture<Optional<Party>> lookup(UUID player);

    default boolean together(UUID first, UUID second) {
        return partyOf(first).map(party -> party.contains(second)).orElse(false);
    }

    default Optional<PartyRole> roleOf(UUID player) {
        return partyOf(player).flatMap(party -> party.role(player));
    }

    CompletableFuture<SocialResult> invite(UUID actor, UUID target);

    CompletableFuture<SocialResult> join(UUID player, UUID member);

    CompletableFuture<SocialResult> leave(UUID player);

    CompletableFuture<SocialResult> kick(UUID actor, UUID target);

    CompletableFuture<SocialResult> promote(UUID actor, UUID target);

    CompletableFuture<SocialResult> demote(UUID actor, UUID target);

    CompletableFuture<SocialResult> transfer(UUID actor, UUID target);

    CompletableFuture<SocialResult> disband(UUID actor);

    CompletableFuture<SocialResult> warp(UUID actor);

    CompletableFuture<SocialResult> changeSettings(UUID actor, PartySettings settings);
}
