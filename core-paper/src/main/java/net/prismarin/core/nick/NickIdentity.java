package net.prismarin.core.nick;

import java.util.Locale;
import java.util.Objects;

public record NickIdentity(String name, String skin, Kind kind) {

    public static final String OWN_SKIN = "#own";
    public static final String DEFAULT_SKIN = "#default";

    public NickIdentity {
        Objects.requireNonNull(name, "name");
        skin = skin == null || skin.isBlank() ? OWN_SKIN : skin.toLowerCase(Locale.ROOT);
        kind = kind == null ? Kind.MANUAL : kind;
    }

    public NickIdentity(String name, String skin) {
        this(name, skin, Kind.MANUAL);
    }

    public boolean ownSkin() {
        return OWN_SKIN.equals(skin);
    }

    public boolean defaultSkin() {
        return DEFAULT_SKIN.equals(skin);
    }

    public boolean poolSkin() {
        return !ownSkin() && !defaultSkin();
    }

    public boolean roundScoped() {
        return kind == Kind.ROUND;
    }

    public boolean sameName(String other) {
        return other != null && name.equalsIgnoreCase(other);
    }

    public enum Kind {
        MANUAL,
        AUTO,
        ROUND
    }
}
