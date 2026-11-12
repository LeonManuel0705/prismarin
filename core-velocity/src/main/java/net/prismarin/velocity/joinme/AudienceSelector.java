package net.prismarin.velocity.joinme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;

public final class AudienceSelector {

    public record Candidate(UUID player, String server, String group, String world, boolean permitted) {

        public Candidate {
            Objects.requireNonNull(player, "player");
            server = server == null ? "" : server;
            group = group == null ? "" : group;
            world = world == null ? "" : world;
        }
    }

    public record Circles(Set<UUID> friends, Set<UUID> party, Set<UUID> clan) {

        public static final Circles NONE = new Circles(Set.of(), Set.of(), Set.of());

        public Circles {
            friends = Set.copyOf(friends);
            party = Set.copyOf(party);
            clan = Set.copyOf(clan);
        }

        public boolean contains(JoinMeAudience audience, UUID player) {
            if (!audience.social()) {
                return true;
            }
            return audience.circles().contains(JoinMeAudience.Circle.FRIENDS) && friends.contains(player)
                    || audience.circles().contains(JoinMeAudience.Circle.PARTY) && party.contains(player)
                    || audience.circles().contains(JoinMeAudience.Circle.CLAN) && clan.contains(player);
        }
    }

    private AudienceSelector() {
        throw new AssertionError();
    }

    public static List<UUID> select(JoinMe joinMe, List<Candidate> candidates, Circles circles,
                                    boolean skipSameWorld) {
        List<UUID> chosen = new ArrayList<>();
        JoinMeAudience audience = joinMe.audience();
        for (Candidate candidate : candidates) {
            if (candidate.player().equals(joinMe.creator()) || !candidate.permitted()) {
                continue;
            }
            if (!audience.covers(candidate.group()) || !circles.contains(audience, candidate.player())) {
                continue;
            }
            if (skipSameWorld && sameWorld(joinMe, candidate)) {
                continue;
            }
            chosen.add(candidate.player());
        }
        return List.copyOf(chosen);
    }

    public static boolean sameWorld(JoinMe joinMe, Candidate candidate) {
        return !joinMe.world().isEmpty() && joinMe.server().equalsIgnoreCase(candidate.server())
                && joinMe.world().equalsIgnoreCase(candidate.world());
    }
}
