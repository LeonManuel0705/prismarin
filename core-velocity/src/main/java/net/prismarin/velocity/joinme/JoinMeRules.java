package net.prismarin.velocity.joinme;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.server.GameState;
import net.prismarin.shared.joinme.JoinMeDraft;
import net.prismarin.shared.joinme.JoinMeRoute;
import net.prismarin.shared.joinme.JoinMeTerms;

public final class JoinMeRules {

    public record Creator(boolean online, boolean vanished, String group, GameState state, long cooldownLeftMillis,
                          Predicate<String> permissions) {

        public Creator {
            group = group == null ? "" : group;
            state = state == null ? GameState.UNKNOWN : state;
            permissions = permissions == null ? permission -> false : permissions;
        }
    }

    public record Clicker(UUID player, boolean online, String server, String world, boolean permitted,
                          boolean inAudience) {

        public Clicker {
            Objects.requireNonNull(player, "player");
            server = server == null ? "" : server;
            world = world == null ? "" : world;
        }
    }

    public record Target(Boolean online, GameState state) {

        public static final Target UNKNOWN = new Target(null, GameState.UNKNOWN);

        public Target {
            state = state == null ? GameState.UNKNOWN : state;
        }
    }

    public record Verdict(JoinMeResult.Status status, JoinMeRoute route) {

        public static Verdict refused(JoinMeResult.Status status) {
            return new Verdict(status, null);
        }

        public boolean allowed() {
            return status.success();
        }
    }

    private final JoinMeSettings settings;

    public JoinMeRules(JoinMeSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public JoinMeSettings settings() {
        return settings;
    }

    public long cooldownMillis(Predicate<String> permissions) {
        return settings.cooldowns().best(permissions, Comparator.naturalOrder()).orElse(0L) * 1000L;
    }

    public JoinMeSettings.Cost cost(Predicate<String> permissions) {
        return settings.costs().best(permissions, Comparator.comparingLong(JoinMeSettings.Cost::amount))
                .orElse(new JoinMeSettings.Cost("", 0L));
    }

    public long lifetimeMillis(Predicate<String> permissions) {
        long tier = settings.lifetimes().best(permissions, Comparator.reverseOrder()).orElse(0L);
        return Math.max(settings.create().lifetimeSeconds(), tier) * 1000L;
    }

    public int maxJoins(Predicate<String> permissions) {
        int base = settings.create().maxJoins();
        if (base == 0) {
            return 0;
        }
        int best = base;
        for (Map.Entry<String, Integer> entry : settings.maxJoins().values().entrySet()) {
            boolean granted = Tiers.DEFAULT.equals(entry.getKey())
                    || permissions.test(settings.maxJoins().permission(entry.getKey()));
            if (!granted) {
                continue;
            }
            if (entry.getValue() == 0) {
                return 0;
            }
            best = Math.max(best, entry.getValue());
        }
        return best;
    }

    public JoinMeTerms check(Creator creator, JoinMeDraft draft) {
        if (!creator.online()) {
            return JoinMeTerms.refused(JoinMeResult.Status.OFFLINE);
        }
        if (creator.vanished()) {
            return JoinMeTerms.refused(JoinMeResult.Status.VANISHED);
        }
        if (!settings.create().groupAllowed(creator.group())) {
            return JoinMeTerms.refused(JoinMeResult.Status.GROUP_BLOCKED);
        }
        if (settings.create().joinableStateOnly() && !creator.state().joinable()) {
            return JoinMeTerms.refused(JoinMeResult.Status.GAME_STATE);
        }
        if (!draft.ignoreCooldown() && creator.cooldownLeftMillis() > 0L) {
            return JoinMeTerms.cooldown(creator.cooldownLeftMillis());
        }
        Predicate<String> permissions = creator.permissions();
        long lifetime = draft.lifetimeMillis() > 0L ? Math.min(draft.lifetimeMillis(), JoinMeSettings.MAX_SECONDS * 1000L)
                : lifetimeMillis(permissions);
        int joins = draft.maxJoins() >= 0 ? draft.maxJoins() : maxJoins(permissions);
        JoinMeAudience audience = draft.audience() != null ? draft.audience() : settings.audience().audience();
        JoinMeSettings.Cost cost = draft.free() ? new JoinMeSettings.Cost("", 0L) : cost(permissions);
        return new JoinMeTerms(JoinMeResult.Status.SUCCESS, 0L, cost.free() ? "" : cost.currency(),
                cost.free() ? 0L : cost.amount(), lifetime, joins, audience, settings.join().sameServer());
    }

    public Verdict join(JoinMe joinMe, boolean worldFallback, int reserved, Clicker clicker, Target target,
                        long now) {
        if (joinMe.creator().equals(clicker.player())) {
            return Verdict.refused(JoinMeResult.Status.OWN);
        }
        if (!clicker.inAudience()) {
            return Verdict.refused(JoinMeResult.Status.NOT_FOUND);
        }
        if (!clicker.online()) {
            return Verdict.refused(JoinMeResult.Status.OFFLINE);
        }
        if (joinMe.expired(now)) {
            return Verdict.refused(JoinMeResult.Status.EXPIRED);
        }
        if (joinMe.maxJoins() > 0 && joinMe.joins() + reserved >= joinMe.maxJoins()) {
            return Verdict.refused(JoinMeResult.Status.USED_UP);
        }
        if (!clicker.permitted()) {
            return Verdict.refused(JoinMeResult.Status.NO_PERMISSION);
        }
        boolean onServer = joinMe.server().equalsIgnoreCase(clicker.server());
        boolean inWorld = onServer && !joinMe.world().isEmpty() && joinMe.world().equalsIgnoreCase(clicker.world());
        JoinMeRoute route = JoinMeRoute.plan(joinMe.handled(), settings.join().sameServer(), worldFallback,
                onServer, inWorld);
        if (route == JoinMeRoute.ALREADY_THERE) {
            return Verdict.refused(JoinMeResult.Status.ALREADY_THERE);
        }
        if (route == JoinMeRoute.OTHER_SERVER) {
            return Verdict.refused(JoinMeResult.Status.OTHER_SERVER);
        }
        if (route == JoinMeRoute.CONNECT && Boolean.FALSE.equals(target.online())) {
            return Verdict.refused(JoinMeResult.Status.SERVER_OFFLINE);
        }
        if (settings.join().joinableStateOnly() && !target.state().joinable()) {
            return Verdict.refused(JoinMeResult.Status.GAME_STATE);
        }
        return new Verdict(JoinMeResult.Status.SUCCESS, route);
    }
}
