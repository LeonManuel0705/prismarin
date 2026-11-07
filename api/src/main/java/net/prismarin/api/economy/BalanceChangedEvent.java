package net.prismarin.api.economy;

import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record BalanceChangedEvent(UUID uuid, Currency currency, Type type, long amount, long newBalance)
        implements PrismarinEvent {

    public enum Type {
        DEPOSIT,
        WITHDRAW,
        SET
    }
}
