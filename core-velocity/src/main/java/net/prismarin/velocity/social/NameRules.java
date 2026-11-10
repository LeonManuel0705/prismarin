package net.prismarin.velocity.social;

import java.util.Optional;

public final class NameRules {

    private NameRules() {
        throw new AssertionError();
    }

    public static String visible(boolean self, boolean friend, boolean privileged, String realName,
                                 Optional<String> nick) {
        if (self || friend || privileged || nick.isEmpty() || nick.get().isBlank()) {
            return realName;
        }
        return nick.get();
    }

    public static boolean masked(boolean self, boolean friend, boolean privileged, Optional<String> nick) {
        return !self && !friend && !privileged && nick.isPresent() && !nick.get().isBlank();
    }
}
