package net.prismarin.velocity.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

public final class PartyManager {

    public record Party(UUID id, UUID leader, List<UUID> members, Map<UUID, Long> invites) {

        public Party {
            members = List.copyOf(members);
            invites = Map.copyOf(invites);
        }

        public boolean leader(UUID player) {
            return leader.equals(player);
        }

        public boolean member(UUID player) {
            return members.contains(player);
        }

        public int size() {
            return members.size();
        }
    }

    public enum Status {
        OK,
        SELF,
        NOT_IN_PARTY,
        NOT_LEADER,
        NOT_MEMBER,
        ALREADY_MEMBER,
        TARGET_IN_PARTY,
        IN_PARTY,
        ALREADY_INVITED,
        FULL,
        NO_INVITE
    }

    public record Result(Status status, Party party, boolean created, boolean disbanded, UUID newLeader,
                         List<UUID> formerMembers) {

        public Result {
            formerMembers = formerMembers == null ? List.of() : List.copyOf(formerMembers);
        }

        static Result of(Status status) {
            return new Result(status, null, false, false, null, List.of());
        }

        static Result ok(Party party) {
            return new Result(Status.OK, party, false, false, null, List.of());
        }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    private static final class State {

        private final UUID id;
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private final Map<UUID, Long> invites = new LinkedHashMap<>();

        private State(UUID id, UUID leader) {
            this.id = id;
            this.leader = leader;
            members.add(leader);
        }

        private Party snapshot() {
            return new Party(id, leader, List.copyOf(members), invites);
        }

        private void prune(long now) {
            invites.values().removeIf(expires -> expires <= now);
        }
    }

    private final Map<UUID, State> byMember = new HashMap<>();
    private final Map<UUID, State> parties = new LinkedHashMap<>();
    private final boolean membersCanInvite;

    public PartyManager(boolean membersCanInvite) {
        this.membersCanInvite = membersCanInvite;
    }

    public synchronized Optional<Party> partyOf(UUID player) {
        return Optional.ofNullable(byMember.get(player)).map(State::snapshot);
    }

    public synchronized Optional<Party> party(UUID id) {
        return Optional.ofNullable(parties.get(id)).map(State::snapshot);
    }

    public synchronized List<Party> invitesFor(UUID player, long now) {
        List<Party> found = new ArrayList<>();
        for (State state : parties.values()) {
            state.prune(now);
            if (state.invites.containsKey(player)) {
                found.add(state.snapshot());
            }
        }
        found.sort(Comparator.comparingLong((Party party) -> party.invites().get(player)).reversed());
        return found;
    }

    public synchronized Result invite(UUID actor, UUID target, int maxSize, long now, long expiryMillis) {
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        State state = byMember.get(actor);
        if (state != null && !state.leader.equals(actor) && !membersCanInvite) {
            return Result.of(Status.NOT_LEADER);
        }
        State other = byMember.get(target);
        if (other != null) {
            return Result.of(other == state ? Status.ALREADY_MEMBER : Status.TARGET_IN_PARTY);
        }
        boolean created = false;
        if (state == null) {
            state = new State(UUID.randomUUID(), actor);
            created = true;
        }
        state.prune(now);
        if (state.invites.containsKey(target)) {
            return Result.of(Status.ALREADY_INVITED);
        }
        if (state.members.size() >= maxSize) {
            return Result.of(Status.FULL);
        }
        if (created) {
            parties.put(state.id, state);
            byMember.put(actor, state);
        }
        state.invites.put(target, now + expiryMillis);
        return new Result(Status.OK, state.snapshot(), created, false, null, List.of());
    }

    public synchronized Result accept(UUID player, UUID leader, ToIntFunction<UUID> maxSize, long now) {
        if (byMember.containsKey(player)) {
            return Result.of(Status.IN_PARTY);
        }
        State state = invitingParty(player, leader, now);
        if (state == null) {
            return Result.of(Status.NO_INVITE);
        }
        if (state.members.size() >= maxSize.applyAsInt(state.leader)) {
            return new Result(Status.FULL, state.snapshot(), false, false, null, List.of());
        }
        state.invites.remove(player);
        state.members.add(player);
        byMember.put(player, state);
        return Result.ok(state.snapshot());
    }

    public synchronized Result deny(UUID player, UUID leader, long now) {
        State state = invitingParty(player, leader, now);
        if (state == null) {
            return Result.of(Status.NO_INVITE);
        }
        state.invites.remove(player);
        Result disband = disbandIfLonely(state);
        return disband != null ? disband : Result.ok(state.snapshot());
    }

    public synchronized boolean expire(UUID partyId, UUID player, long expiresAt) {
        State state = parties.get(partyId);
        if (state == null || !Long.valueOf(expiresAt).equals(state.invites.get(player))) {
            return false;
        }
        state.invites.remove(player);
        disbandIfLonely(state);
        return true;
    }

    public synchronized Result leave(UUID player) {
        State state = byMember.get(player);
        if (state == null) {
            return Result.of(Status.NOT_IN_PARTY);
        }
        return remove(state, player);
    }

    public synchronized Result kick(UUID actor, UUID target) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_PARTY);
        }
        if (!state.leader.equals(actor)) {
            return Result.of(Status.NOT_LEADER);
        }
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        if (!state.members.contains(target)) {
            if (state.invites.remove(target) != null) {
                Result disband = disbandIfLonely(state);
                return disband != null ? disband : Result.ok(state.snapshot());
            }
            return Result.of(Status.NOT_MEMBER);
        }
        return remove(state, target);
    }

    public synchronized Result promote(UUID actor, UUID target) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_PARTY);
        }
        if (!state.leader.equals(actor)) {
            return Result.of(Status.NOT_LEADER);
        }
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        if (!state.members.contains(target)) {
            return Result.of(Status.NOT_MEMBER);
        }
        state.leader = target;
        return new Result(Status.OK, state.snapshot(), false, false, target, List.of());
    }

    public synchronized Result disband(UUID actor) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_PARTY);
        }
        if (!state.leader.equals(actor)) {
            return Result.of(Status.NOT_LEADER);
        }
        return dissolve(state);
    }

    public synchronized Optional<UUID> handOver(UUID leader, Set<UUID> online) {
        State state = byMember.get(leader);
        if (state == null || !state.leader.equals(leader)) {
            return Optional.empty();
        }
        for (UUID member : state.members) {
            if (!member.equals(leader) && online.contains(member)) {
                state.leader = member;
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    public synchronized int count() {
        return parties.size();
    }

    private State invitingParty(UUID player, UUID leader, long now) {
        State chosen = null;
        long newest = Long.MIN_VALUE;
        for (State state : parties.values()) {
            state.prune(now);
            Long expires = state.invites.get(player);
            if (expires == null) {
                continue;
            }
            if (leader != null) {
                if (state.members.contains(leader)) {
                    return state;
                }
                continue;
            }
            if (expires > newest) {
                newest = expires;
                chosen = state;
            }
        }
        return chosen;
    }

    private Result remove(State state, UUID player) {
        state.members.remove(player);
        byMember.remove(player);
        UUID newLeader = null;
        if (state.leader.equals(player) && !state.members.isEmpty()) {
            newLeader = state.members.iterator().next();
            state.leader = newLeader;
        }
        if (state.members.isEmpty()) {
            parties.remove(state.id);
            return new Result(Status.OK, state.snapshot(), false, true, null, List.of());
        }
        Result disband = disbandIfLonely(state);
        if (disband != null) {
            return new Result(Status.OK, disband.party(), false, true, newLeader, disband.formerMembers());
        }
        return new Result(Status.OK, state.snapshot(), false, false, newLeader, List.of());
    }

    private Result disbandIfLonely(State state) {
        if (state.members.size() > 1 || !state.invites.isEmpty()) {
            return null;
        }
        return dissolve(state);
    }

    private Result dissolve(State state) {
        Party snapshot = state.snapshot();
        for (UUID member : state.members) {
            byMember.remove(member);
        }
        parties.remove(state.id);
        return new Result(Status.OK, snapshot, false, true, null, snapshot.members());
    }
}
