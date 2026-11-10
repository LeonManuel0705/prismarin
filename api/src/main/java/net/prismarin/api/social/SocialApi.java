package net.prismarin.api.social;

import java.util.Optional;

public final class SocialApi {

    private static volatile FriendService friends;
    private static volatile PartyService parties;

    private SocialApi() {
        throw new AssertionError();
    }

    public static Optional<FriendService> friends() {
        return Optional.ofNullable(friends);
    }

    public static Optional<PartyService> parties() {
        return Optional.ofNullable(parties);
    }

    public static void register(FriendService friendService, PartyService partyService) {
        friends = friendService;
        parties = partyService;
    }

    public static void unregister() {
        friends = null;
        parties = null;
    }
}
