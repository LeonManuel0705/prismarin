package net.prismarin.api.moderation;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ChatMessage(UUID sender, String senderName, String channel, String text) {

    public static final UUID NOBODY = new UUID(0L, 0L);
    public static final String PUBLIC = "public";
    public static final String PRIVATE = "private";
    public static final String PARTY = "party";
    public static final String TEXT = "text";

    public ChatMessage {
        sender = sender == null ? NOBODY : sender;
        senderName = senderName == null ? "" : senderName;
        channel = channel == null || channel.isBlank() ? PUBLIC : channel.trim().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(text, "text");
    }

    public static ChatMessage of(UUID sender, String senderName, String text) {
        return new ChatMessage(sender, senderName, PUBLIC, text);
    }

    public static ChatMessage text(String text) {
        return new ChatMessage(NOBODY, "", TEXT, text);
    }

    public boolean anonymous() {
        return NOBODY.equals(sender);
    }

    public ChatMessage withText(String replaced) {
        return new ChatMessage(sender, senderName, channel, replaced);
    }
}
