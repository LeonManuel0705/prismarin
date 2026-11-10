package net.prismarin.api.nick;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record NickChangedEvent(UUID player, String realName, String previousName, String currentName, boolean nicked)
        implements PrismarinEvent {
}
