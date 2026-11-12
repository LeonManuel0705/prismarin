package net.prismarin.api.cosmetics;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record CosmeticSlot(String id) {

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    public static final CosmeticSlot TRAIL = new CosmeticSlot("trail");
    public static final CosmeticSlot AURA = new CosmeticSlot("aura");
    public static final CosmeticSlot HAT = new CosmeticSlot("hat");
    public static final CosmeticSlot PROJECTILE_TRAIL = new CosmeticSlot("projectile-trail");
    public static final CosmeticSlot KILL_EFFECT = new CosmeticSlot("kill-effect");
    public static final CosmeticSlot WIN_EFFECT = new CosmeticSlot("win-effect");

    public static final List<CosmeticSlot> BUILT_IN = List.of(TRAIL, AURA, HAT, PROJECTILE_TRAIL, KILL_EFFECT,
            WIN_EFFECT);

    public CosmeticSlot {
        id = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("'" + id + "' is no cosmetic slot, use " + ID.pattern());
        }
    }

    public static CosmeticSlot of(String id) {
        return new CosmeticSlot(id);
    }

    public static Optional<CosmeticSlot> parse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.strip().toLowerCase(Locale.ROOT);
        return ID.matcher(key).matches() ? Optional.of(new CosmeticSlot(key)) : Optional.empty();
    }

    public boolean builtIn() {
        return BUILT_IN.contains(this);
    }

    public boolean continuous() {
        return equals(TRAIL) || equals(AURA);
    }

    public boolean hat() {
        return equals(HAT);
    }

    public boolean effect() {
        return !continuous() && !hat();
    }
}
