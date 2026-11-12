package net.prismarin.velocity.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.server.GameState;
import net.prismarin.shared.joinme.JoinMeDraft;
import net.prismarin.shared.joinme.JoinMeRoute;
import net.prismarin.shared.joinme.JoinMeTerms;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

class JoinMeRulesTest {

    private final UUID creator = UUID.randomUUID();
    private final UUID clicker = UUID.randomUUID();

    private static JoinMeSettings settings(boolean sameServer, int maxJoins) throws Exception {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        JoinMeSettings.fillDefaults(root);
        root.node("cooldowns", "staff").set(0);
        root.node("costs", "premium", "currency").set("coins");
        root.node("costs", "premium", "amount").set(10);
        root.node("costs", Tiers.DEFAULT, "amount").set(50);
        root.node("lifetime-tiers", "premium").set(200);
        root.node("max-joins-tiers", "premium").set(12);
        root.node("max-joins-tiers", "youtuber").set(0);
        root.node("create", "max-joins").set(maxJoins);
        root.node("join", "same-server").set(sameServer);
        return JoinMeSettings.read(root, List.of());
    }

    private static Predicate<String> has(String... permissions) {
        return Set.of(permissions)::contains;
    }

    private JoinMeDraft draft(JoinMeRequest request) {
        return JoinMeDraft.of(new JoinMeSpot(creator, "bedwars-2", "bedwars", "arena-1", GameState.WAITING), request)
                .withHandler("", true);
    }

    private JoinMeRules.Creator creator(String group, GameState state, long cooldown, Predicate<String> has) {
        return new JoinMeRules.Creator(true, false, group, state, cooldown, has);
    }

    @Test
    void theShortestGrantedCooldownWins() throws Exception {
        JoinMeRules rules = new JoinMeRules(settings(true, 0));
        assertEquals(300_000L, rules.cooldownMillis(has()));
        assertEquals(180_000L, rules.cooldownMillis(has("prismarin.joinme.cooldown.premium")));
        assertEquals(60_000L, rules.cooldownMillis(has("prismarin.joinme.cooldown.premium",
                "prismarin.joinme.cooldown.youtuber")));
        assertEquals(0L, rules.cooldownMillis(has("prismarin.joinme.cooldown.staff")));
    }

    @Test
    void costsLifetimesAndJoinsPickTheBestTier() throws Exception {
        JoinMeRules rules = new JoinMeRules(settings(true, 5));
        assertEquals(new JoinMeSettings.Cost("coins", 50L), rules.cost(has()));
        assertEquals(new JoinMeSettings.Cost("coins", 10L), rules.cost(has("prismarin.joinme.cost.premium")));
        assertEquals(120_000L, rules.lifetimeMillis(has()));
        assertEquals(300_000L, rules.lifetimeMillis(has("prismarin.joinme.lifetime.youtuber")));
        assertEquals(300_000L, rules.lifetimeMillis(has("prismarin.joinme.lifetime.youtuber",
                "prismarin.joinme.lifetime.premium")));
        assertEquals(5, rules.maxJoins(has()));
        assertEquals(12, rules.maxJoins(has("prismarin.joinme.joins.premium")));
        assertEquals(0, rules.maxJoins(has("prismarin.joinme.joins.premium", "prismarin.joinme.joins.youtuber")));
        assertEquals(0, new JoinMeRules(settings(true, 0)).maxJoins(has("prismarin.joinme.joins.premium")));
    }

    @Test
    void creationChecksRunInAFixedOrder() throws Exception {
        JoinMeRules rules = new JoinMeRules(settings(true, 0));
        JoinMeDraft draft = draft(JoinMeRequest.of(creator));
        assertEquals(JoinMeResult.Status.OFFLINE, rules.check(new JoinMeRules.Creator(false, true, "bedwars",
                GameState.WAITING, 0L, has()), draft).status());
        assertEquals(JoinMeResult.Status.VANISHED, rules.check(new JoinMeRules.Creator(true, true, "lobby",
                GameState.RUNNING, 5L, has()), draft).status());
        assertEquals(JoinMeResult.Status.GROUP_BLOCKED, rules.check(creator("lobby", GameState.RUNNING, 5L, has()),
                draft).status());
        assertEquals(JoinMeResult.Status.GAME_STATE, rules.check(creator("bedwars", GameState.RUNNING, 5L, has()),
                draft).status());
        JoinMeTerms cooldown = rules.check(creator("bedwars", GameState.WAITING, 5_000L, has()), draft);
        assertEquals(JoinMeResult.Status.COOLDOWN, cooldown.status());
        assertEquals(5_000L, cooldown.waitMillis());
        assertTrue(rules.check(creator("bedwars", GameState.WAITING, 5_000L, has()),
                draft(JoinMeRequest.of(creator).ignoringCooldown())).allowed());
    }

    @Test
    void allowedCreationsCarryTheResolvedTerms() throws Exception {
        JoinMeRules rules = new JoinMeRules(settings(false, 3));
        JoinMeTerms terms = rules.check(creator("bedwars", GameState.UNKNOWN, 0L,
                has("prismarin.joinme.cost.premium")), draft(JoinMeRequest.of(creator)));
        assertTrue(terms.allowed());
        assertEquals("coins", terms.currency());
        assertEquals(10L, terms.cost());
        assertEquals(120_000L, terms.lifetimeMillis());
        assertEquals(3, terms.maxJoins());
        assertEquals(Set.of("lobby"), terms.audience().groups());
        assertFalse(terms.sameServer());
        JoinMeTerms custom = rules.check(creator("bedwars", GameState.UNKNOWN, 0L, has()),
                draft(JoinMeRequest.of(creator).withMaxJoins(0).withoutCost()
                        .withLifetime(Duration.ofSeconds(30)).withAudience(JoinMeAudience.network())));
        assertEquals(0L, custom.cost());
        assertFalse(custom.paid());
        assertEquals(30_000L, custom.lifetimeMillis());
        assertEquals(0, custom.maxJoins());
        assertEquals(JoinMeAudience.NETWORK, custom.audience());
    }

    private JoinMe joinMe(String handler, int maxJoins, int joins) {
        return new JoinMe("k3f9a2", creator, "Lea", "Lea", "bedwars-2", "bedwars", "arena-1", GameState.WAITING,
                handler, Map.of(), JoinMeAudience.NETWORK, maxJoins, joins, 0L, 10_000L);
    }

    private JoinMeRules.Clicker on(String server, String world) {
        return new JoinMeRules.Clicker(clicker, true, server, world, true, true);
    }

    @Test
    void clicksAreRefusedForTheRightReason() throws Exception {
        JoinMeRules rules = new JoinMeRules(settings(true, 0));
        JoinMeRules.Target up = new JoinMeRules.Target(true, GameState.WAITING);
        JoinMe open = joinMe("", 2, 1);
        assertEquals(JoinMeResult.Status.OWN, rules.join(open, true, 0, new JoinMeRules.Clicker(creator, true,
                "lobby-1", "", true, false), up, 0L).status());
        assertEquals(JoinMeResult.Status.NOT_FOUND, rules.join(open, true, 0, new JoinMeRules.Clicker(clicker, true,
                "lobby-1", "", true, false), up, 0L).status());
        assertEquals(JoinMeResult.Status.EXPIRED, rules.join(open, true, 0, on("lobby-1", ""), up, 10_000L)
                .status());
        assertEquals(JoinMeResult.Status.USED_UP, rules.join(open, true, 1, on("lobby-1", ""), up, 0L).status());
        assertEquals(JoinMeResult.Status.NO_PERMISSION, rules.join(open, true, 0, new JoinMeRules.Clicker(clicker,
                true, "lobby-1", "", false, true), up, 0L).status());
        assertEquals(JoinMeResult.Status.SERVER_OFFLINE, rules.join(open, true, 0, on("lobby-1", ""),
                new JoinMeRules.Target(false, GameState.UNKNOWN), 0L).status());
        assertEquals(JoinMeResult.Status.GAME_STATE, rules.join(open, true, 0, on("lobby-1", ""),
                new JoinMeRules.Target(true, GameState.RUNNING), 0L).status());
        assertEquals(JoinMeResult.Status.ALREADY_THERE, rules.join(open, true, 0, on("bedwars-2", "ARENA-1"), up,
                0L).status());
    }

    @Test
    void allowedClicksKnowTheWayToTheCreator() throws Exception {
        JoinMeRules both = new JoinMeRules(settings(true, 0));
        JoinMeRules worldOnly = new JoinMeRules(settings(false, 0));
        JoinMeRules.Target up = JoinMeRules.Target.UNKNOWN;
        assertEquals(JoinMeRoute.CONNECT, both.join(joinMe("", 0, 0), true, 0, on("lobby-1", "world"), up, 0L)
                .route());
        assertEquals(JoinMeRoute.ARRIVE, both.join(joinMe("", 0, 0), true, 0, on("bedwars-2", "hub"), up, 0L)
                .route());
        assertEquals(JoinMeResult.Status.ALREADY_THERE, both.join(joinMe("", 0, 0), false, 0,
                on("bedwars-2", "hub"), up, 0L).status());
        assertEquals(JoinMeResult.Status.OTHER_SERVER, worldOnly.join(joinMe("", 0, 0), true, 0,
                on("lobby-1", "world"), up, 0L).status());
        assertEquals(JoinMeRoute.ARRIVE, worldOnly.join(joinMe("", 0, 0), true, 0, on("bedwars-2", "hub"), up, 0L)
                .route());
        assertEquals(JoinMeRoute.CONNECT, worldOnly.join(joinMe("bedwars", 0, 0), false, 0,
                on("lobby-1", "world"), up, 0L).route());
        assertEquals(JoinMeRoute.ARRIVE, worldOnly.join(joinMe("bedwars", 0, 0), false, 0,
                on("bedwars-2", "arena-1"), up, 0L).route());
    }
}
