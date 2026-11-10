package net.prismarin.api.social;

import java.util.Objects;

public record PartySettings(PartyRole invite, PartyAccess access, boolean follow, PartyRole chat) {

    public static final PartySettings DEFAULT = new PartySettings(PartyRole.MODERATOR, PartyAccess.INVITE_ONLY,
            true, PartyRole.MEMBER);

    public PartySettings {
        Objects.requireNonNull(invite, "invite");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(chat, "chat");
    }

    public PartySettings withInvite(PartyRole value) {
        return new PartySettings(value, access, follow, chat);
    }

    public PartySettings withAccess(PartyAccess value) {
        return new PartySettings(invite, value, follow, chat);
    }

    public PartySettings withFollow(boolean value) {
        return new PartySettings(invite, access, value, chat);
    }

    public PartySettings withChat(PartyRole value) {
        return new PartySettings(invite, access, follow, value);
    }
}
