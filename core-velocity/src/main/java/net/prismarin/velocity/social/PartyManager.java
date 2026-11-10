package net.prismarin.velocity.social;

import java.util.ArrayList;
import java.util.Collections;
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
import java.util.function.UnaryOperator;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyAction;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartyRules;
import net.prismarin.api.social.PartySettings;

public final class PartyManager {

    public record Party(UUID id, UUID leader, List<UUID> members, Set<UUID> moderators, Map<UUID, Long> invites,
                        PartySettings settings) {

        public Party {
            members = List.copyOf(members);
            moderators = Collections.unmodifiableSet(new LinkedHashSet<>(moderators));
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

        public PartyRole role(UUID player) {
            if (leader.equals(player)) {
                return PartyRole.LEADER;
            }
            if (moderators.contains(player)) {
                return PartyRole.MODERATOR;
            }
            return members.contains(player) ? PartyRole.MEMBER : null;
        }

        public boolean allowed(UUID actor, PartyAction action, UUID target) {
            return PartyRules.allowed(action, role(actor), target == null ? null : role(target), settings);
        }
    }

    public enum Status {
        OK,
        SELF,
        NOT_IN_PARTY,
        NOT_ALLOWED,
        NOT_MEMBER,
        ALREADY_MEMBER,
        TARGET_IN_PARTY,
        IN_PARTY,
        ALREADY_INVITED,
        FULL,
        NO_INVITE,
        NO_PARTY,
        CLOSED,
        ALREADY_ROLE,
        UNCHANGED
    }

    public record Result(Status status, Party before, Party party, boolean created, boolean disbanded,
                         UUID newLeader, List<UUID> formerMembers) {

        public Result {
            formerMembers = formerMembers == null ? List.of() : List.copyOf(formerMembers);
        }

        static Result of(Status status) {
            return new Result(status, null, null, false, false, null, List.of());
        }

        static Result ok(Party before, Party party) {
            return new Result(Status.OK, before, party, false, false, null, List.of());
        }

        public boolean ok() {
            return status == Status.OK;
        }

        public Party after() {
            return disbanded ? null : party;
        }
    }

    private static final class State {

        private final UUID id;
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> moderators = new LinkedHashSet<>();
        private final Map<UUID, Long> invites = new LinkedHashMap<>();
        private PartySettings settings;

        private State(UUID id, UUID leader, PartySettings settings) {
            this.id = id;
            this.leader = leader;
            this.settings = settings;
            members.add(leader);
        }

        private Party snapshot() {
            return new Party(id, leader, List.copyOf(members), moderators, invites, settings);
        }

        private void prune(long now) {
            invites.values().removeIf(expires -> expires <= now);
        }

        private PartyRole role(UUID player) {
            return snapshot().role(player);
        }

        private boolean allowed(UUID actor, PartyAction action, UUID target) {
            return PartyRules.allowed(action, role(actor), target == null ? null : role(target), settings);
        }

        private UUID successor(Set<UUID> candidates) {
            for (UUID moderator : moderators) {
                if (!moderator.equals(leader) && candidates.contains(moderator)) {
                    return moderator;
                }
            }
            for (UUID member : members) {
                if (!member.equals(leader) && candidates.contains(member)) {
                    return member;
                }
            }
            return null;
        }

        private void lead(UUID next) {
            moderators.remove(next);
            leader = next;
        }
    }

    private final Map<UUID, State> byMember = new HashMap<>();
    private final Map<UUID, State> parties = new LinkedHashMap<>();
    private final PartySettings defaults;

    public PartyManager(PartySettings defaults) {
        this.defaults = defaults == null ? PartySettings.DEFAULT : defaults;
    }

    public PartySettings defaults() {
        return defaults;
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
        if (state != null && !state.allowed(actor, PartyAction.INVITE, null)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        State other = byMember.get(target);
        if (other != null) {
            return Result.of(other == state ? Status.ALREADY_MEMBER : Status.TARGET_IN_PARTY);
        }
        boolean created = false;
        Party before = null;
        if (state == null) {
            state = new State(UUID.randomUUID(), actor, defaults);
            created = true;
        } else {
            state.prune(now);
            before = state.snapshot();
        }
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
        return new Result(Status.OK, before, state.snapshot(), created, false, null, List.of());
    }

    public synchronized Result accept(UUID player, UUID leader, ToIntFunction<UUID> maxSize, long now) {
        if (byMember.containsKey(player)) {
            return Result.of(Status.IN_PARTY);
        }
        State state = invitingParty(player, leader, now);
        if (state == null) {
            return Result.of(Status.NO_INVITE);
        }
        return admit(state, player, maxSize);
    }

    public synchronized Result join(UUID player, UUID member, Set<UUID> joinerFriends, ToIntFunction<UUID> maxSize,
                                    long now) {
        if (player.equals(member)) {
            return Result.of(Status.SELF);
        }
        if (byMember.containsKey(player)) {
            return Result.of(Status.IN_PARTY);
        }
        State state = byMember.get(member);
        if (state == null) {
            return Result.of(Status.NO_PARTY);
        }
        state.prune(now);
        boolean invited = state.invites.containsKey(player);
        boolean allowed = invited || state.settings.access() == PartyAccess.OPEN
                || state.settings.access() == PartyAccess.FRIENDS && joinerFriends.contains(state.leader);
        if (!allowed) {
            return new Result(Status.CLOSED, null, state.snapshot(), false, false, null, List.of());
        }
        return admit(state, player, maxSize);
    }

    private Result admit(State state, UUID player, ToIntFunction<UUID> maxSize) {
        if (state.members.size() >= maxSize.applyAsInt(state.leader)) {
            return new Result(Status.FULL, null, state.snapshot(), false, false, null, List.of());
        }
        Party before = state.snapshot();
        state.invites.remove(player);
        state.members.add(player);
        byMember.put(player, state);
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result deny(UUID player, UUID leader, long now) {
        State state = invitingParty(player, leader, now);
        if (state == null) {
            return Result.of(Status.NO_INVITE);
        }
        Party before = state.snapshot();
        state.invites.remove(player);
        return disbandIfLonely(state, before);
    }

    public synchronized Result expire(UUID partyId, UUID player, long expiresAt) {
        State state = parties.get(partyId);
        if (state == null || !Long.valueOf(expiresAt).equals(state.invites.get(player))) {
            return Result.of(Status.NO_INVITE);
        }
        Party before = state.snapshot();
        state.invites.remove(player);
        return disbandIfLonely(state, before);
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
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        if (!state.role(actor).atLeast(PartyRole.MODERATOR)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        if (!state.members.contains(target)) {
            if (state.invites.containsKey(target)) {
                if (!state.allowed(actor, PartyAction.REVOKE, null)) {
                    return Result.of(Status.NOT_ALLOWED);
                }
                Party before = state.snapshot();
                state.invites.remove(target);
                return disbandIfLonely(state, before);
            }
            return Result.of(Status.NOT_MEMBER);
        }
        if (!state.allowed(actor, PartyAction.KICK, target)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        return remove(state, target);
    }

    public synchronized Result promote(UUID actor, UUID target) {
        State state = leaderAction(actor, target);
        if (state == null) {
            return failure(actor, target);
        }
        if (state.moderators.contains(target)) {
            return Result.of(Status.ALREADY_ROLE);
        }
        Party before = state.snapshot();
        state.moderators.add(target);
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result demote(UUID actor, UUID target) {
        State state = leaderAction(actor, target);
        if (state == null) {
            return failure(actor, target);
        }
        if (!state.moderators.contains(target)) {
            return Result.of(Status.ALREADY_ROLE);
        }
        Party before = state.snapshot();
        state.moderators.remove(target);
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result transfer(UUID actor, UUID target) {
        State state = leaderAction(actor, target);
        if (state == null) {
            return failure(actor, target);
        }
        Party before = state.snapshot();
        state.moderators.add(actor);
        state.lead(target);
        return new Result(Status.OK, before, state.snapshot(), false, false, target, List.of());
    }

    private State leaderAction(UUID actor, UUID target) {
        State state = byMember.get(actor);
        if (state == null || actor.equals(target) || !state.leader.equals(actor)
                || !state.members.contains(target)) {
            return null;
        }
        return state;
    }

    private Result failure(UUID actor, UUID target) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_PARTY);
        }
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        if (!state.leader.equals(actor)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        return Result.of(Status.NOT_MEMBER);
    }

    public synchronized Result configure(UUID actor, UnaryOperator<PartySettings> change) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_PARTY);
        }
        if (!state.allowed(actor, PartyAction.SETTINGS, null)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        PartySettings next = change.apply(state.settings);
        if (next == null || state.settings.equals(next)) {
            return new Result(Status.UNCHANGED, null, state.snapshot(), false, false, null, List.of());
        }
        Party before = state.snapshot();
        state.settings = next;
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result disband(UUID actor) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_PARTY);
        }
        if (!state.allowed(actor, PartyAction.DISBAND, null)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        return dissolve(state, state.snapshot());
    }

    public synchronized Result handOver(UUID leader, Set<UUID> online) {
        State state = byMember.get(leader);
        if (state == null || !state.leader.equals(leader)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        UUID next = state.successor(online);
        if (next == null) {
            return Result.of(Status.NOT_MEMBER);
        }
        Party before = state.snapshot();
        state.moderators.add(leader);
        state.lead(next);
        return new Result(Status.OK, before, state.snapshot(), false, false, next, List.of());
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
        Party before = state.snapshot();
        state.members.remove(player);
        state.moderators.remove(player);
        byMember.remove(player);
        UUID newLeader = null;
        if (state.leader.equals(player) && !state.members.isEmpty()) {
            newLeader = state.successor(state.members);
            state.lead(newLeader);
        }
        if (state.members.isEmpty()) {
            parties.remove(state.id);
            return new Result(Status.OK, before, state.snapshot(), false, true, null, List.of());
        }
        if (lonely(state)) {
            Result disband = dissolve(state, before);
            return new Result(Status.OK, before, disband.party(), false, true, newLeader, disband.formerMembers());
        }
        return new Result(Status.OK, before, state.snapshot(), false, false, newLeader, List.of());
    }

    private Result disbandIfLonely(State state, Party before) {
        if (!lonely(state)) {
            return Result.ok(before, state.snapshot());
        }
        return dissolve(state, before);
    }

    private static boolean lonely(State state) {
        return state.members.size() <= 1 && state.invites.isEmpty();
    }

    private Result dissolve(State state, Party before) {
        Party snapshot = state.snapshot();
        for (UUID member : state.members) {
            byMember.remove(member);
        }
        parties.remove(state.id);
        return new Result(Status.OK, before, snapshot, false, true, null, snapshot.members());
    }
}
