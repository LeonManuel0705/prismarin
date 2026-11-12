package net.prismarin.shared.joinme;

public enum JoinMeRoute {
    CONNECT,
    ARRIVE,
    ALREADY_THERE,
    OTHER_SERVER;

    public static JoinMeRoute plan(boolean handled, boolean sameServer, boolean worldFallback, boolean onServer,
                                   boolean inWorld) {
        if (!onServer) {
            return handled || sameServer ? CONNECT : OTHER_SERVER;
        }
        if (handled) {
            return ARRIVE;
        }
        if (worldFallback && !inWorld) {
            return ARRIVE;
        }
        return ALREADY_THERE;
    }

    public static boolean joinable(boolean handled, boolean sameServer, boolean worldFallback) {
        return handled || sameServer || worldFallback;
    }

    public boolean moves() {
        return this == CONNECT || this == ARRIVE;
    }
}
