package net.prismarin.api.joinme;

import java.util.Locale;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record JoinMeArrivedEvent(UUID player, JoinMe joinMe, Placement placement, String handler, boolean party)
        implements PrismarinEvent {

    public enum Placement {
        HANDLER,
        WORLD_SPAWN,
        NEAR_CREATOR,
        SERVER,
        REFUSED;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public JoinMeArrivedEvent {
        handler = handler == null ? "" : handler;
    }
}
