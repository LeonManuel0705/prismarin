package net.prismarin.api.moderation;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record ChatFilterEscalatedEvent(UUID player, String name, String rule, int violations, String template,
                                       boolean reported) implements PrismarinEvent {

    public ChatFilterEscalatedEvent {
        Objects.requireNonNull(player, "player");
        name = name == null ? "" : name;
        Objects.requireNonNull(rule, "rule");
        template = template == null ? "" : template;
    }
}
