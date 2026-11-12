package net.prismarin.velocity.social;

import com.velocitypowered.api.proxy.Player;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanPage;
import net.prismarin.api.social.ClanResult;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.SocialResult;

public final class ProxyClanApi implements net.prismarin.api.social.ClanService {

    private final SocialContext context;
    private final ClanService clans;

    public ProxyClanApi(SocialContext context, ClanService clans) {
        this.context = context;
        this.clans = clans;
    }

    @Override
    public Optional<Clan> clanOf(UUID player) {
        return clans.clans().clanOf(player);
    }

    @Override
    public Optional<Clan> clan(UUID clanId) {
        return clans.clans().clan(clanId);
    }

    @Override
    public CompletableFuture<Optional<Clan>> lookup(UUID player) {
        return context.supply(() -> {
            clans.ready();
            return clans.clans().clanOf(player);
        });
    }

    @Override
    public CompletableFuture<Optional<Clan>> byId(UUID clanId) {
        return context.supply(() -> {
            clans.ready();
            return clans.clans().clan(clanId);
        });
    }

    @Override
    public CompletableFuture<Optional<Clan>> find(String nameOrTag) {
        return context.supply(() -> {
            clans.ready();
            return clans.clans().find(nameOrTag);
        });
    }

    @Override
    public CompletableFuture<ClanPage> list(int page, int pageSize) {
        return context.supply(() -> {
            clans.ready();
            return ClanPage.of(clans.clans().all(), page, pageSize);
        });
    }

    @Override
    public ClanRights rights() {
        return clans.settings().rights();
    }

    @Override
    public Component formatTag(Clan clan) {
        return clans.tag(clan);
    }

    @Override
    public CompletableFuture<SocialResult> create(UUID founder, String name, String tag) {
        return as(founder, player -> clans.create(player, name, tag));
    }

    @Override
    public CompletableFuture<SocialResult> invite(UUID actor, UUID target) {
        return as(actor, player -> clans.invite(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> join(UUID player, UUID clanId) {
        return as(player, online -> clans.join(online, clanId));
    }

    @Override
    public CompletableFuture<SocialResult> leave(UUID player) {
        return as(player, clans::leave);
    }

    @Override
    public CompletableFuture<SocialResult> kick(UUID actor, UUID target) {
        return as(actor, player -> clans.kick(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> promote(UUID actor, UUID target) {
        return as(actor, player -> clans.promote(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> demote(UUID actor, UUID target) {
        return as(actor, player -> clans.demote(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> transfer(UUID actor, UUID target) {
        return as(actor, player -> clans.transfer(player, target));
    }

    @Override
    public CompletableFuture<SocialResult> disband(UUID actor) {
        return as(actor, clans::disbandConfirmed);
    }

    @Override
    public CompletableFuture<SocialResult> changeColor(UUID actor, String color) {
        return as(actor, player -> clans.color(player, color));
    }

    @Override
    public CompletableFuture<SocialResult> changeAccess(UUID actor, ClanAccess access) {
        if (access == null) {
            return CompletableFuture.completedFuture(SocialResult.FAILED);
        }
        return as(actor, player -> clans.access(player, access));
    }

    @Override
    public CompletableFuture<SocialResult> chat(UUID sender, String message) {
        if (message == null || message.isBlank()) {
            return CompletableFuture.completedFuture(SocialResult.FAILED);
        }
        return as(sender, player -> clans.chat(player, message));
    }

    @Override
    public CompletableFuture<ClanResult> createClan(UUID leader, String name, String tag) {
        return managed(() -> clans.createClan(leader, name, tag));
    }

    @Override
    public CompletableFuture<ClanResult> renameClan(UUID clanId, String name, String tag) {
        return managed(() -> clans.renameClan(clanId, name, tag));
    }

    @Override
    public CompletableFuture<ClanResult> disbandClan(UUID clanId) {
        return managed(() -> clans.disbandClan(clanId));
    }

    @Override
    public CompletableFuture<ClanResult> addMember(UUID clanId, UUID player, ClanRole role) {
        return managed(() -> clans.addMember(clanId, player, role));
    }

    @Override
    public CompletableFuture<ClanResult> removeMember(UUID clanId, UUID player) {
        return managed(() -> clans.removeMember(clanId, player));
    }

    @Override
    public CompletableFuture<ClanResult> setRole(UUID clanId, UUID player, ClanRole role) {
        return managed(() -> clans.setRole(clanId, player, role));
    }

    @Override
    public CompletableFuture<ClanResult> setColor(UUID clanId, String color) {
        return managed(() -> clans.setColor(clanId, color));
    }

    @Override
    public CompletableFuture<ClanResult> setAccess(UUID clanId, ClanAccess access) {
        return managed(() -> clans.setAccess(clanId, access));
    }

    @Override
    public CompletableFuture<ClanResult> setData(UUID clanId, String key, String value) {
        return managed(() -> clans.setData(clanId, key, value));
    }

    @Override
    public CompletableFuture<SocialResult> broadcast(UUID clanId, String message) {
        if (!context.clansEnabled()) {
            return CompletableFuture.completedFuture(SocialResult.DISABLED);
        }
        return clans.broadcast(clanId, message);
    }

    private CompletableFuture<ClanResult> managed(Supplier<CompletableFuture<ClanResult>> call) {
        if (!context.clansEnabled()) {
            return CompletableFuture.completedFuture(ClanResult.of(SocialResult.DISABLED));
        }
        return call.get();
    }

    private CompletableFuture<SocialResult> as(UUID actor, Function<Player, CompletableFuture<SocialResult>> action) {
        if (!context.clansEnabled()) {
            return CompletableFuture.completedFuture(SocialResult.DISABLED);
        }
        return context.proxy().getPlayer(actor).map(action)
                .orElseGet(() -> CompletableFuture.completedFuture(SocialResult.UNAVAILABLE));
    }
}
