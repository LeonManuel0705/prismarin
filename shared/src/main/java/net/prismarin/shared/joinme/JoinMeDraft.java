package net.prismarin.shared.joinme;

import java.util.Map;
import java.util.Objects;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeSpot;

public record JoinMeDraft(JoinMeSpot spot, String shownName, String handler, boolean worldFallback,
                          Map<String, String> placeholders, long lifetimeMillis, int maxJoins,
                          JoinMeAudience audience, boolean ignoreCooldown, boolean free, JoinMeLook look) {

    public JoinMeDraft {
        Objects.requireNonNull(spot, "spot");
        shownName = shownName == null ? "" : shownName;
        handler = handler == null ? "" : handler;
        placeholders = JoinMeCodec.cleanPlaceholders(placeholders);
        lifetimeMillis = Math.max(0L, lifetimeMillis);
        maxJoins = Math.max(JoinMeRequest.CONFIGURED, maxJoins);
        look = look == null ? JoinMeLook.NONE : look;
    }

    public static JoinMeDraft of(JoinMeSpot spot, JoinMeRequest request) {
        return new JoinMeDraft(spot, "", "", false, request.placeholders(),
                request.customLifetime().map(duration -> duration.toMillis()).orElse(0L), request.maxJoins(),
                request.audience(), request.ignoreCooldown(), request.free(), JoinMeLook.NONE);
    }

    public JoinMeDraft withSpot(JoinMeSpot value) {
        return new JoinMeDraft(value, shownName, handler, worldFallback, placeholders, lifetimeMillis, maxJoins,
                audience, ignoreCooldown, free, look);
    }

    public JoinMeDraft withShownName(String value) {
        return new JoinMeDraft(spot, value, handler, worldFallback, placeholders, lifetimeMillis, maxJoins, audience,
                ignoreCooldown, free, look);
    }

    public JoinMeDraft withHandler(String value, boolean fallback) {
        return new JoinMeDraft(spot, shownName, value, fallback, placeholders, lifetimeMillis, maxJoins, audience,
                ignoreCooldown, free, look);
    }

    public JoinMeDraft withPlaceholders(Map<String, String> value) {
        return new JoinMeDraft(spot, shownName, handler, worldFallback, value, lifetimeMillis, maxJoins, audience,
                ignoreCooldown, free, look);
    }

    public JoinMeDraft withTerms(long lifetime, int joins, JoinMeAudience shownTo) {
        return new JoinMeDraft(spot, shownName, handler, worldFallback, placeholders, lifetime, joins, shownTo,
                ignoreCooldown, free, look);
    }

    public JoinMeDraft withLook(JoinMeLook value) {
        return new JoinMeDraft(spot, shownName, handler, worldFallback, placeholders, lifetimeMillis, maxJoins,
                audience, ignoreCooldown, free, value);
    }

    public boolean handled() {
        return !handler.isEmpty();
    }
}
