package net.prismarin.velocity.social;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class FriendRules {

    public enum RequestOutcome {
        SELF,
        ALREADY_FRIENDS,
        ALREADY_SENT,
        ACCEPTED,
        NOT_ACCEPTING,
        SENDER_FULL,
        TARGET_FULL,
        SENT
    }

    public enum AcceptOutcome {
        NO_REQUEST,
        ALREADY_FRIENDS,
        SELF_FULL,
        OTHER_FULL,
        ACCEPTED
    }

    private FriendRules() {
        throw new AssertionError();
    }

    public static boolean expired(long at, long now, long expiryMillis) {
        return expiryMillis > 0 && now - at >= expiryMillis;
    }

    public static boolean pending(Map<UUID, Long> requests, UUID other, long now,
                                  long expiryMillis) {
        Long at = requests.get(other);
        return at != null && !expired(at, now, expiryMillis);
    }

    public static RequestOutcome request(SocialProfile sender, SocialProfile target, int senderLimit, int targetLimit,
                                         String targetPrivacy, long now, long expiryMillis) {
        if (sender.uuid().equals(target.uuid())) {
            return RequestOutcome.SELF;
        }
        if (sender.friendsWith(target.uuid())) {
            return RequestOutcome.ALREADY_FRIENDS;
        }
        if (pending(sender.incoming(), target.uuid(), now, expiryMillis)) {
            return limits(sender, target, senderLimit, targetLimit, RequestOutcome.ACCEPTED);
        }
        if (pending(sender.outgoing(), target.uuid(), now, expiryMillis)) {
            return RequestOutcome.ALREADY_SENT;
        }
        if (SocialSetting.NOBODY.equals(targetPrivacy)
                || SocialSetting.FRIENDS_OF_FRIENDS.equals(targetPrivacy) && !sharesFriend(sender, target)) {
            return RequestOutcome.NOT_ACCEPTING;
        }
        return limits(sender, target, senderLimit, targetLimit, RequestOutcome.SENT);
    }

    public static AcceptOutcome accept(SocialProfile accepter, SocialProfile requester, int accepterLimit,
                                       int requesterLimit, long now, long expiryMillis) {
        if (accepter.friendsWith(requester.uuid())) {
            return AcceptOutcome.ALREADY_FRIENDS;
        }
        if (!pending(accepter.incoming(), requester.uuid(), now, expiryMillis)) {
            return AcceptOutcome.NO_REQUEST;
        }
        if (accepter.friends().size() >= accepterLimit) {
            return AcceptOutcome.SELF_FULL;
        }
        if (requester.friends().size() >= requesterLimit) {
            return AcceptOutcome.OTHER_FULL;
        }
        return AcceptOutcome.ACCEPTED;
    }

    public static boolean sharesFriend(SocialProfile first, SocialProfile second) {
        for (UUID friend : first.friendIds()) {
            if (second.friendsWith(friend)) {
                return true;
            }
        }
        return false;
    }

    public static int limit(int base, Map<String, Integer> extra, Predicate<String> has) {
        int limit = base;
        for (Map.Entry<String, Integer> entry : extra.entrySet()) {
            if (entry.getValue() > limit && has.test(entry.getKey())) {
                limit = entry.getValue();
            }
        }
        return limit;
    }

    private static RequestOutcome limits(SocialProfile sender, SocialProfile target, int senderLimit,
                                         int targetLimit, RequestOutcome success) {
        if (sender.friends().size() >= senderLimit) {
            return RequestOutcome.SENDER_FULL;
        }
        if (target.friends().size() >= targetLimit) {
            return RequestOutcome.TARGET_FULL;
        }
        return success;
    }
}
