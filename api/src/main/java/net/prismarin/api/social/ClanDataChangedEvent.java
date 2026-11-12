package net.prismarin.api.social;

import java.util.Optional;
import net.prismarin.api.event.PrismarinEvent;

public record ClanDataChangedEvent(Clan clan, String key, String previous, String value) implements PrismarinEvent {

    public Optional<String> previousValue() {
        return Optional.ofNullable(previous);
    }

    public Optional<String> newValue() {
        return Optional.ofNullable(value);
    }
}
