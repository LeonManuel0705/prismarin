package net.prismarin.velocity.social;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum SocialSetting {

    REQUESTS("requests", Feature.FRIENDS, List.of(SocialSetting.EVERYONE, SocialSetting.FRIENDS_OF_FRIENDS,
            SocialSetting.NOBODY)),
    NOTIFICATIONS("notifications", Feature.FRIENDS, List.of(SocialSetting.ON, SocialSetting.OFF)),
    JUMP("jump", Feature.FRIENDS, List.of(SocialSetting.ON, SocialSetting.OFF)),
    PARTY_INVITES("party-invites", Feature.PARTY, List.of(SocialSetting.EVERYONE, SocialSetting.FRIENDS,
            SocialSetting.NOBODY)),
    MESSAGES("messages", Feature.MESSAGES, List.of(SocialSetting.EVERYONE, SocialSetting.FRIENDS,
            SocialSetting.NOBODY));

    public static final String EVERYONE = "everyone";
    public static final String FRIENDS = "friends";
    public static final String FRIENDS_OF_FRIENDS = "friends-of-friends";
    public static final String NOBODY = "nobody";
    public static final String ON = "on";
    public static final String OFF = "off";

    public enum Feature {
        FRIENDS,
        PARTY,
        MESSAGES
    }

    private final String id;
    private final Feature feature;
    private final List<String> options;

    SocialSetting(String id, Feature feature, List<String> options) {
        this.id = id;
        this.feature = feature;
        this.options = options;
    }

    public String id() {
        return id;
    }

    public Feature feature() {
        return feature;
    }

    public List<String> options() {
        return options;
    }

    public String fallback() {
        return options.getFirst();
    }

    public Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        String mapped = switch (normalized) {
            case "true", "yes", "enabled", "an", "ja" -> ON;
            case "false", "no", "disabled", "aus", "nein" -> OFF;
            case "all", "anyone", "alle" -> EVERYONE;
            case "none", "niemand" -> NOBODY;
            case "fof", "friends-of-friend" -> FRIENDS_OF_FRIENDS;
            case "friend", "freunde" -> FRIENDS;
            default -> normalized;
        };
        return options.contains(mapped) ? Optional.of(mapped) : Optional.empty();
    }

    public String next(String current) {
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    public static Optional<SocialSetting> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        for (SocialSetting setting : values()) {
            if (setting.id.equals(normalized)) {
                return Optional.of(setting);
            }
        }
        return Optional.empty();
    }
}
