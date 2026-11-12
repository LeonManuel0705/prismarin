package net.prismarin.api.social;

import java.util.Optional;

public final class SocialApi {

    private static volatile FriendService friends;
    private static volatile PartyService parties;
    private static volatile ClanService clans;

    private SocialApi() {
        throw new AssertionError();
    }

    public static Optional<FriendService> friends() {
        return Optional.ofNullable(friends);
    }

    public static Optional<PartyService> parties() {
        return Optional.ofNullable(parties);
    }

    public static Optional<ClanService> clans() {
        return Optional.ofNullable(clans);
    }

    public static void register(FriendService friendService, PartyService partyService) {
        register(friendService, partyService, null);
    }

    public static void register(FriendService friendService, PartyService partyService, ClanService clanService) {
        friends = friendService;
        parties = partyService;
        clans = clanService;
    }

    public static void unregister() {
        friends = null;
        parties = null;
        clans = null;
    }
}
