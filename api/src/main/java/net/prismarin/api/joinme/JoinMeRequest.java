package net.prismarin.api.joinme;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record JoinMeRequest(UUID creator, Duration lifetime, int maxJoins, JoinMeAudience audience,
                            Map<String, String> placeholders, boolean ignoreCooldown, boolean free) {

    public static final int CONFIGURED = -1;

    public JoinMeRequest {
        Objects.requireNonNull(creator, "creator");
        lifetime = lifetime == null || lifetime.isNegative() || lifetime.isZero() ? null : lifetime;
        maxJoins = Math.max(CONFIGURED, maxJoins);
        Map<String, String> copied = new LinkedHashMap<>();
        if (placeholders != null) {
            placeholders.forEach((key, value) -> {
                if (key != null && value != null) {
                    copied.put(key, value);
                }
            });
        }
        placeholders = Collections.unmodifiableMap(copied);
    }

    public static JoinMeRequest of(UUID creator) {
        return new JoinMeRequest(creator, null, CONFIGURED, null, Map.of(), false, false);
    }

    public Optional<Duration> customLifetime() {
        return Optional.ofNullable(lifetime);
    }

    public Optional<JoinMeAudience> customAudience() {
        return Optional.ofNullable(audience);
    }

    public boolean customMaxJoins() {
        return maxJoins != CONFIGURED;
    }

    public JoinMeRequest withLifetime(Duration value) {
        return new JoinMeRequest(creator, value, maxJoins, audience, placeholders, ignoreCooldown, free);
    }

    public JoinMeRequest withMaxJoins(int value) {
        return new JoinMeRequest(creator, lifetime, Math.max(0, value), audience, placeholders, ignoreCooldown, free);
    }

    public JoinMeRequest withAudience(JoinMeAudience value) {
        return new JoinMeRequest(creator, lifetime, maxJoins, value, placeholders, ignoreCooldown, free);
    }

    public JoinMeRequest withPlaceholder(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(placeholders);
        next.put(key, value);
        return new JoinMeRequest(creator, lifetime, maxJoins, audience, next, ignoreCooldown, free);
    }

    public JoinMeRequest withPlaceholders(Map<String, String> values) {
        Map<String, String> next = new LinkedHashMap<>(placeholders);
        next.putAll(values);
        return new JoinMeRequest(creator, lifetime, maxJoins, audience, next, ignoreCooldown, free);
    }

    public JoinMeRequest ignoringCooldown() {
        return new JoinMeRequest(creator, lifetime, maxJoins, audience, placeholders, true, free);
    }

    public JoinMeRequest withoutCost() {
        return new JoinMeRequest(creator, lifetime, maxJoins, audience, placeholders, ignoreCooldown, true);
    }
}
