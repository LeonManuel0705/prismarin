package net.prismarin.velocity.admin;

import java.util.Locale;
import java.util.Optional;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentType;

public enum PunishmentKind {
    BAN(PunishmentType.BAN),
    TEMPBAN(PunishmentType.BAN),
    IPBAN(PunishmentType.BAN),
    MUTE(PunishmentType.MUTE),
    TEMPMUTE(PunishmentType.MUTE),
    KICK(PunishmentType.KICK),
    WARN(PunishmentType.WARN);

    private final PunishmentType type;

    PunishmentKind(PunishmentType type) {
        this.type = type;
    }

    public PunishmentType type() {
        return type;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PunishmentKind of(Punishment punishment) {
        return of(punishment.type(), punishment.permanent(), punishment.addressBound());
    }

    public static PunishmentKind of(PunishmentType type, boolean permanent, boolean address) {
        return switch (type) {
            case BAN -> address ? IPBAN : permanent ? BAN : TEMPBAN;
            case MUTE -> permanent ? MUTE : TEMPMUTE;
            case KICK -> KICK;
            case WARN -> WARN;
        };
    }

    public static Optional<PunishmentKind> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String key = text.trim().toLowerCase(Locale.ROOT);
        for (PunishmentKind kind : values()) {
            if (kind.key().equals(key)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
