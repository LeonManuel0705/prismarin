package net.prismarin.api.moderation;

import java.util.Objects;
import java.util.UUID;

public record AltAccount(UUID uuid, String name, long firstSeen, long lastSeen, int sharedAddresses, boolean banned,
                         boolean muted) {

    public AltAccount {
        Objects.requireNonNull(uuid, "uuid");
        name = name == null ? "" : name;
        if (sharedAddresses < 1) {
            throw new IllegalArgumentException("an alt shares at least one address");
        }
    }

    public boolean punished() {
        return banned || muted;
    }
}
