package net.prismarin.shared.joinme;

import java.util.Objects;
import java.util.Optional;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeResult;

public record JoinMeTerms(JoinMeResult.Status status, long waitMillis, String currency, long cost,
                          long lifetimeMillis, int maxJoins, JoinMeAudience audience, boolean sameServer) {

    public JoinMeTerms {
        Objects.requireNonNull(status, "status");
        waitMillis = Math.max(0L, waitMillis);
        currency = currency == null ? "" : currency;
        cost = Math.max(0L, cost);
        lifetimeMillis = Math.max(0L, lifetimeMillis);
        maxJoins = Math.max(0, maxJoins);
        audience = audience == null ? JoinMeAudience.NETWORK : audience;
    }

    public static JoinMeTerms refused(JoinMeResult.Status status) {
        return new JoinMeTerms(status, 0L, "", 0L, 0L, 0, null, true);
    }

    public static JoinMeTerms cooldown(long waitMillis) {
        return new JoinMeTerms(JoinMeResult.Status.COOLDOWN, waitMillis, "", 0L, 0L, 0, null, true);
    }

    public boolean allowed() {
        return status.success();
    }

    public boolean paid() {
        return cost > 0L && !currency.isEmpty();
    }

    public JoinMeResult refusal() {
        return new JoinMeResult(status, Optional.empty(), waitMillis);
    }
}
