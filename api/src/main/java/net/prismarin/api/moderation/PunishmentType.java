package net.prismarin.api.moderation;

public enum PunishmentType {
    BAN,
    MUTE,
    KICK,
    WARN;

    public boolean lasting() {
        return this == BAN || this == MUTE;
    }
}
