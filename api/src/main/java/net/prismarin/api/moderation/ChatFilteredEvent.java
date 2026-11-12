package net.prismarin.api.moderation;

import java.util.Objects;
import net.prismarin.api.event.PrismarinEvent;

public final class ChatFilteredEvent implements PrismarinEvent {

    private final ChatMessage message;
    private final ChatFilterResult result;
    private final String server;
    private volatile boolean cancelled;

    public ChatFilteredEvent(ChatMessage message, ChatFilterResult result, String server) {
        this.message = Objects.requireNonNull(message, "message");
        this.result = Objects.requireNonNull(result, "result");
        this.server = server == null ? "" : server;
    }

    public ChatMessage message() {
        return message;
    }

    public ChatFilterResult result() {
        return result;
    }

    public String server() {
        return server;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
