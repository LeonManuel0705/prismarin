package net.prismarin.api.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanService;
import net.prismarin.api.social.FriendService;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyService;
import net.prismarin.api.social.SocialApi;

public interface PlayerService {

    String REAL_NAME_PERMISSION = "prismarin.nick.realname";

    String SEE_VANISHED_PERMISSION = "prismarin.vanish.see";

    Optional<NetworkPlayer> online(UUID player);

    Collection<NetworkPlayer> onlinePlayers();

    default boolean isOnline(UUID player) {
        return online(player).isPresent();
    }

    CompletableFuture<Optional<NetworkPlayer>> find(UUID player);

    default CompletableFuture<List<NetworkPlayer>> findAll(Collection<UUID> players) {
        List<CompletableFuture<Optional<NetworkPlayer>>> lookups = players.stream().distinct().map(this::find).toList();
        return CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).thenApply(done -> {
            List<NetworkPlayer> found = new ArrayList<>();
            lookups.forEach(lookup -> lookup.join().ifPresent(found::add));
            return List.copyOf(found);
        });
    }

    CompletableFuture<Optional<NetworkPlayer>> findByRealName(String realName);

    Locale locale(UUID player);

    boolean revealsRealName(UUID viewer, UUID target);

    boolean canSee(UUID viewer, NetworkPlayer target);

    String displayName(UUID viewer, UUID target);

    default String displayName(UUID viewer, NetworkPlayer target) {
        return ViewerRules.displayName(target, revealsRealName(viewer, target.uuid()));
    }

    RankDisplay rank(UUID viewer, UUID target);

    default RankDisplay rank(UUID viewer, NetworkPlayer target) {
        return ViewerRules.rank(target, revealsRealName(viewer, target.uuid()));
    }

    Optional<NetworkPlayer> resolveOnline(UUID viewer, String typed);

    CompletableFuture<Optional<NetworkPlayer>> resolve(UUID viewer, String typed);

    List<String> suggestions(UUID viewer, String prefix);

    PlayerAttributes attributes();

    default RankSources ranks() {
        return RankSources.global();
    }

    default Optional<FriendService> friends() {
        return SocialApi.friends();
    }

    default Optional<PartyService> parties() {
        return SocialApi.parties();
    }

    default Optional<Party> party(UUID player) {
        return parties().flatMap(service -> service.partyOf(player));
    }

    default Optional<ClanService> clans() {
        return SocialApi.clans();
    }

    default Optional<Clan> clan(UUID player) {
        return clans().flatMap(service -> service.clanOf(player));
    }

    default CompletableFuture<Optional<Clan>> lookupClan(UUID player) {
        return clans().map(service -> service.lookup(player))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
    }

    default CompletableFuture<Boolean> areFriends(UUID first, UUID second) {
        return friends().map(service -> service.areFriends(first, second))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
    }

    default CompletableFuture<Set<UUID>> friendIds(UUID player) {
        return friends().map(service -> service.friendIds(player))
                .orElseGet(() -> CompletableFuture.completedFuture(Set.of()));
    }
}
