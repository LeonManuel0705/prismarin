package net.prismarin.velocity.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;

public enum PartyOption {

    INVITE("invite", List.of(PartyOption.LEADER, PartyOption.MODERATORS, PartyOption.MEMBERS)),
    ACCESS("access", List.of(PartyOption.INVITE_ONLY, PartyOption.FRIENDS, PartyOption.OPEN)),
    FOLLOW("follow", List.of(SocialSetting.ON, SocialSetting.OFF)),
    CHAT("chat", List.of(PartyOption.MEMBERS, PartyOption.MODERATORS, PartyOption.LEADER));

    public static final String LEADER = "leader";
    public static final String MODERATORS = "moderators";
    public static final String MEMBERS = "members";
    public static final String INVITE_ONLY = "invite-only";
    public static final String FRIENDS = "friends";
    public static final String OPEN = "open";

    private final String id;
    private final List<String> options;

    PartyOption(String id, List<String> options) {
        this.id = id;
        this.options = options;
    }

    public String id() {
        return id;
    }

    public List<String> options() {
        return options;
    }

    public Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        String mapped = switch (this) {
            case INVITE, CHAT -> switch (normalized) {
                case "leader", "lead", "owner", "leiter" -> LEADER;
                case "moderators", "moderator", "mods", "mod", "moderatoren" -> MODERATORS;
                case "members", "member", "all", "everyone", "alle", "mitglieder", "jeder" -> MEMBERS;
                default -> normalized;
            };
            case ACCESS -> switch (normalized) {
                case "invite-only", "invite", "invites", "invited", "closed", "private", "einladung", "geschlossen",
                     "privat" -> INVITE_ONLY;
                case "friends", "friend", "freunde" -> FRIENDS;
                case "open", "public", "everyone", "all", "offen", "alle" -> OPEN;
                default -> normalized;
            };
            case FOLLOW -> switch (normalized) {
                case "on", "true", "yes", "enabled", "an", "ja" -> SocialSetting.ON;
                case "off", "false", "no", "disabled", "aus", "nein" -> SocialSetting.OFF;
                default -> normalized;
            };
        };
        return options.contains(mapped) ? Optional.of(mapped) : Optional.empty();
    }

    public String value(PartySettings settings) {
        return switch (this) {
            case INVITE -> roleValue(settings.invite());
            case ACCESS -> settings.access().id();
            case FOLLOW -> settings.follow() ? SocialSetting.ON : SocialSetting.OFF;
            case CHAT -> roleValue(settings.chat());
        };
    }

    public PartySettings apply(PartySettings settings, String value) {
        String normalized = normalize(value).orElseThrow(() -> new IllegalArgumentException(
                "'" + value + "' is not one of " + options));
        return switch (this) {
            case INVITE -> settings.withInvite(role(normalized));
            case ACCESS -> settings.withAccess(PartyAccess.byId(normalized).orElse(PartyAccess.INVITE_ONLY));
            case FOLLOW -> settings.withFollow(SocialSetting.ON.equals(normalized));
            case CHAT -> settings.withChat(role(normalized));
        };
    }

    public String next(String current) {
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    public static Optional<PartyOption> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        for (PartyOption option : values()) {
            if (option.id.equals(normalized)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    public static List<PartyOption> available(boolean followEnabled) {
        List<PartyOption> available = new ArrayList<>();
        for (PartyOption option : values()) {
            if (option != FOLLOW || followEnabled) {
                available.add(option);
            }
        }
        return List.copyOf(available);
    }

    private static String roleValue(PartyRole role) {
        return switch (role) {
            case LEADER -> LEADER;
            case MODERATOR -> MODERATORS;
            case MEMBER -> MEMBERS;
        };
    }

    private static PartyRole role(String value) {
        return switch (value) {
            case LEADER -> PartyRole.LEADER;
            case MODERATORS -> PartyRole.MODERATOR;
            default -> PartyRole.MEMBER;
        };
    }
}
