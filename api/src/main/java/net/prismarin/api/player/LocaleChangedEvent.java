package net.prismarin.api.player;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record LocaleChangedEvent(UUID player, Locale previous, Locale current) implements PrismarinEvent {

    public LocaleChangedEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
    }
}
