package net.prismarin.api.social;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;

public interface ClanService {

    Optional<Clan> clanOf(UUID player);

    Optional<Clan> clan(UUID clanId);

    CompletableFuture<Optional<Clan>> lookup(UUID player);

    CompletableFuture<Optional<Clan>> byId(UUID clanId);

    CompletableFuture<Optional<Clan>> find(String nameOrTag);

    CompletableFuture<ClanPage> list(int page, int pageSize);

    ClanRights rights();

    Component formatTag(Clan clan);

    default Optional<Component> formattedTag(UUID player) {
        return clanOf(player).map(this::formatTag);
    }

    default Optional<ClanRole> roleOf(UUID player) {
        return clanOf(player).flatMap(clan -> clan.role(player));
    }

    default boolean together(UUID first, UUID second) {
        return clanOf(first).map(clan -> clan.contains(second)).orElse(false);
    }

    default boolean can(UUID actor, ClanAction action, UUID target) {
        return clanOf(actor).map(clan -> clan.can(actor, action, target, rights())).orElse(false);
    }

    CompletableFuture<SocialResult> create(UUID founder, String name, String tag);

    CompletableFuture<SocialResult> invite(UUID actor, UUID target);

    CompletableFuture<SocialResult> join(UUID player, UUID clanId);

    CompletableFuture<SocialResult> leave(UUID player);

    CompletableFuture<SocialResult> kick(UUID actor, UUID target);

    CompletableFuture<SocialResult> promote(UUID actor, UUID target);

    CompletableFuture<SocialResult> demote(UUID actor, UUID target);

    CompletableFuture<SocialResult> transfer(UUID actor, UUID target);

    CompletableFuture<SocialResult> disband(UUID actor);

    CompletableFuture<SocialResult> changeColor(UUID actor, String color);

    CompletableFuture<SocialResult> changeAccess(UUID actor, ClanAccess access);

    CompletableFuture<SocialResult> chat(UUID sender, String message);

    CompletableFuture<ClanResult> createClan(UUID leader, String name, String tag);

    CompletableFuture<ClanResult> renameClan(UUID clanId, String name, String tag);

    CompletableFuture<ClanResult> disbandClan(UUID clanId);

    CompletableFuture<ClanResult> addMember(UUID clanId, UUID player, ClanRole role);

    CompletableFuture<ClanResult> removeMember(UUID clanId, UUID player);

    CompletableFuture<ClanResult> setRole(UUID clanId, UUID player, ClanRole role);

    CompletableFuture<ClanResult> setColor(UUID clanId, String color);

    CompletableFuture<ClanResult> setAccess(UUID clanId, ClanAccess access);

    CompletableFuture<ClanResult> setData(UUID clanId, String key, String value);

    CompletableFuture<SocialResult> broadcast(UUID clanId, String message);
}
