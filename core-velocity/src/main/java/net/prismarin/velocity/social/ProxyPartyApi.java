package net.prismarin.velocity.social;

import com.velocitypowered.api.proxy.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartySettings;
import net.prismarin.api.social.SocialResult;

public final class ProxyPartyApi implements net.prismarin.api.social.PartyService {

    private final SocialContext context;
    private final PartyService party;

    public ProxyPartyApi(SocialContext context, PartyService party) {
        this.context = context;
        this.party = party;
    }

    @Override
    public Optional<Party> partyOf(UUID player) {
        return party.parties().partyOf(player).map(party.feed()::view);
    }

    @Override
    public Optional<Party> party(UUID partyId) {
        return party.parties().party(partyId).map(party.feed()::view);
    }

    @Override
    public CompletableFuture<Optional<Party>> lookup(UUID player) {
        return context.supply(() -> partyOf(player));
    }

    @Override
    public CompletableFuture<SocialResult> invite(UUID actor, UUID target) {
        return as(actor, player -> party.invite(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> join(UUID player, UUID member) {
        return as(player, online -> party.join(online, member));
    }

    @Override
    public CompletableFuture<SocialResult> leave(UUID player) {
        return as(player, party::leave);
    }

    @Override
    public CompletableFuture<SocialResult> kick(UUID actor, UUID target) {
        return as(actor, player -> party.kick(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> promote(UUID actor, UUID target) {
        return as(actor, player -> party.promote(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> demote(UUID actor, UUID target) {
        return as(actor, player -> party.demote(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> transfer(UUID actor, UUID target) {
        return as(actor, player -> party.transfer(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> disband(UUID actor) {
        return as(actor, party::disband);
    }

    @Override
    public CompletableFuture<SocialResult> warp(UUID actor) {
        return as(actor, party::warp);
    }

    @Override
    public CompletableFuture<SocialResult> changeSettings(UUID actor, PartySettings settings) {
        if (settings == null) {
            return CompletableFuture.completedFuture(SocialResult.FAILED);
        }
        return as(actor, player -> party.configure(player, settings));
    }

    private CompletableFuture<SocialResult> as(UUID actor, Function<Player, CompletableFuture<SocialResult>> action) {
        if (!context.partyEnabled()) {
            return CompletableFuture.completedFuture(SocialResult.DISABLED);
        }
        return context.proxy().getPlayer(actor).map(action)
                .orElseGet(() -> CompletableFuture.completedFuture(SocialResult.UNAVAILABLE));
    }
}
