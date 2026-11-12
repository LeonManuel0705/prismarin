package net.prismarin.velocity.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanData;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.ClanRules;

public final class ClanManager {

    public enum Status {
        OK,
        SELF,
        NOT_IN_CLAN,
        IN_CLAN,
        TARGET_IN_CLAN,
        NOT_ALLOWED,
        NOT_MEMBER,
        ALREADY_MEMBER,
        ALREADY_INVITED,
        NO_INVITE,
        NO_CLAN,
        CLOSED,
        FULL,
        NAME_TAKEN,
        TAG_TAKEN,
        ALREADY_ROLE,
        TRANSFER_FIRST,
        INVALID,
        UNCHANGED
    }

    public record Result(Status status, Clan before, Clan clan, boolean created, boolean disbanded) {

        static Result of(Status status) {
            return new Result(status, null, null, false, false);
        }

        static Result of(Status status, Clan clan) {
            return new Result(status, null, clan, false, false);
        }

        static Result ok(Clan before, Clan clan) {
            return new Result(Status.OK, before, clan, false, false);
        }

        public boolean ok() {
            return status == Status.OK;
        }

        public Clan after() {
            return disbanded ? null : clan;
        }
    }

    private static final class State {

        private final UUID id;
        private String name;
        private String tag;
        private final long created;
        private String color;
        private ClanAccess access;
        private UUID leader;
        private final LinkedHashMap<UUID, ClanMember> members = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, Long> invites = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> data = new LinkedHashMap<>();

        private State(Clan clan) {
            this.id = clan.id();
            this.name = clan.name();
            this.tag = clan.tag();
            this.created = clan.created();
            this.color = clan.color();
            this.access = clan.access();
            this.leader = clan.leader();
            for (ClanMember member : clan.members()) {
                members.put(member.uuid(), member);
            }
            data.putAll(clan.data());
        }

        private Clan snapshot() {
            return new Clan(id, name, tag, color, access, leader, List.copyOf(members.values()), invites.keySet(),
                    data, created);
        }

        private void prune(long now) {
            invites.values().removeIf(expires -> expires <= now);
        }

        private ClanRole role(UUID player) {
            ClanMember member = members.get(player);
            return member == null ? null : member.role();
        }

        private boolean allowed(UUID actor, ClanAction action, UUID target, ClanRights rights) {
            return ClanRules.allowed(action, role(actor), target == null ? null : role(target), rights);
        }

        private void setRole(UUID player, ClanRole role) {
            ClanMember member = members.get(player);
            members.put(player, new ClanMember(player, member.name(), role, member.joined()));
        }
    }

    private final Map<UUID, State> clans = new LinkedHashMap<>();
    private final Map<UUID, State> byMember = new HashMap<>();
    private final Map<String, State> byName = new HashMap<>();
    private final Map<String, State> byTag = new HashMap<>();
    private final ClanRights rights;

    public ClanManager(ClanRights rights) {
        this.rights = rights == null ? ClanRights.DEFAULT : rights;
    }

    public ClanRights rights() {
        return rights;
    }

    public synchronized void load(Clan clan) {
        State previous = clans.get(clan.id());
        if (previous != null) {
            unindex(previous);
        }
        State state = new State(clan);
        if (previous != null) {
            state.invites.putAll(previous.invites);
            state.members.keySet().forEach(state.invites::remove);
        }
        for (UUID member : List.copyOf(state.members.keySet())) {
            State other = byMember.get(member);
            if (other != null && other != state) {
                other.members.remove(member);
            }
        }
        index(state);
    }

    public synchronized boolean forget(UUID clanId) {
        State state = clans.get(clanId);
        if (state == null) {
            return false;
        }
        unindex(state);
        return true;
    }

    public synchronized Optional<Clan> clanOf(UUID player) {
        return Optional.ofNullable(byMember.get(player)).map(State::snapshot);
    }

    public synchronized Optional<Clan> clan(UUID clanId) {
        return Optional.ofNullable(clans.get(clanId)).map(State::snapshot);
    }

    public synchronized Optional<Clan> find(String nameOrTag) {
        if (nameOrTag == null || nameOrTag.isBlank()) {
            return Optional.empty();
        }
        String key = key(nameOrTag);
        State state = byName.get(key);
        if (state == null) {
            state = byTag.get(key);
        }
        return Optional.ofNullable(state).map(State::snapshot);
    }

    public synchronized boolean nameTaken(String name) {
        return byName.containsKey(key(name));
    }

    public synchronized boolean tagTaken(String tag) {
        return byTag.containsKey(key(tag));
    }

    public synchronized List<Clan> all() {
        List<Clan> all = new ArrayList<>();
        clans.values().forEach(state -> all.add(state.snapshot()));
        all.sort(Comparator.comparingInt(Clan::size).reversed()
                .thenComparing(clan -> clan.name().toLowerCase(Locale.ROOT)));
        return all;
    }

    public synchronized int count() {
        return clans.size();
    }

    public synchronized List<Clan> invitesFor(UUID player, long now) {
        List<State> found = new ArrayList<>();
        for (State state : clans.values()) {
            state.prune(now);
            if (state.invites.containsKey(player)) {
                found.add(state);
            }
        }
        found.sort(Comparator.comparingLong((State state) -> state.invites.get(player)).reversed());
        return found.stream().map(State::snapshot).toList();
    }

    public synchronized Result create(UUID founder, String founderName, String name, String tag, ClanAccess access,
                                      long now) {
        if (byMember.containsKey(founder)) {
            return Result.of(Status.IN_CLAN);
        }
        if (byName.containsKey(key(name)) || byTag.containsKey(key(name))) {
            return Result.of(Status.NAME_TAKEN);
        }
        if (byTag.containsKey(key(tag)) || byName.containsKey(key(tag)) && !key(tag).equals(key(name))) {
            return Result.of(Status.TAG_TAKEN);
        }
        Clan clan = new Clan(UUID.randomUUID(), name.strip(), tag.strip(), "", access, founder,
                List.of(new ClanMember(founder, founderName, ClanRole.LEADER, now)), Set.of(), Map.of(), now);
        State state = new State(clan);
        index(state);
        for (State other : clans.values()) {
            other.invites.remove(founder);
        }
        return new Result(Status.OK, null, state.snapshot(), true, false);
    }

    public synchronized Result invite(UUID actor, UUID target, ToIntFunction<UUID> maxSize, long now,
                                      long expiryMillis) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        if (!state.allowed(actor, ClanAction.INVITE, null, rights)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        State other = byMember.get(target);
        if (other != null) {
            return Result.of(other == state ? Status.ALREADY_MEMBER : Status.TARGET_IN_CLAN);
        }
        state.prune(now);
        if (state.invites.containsKey(target)) {
            return Result.of(Status.ALREADY_INVITED);
        }
        if (state.members.size() >= maxSize.applyAsInt(state.leader)) {
            return Result.of(Status.FULL, state.snapshot());
        }
        Clan before = state.snapshot();
        state.invites.put(target, now + expiryMillis);
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result accept(UUID player, String name, UUID clanId, ToIntFunction<UUID> maxSize, long now) {
        if (byMember.containsKey(player)) {
            return Result.of(Status.IN_CLAN);
        }
        State state = inviting(player, clanId, now);
        if (state == null) {
            return Result.of(Status.NO_INVITE);
        }
        return admit(state, player, name, maxSize, now);
    }

    public synchronized Result join(UUID player, String name, UUID clanId, ToIntFunction<UUID> maxSize, long now) {
        if (byMember.containsKey(player)) {
            return Result.of(Status.IN_CLAN);
        }
        State state = clans.get(clanId);
        if (state == null) {
            return Result.of(Status.NO_CLAN);
        }
        state.prune(now);
        if (!state.invites.containsKey(player) && state.access != ClanAccess.OPEN) {
            return Result.of(Status.CLOSED, state.snapshot());
        }
        return admit(state, player, name, maxSize, now);
    }

    private Result admit(State state, UUID player, String name, ToIntFunction<UUID> maxSize, long now) {
        if (state.members.size() >= maxSize.applyAsInt(state.leader)) {
            return Result.of(Status.FULL, state.snapshot());
        }
        Clan before = state.snapshot();
        state.invites.remove(player);
        state.members.put(player, new ClanMember(player, name, ClanRole.MEMBER, now));
        byMember.put(player, state);
        for (State other : clans.values()) {
            other.invites.remove(player);
        }
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result deny(UUID player, UUID clanId, long now) {
        State state = inviting(player, clanId, now);
        if (state == null) {
            return Result.of(Status.NO_INVITE);
        }
        Clan before = state.snapshot();
        state.invites.remove(player);
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result expire(UUID clanId, UUID player, long expiresAt) {
        State state = clans.get(clanId);
        if (state == null || !Long.valueOf(expiresAt).equals(state.invites.get(player))) {
            return Result.of(Status.NO_INVITE);
        }
        Clan before = state.snapshot();
        state.invites.remove(player);
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result leave(UUID player) {
        State state = byMember.get(player);
        if (state == null) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (state.leader.equals(player)) {
            if (state.members.size() > 1) {
                return Result.of(Status.TRANSFER_FIRST, state.snapshot());
            }
            return dissolve(state);
        }
        return remove(state, player);
    }

    public synchronized Result kick(UUID actor, UUID target) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        if (!state.members.containsKey(target)) {
            if (!state.invites.containsKey(target)) {
                return Result.of(Status.NOT_MEMBER);
            }
            if (!state.allowed(actor, ClanAction.INVITE, null, rights)) {
                return Result.of(Status.NOT_ALLOWED);
            }
            Clan before = state.snapshot();
            state.invites.remove(target);
            return Result.ok(before, state.snapshot());
        }
        if (!state.allowed(actor, ClanAction.KICK, target, rights)) {
            return Result.of(Status.NOT_ALLOWED);
        }
        return remove(state, target);
    }

    public synchronized Result promote(UUID actor, UUID target) {
        State state = member(actor, target);
        if (state == null) {
            return failure(actor, target);
        }
        ClanRole current = state.role(target);
        if (current == ClanRole.CO_LEADER || current == ClanRole.LEADER) {
            return Result.of(Status.ALREADY_ROLE, state.snapshot());
        }
        if (!state.allowed(actor, ClanAction.PROMOTE, target, rights)) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        Clan before = state.snapshot();
        state.setRole(target, current.above().orElse(current));
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result demote(UUID actor, UUID target) {
        State state = member(actor, target);
        if (state == null) {
            return failure(actor, target);
        }
        ClanRole current = state.role(target);
        if (current == ClanRole.MEMBER) {
            return Result.of(Status.ALREADY_ROLE, state.snapshot());
        }
        if (!state.allowed(actor, ClanAction.DEMOTE, target, rights)) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        Clan before = state.snapshot();
        state.setRole(target, current.below().orElse(current));
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result transfer(UUID actor, UUID target) {
        State state = member(actor, target);
        if (state == null) {
            return failure(actor, target);
        }
        if (!state.allowed(actor, ClanAction.TRANSFER, target, rights)) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        Clan before = state.snapshot();
        state.setRole(actor, ClanRole.CO_LEADER);
        state.setRole(target, ClanRole.LEADER);
        state.leader = target;
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result disband(UUID actor) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (!state.allowed(actor, ClanAction.DISBAND, null, rights)) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        return dissolve(state);
    }

    public synchronized Result color(UUID actor, String color) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (!state.allowed(actor, ClanAction.COLOR, null, rights)) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        return recolor(state, color);
    }

    public synchronized Result access(UUID actor, ClanAccess access) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (!state.allowed(actor, ClanAction.SETTINGS, null, rights)) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        return reopen(state, access);
    }

    public synchronized Result renameBy(UUID actor, String name, String tag) {
        State state = byMember.get(actor);
        if (state == null) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (!state.allowed(actor, ClanAction.RENAME, null, rights)) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        return relabel(state, name, tag);
    }

    public synchronized Result rename(UUID clanId, String name, String tag) {
        State state = clans.get(clanId);
        return state == null ? Result.of(Status.NO_CLAN) : relabel(state, name, tag);
    }

    public synchronized Result dissolve(UUID clanId) {
        State state = clans.get(clanId);
        return state == null ? Result.of(Status.NO_CLAN) : dissolve(state);
    }

    public synchronized Result add(UUID clanId, UUID player, String name, ClanRole role, ToIntFunction<UUID> maxSize,
                                   long now) {
        State state = clans.get(clanId);
        if (state == null) {
            return Result.of(Status.NO_CLAN);
        }
        State current = byMember.get(player);
        if (current != null) {
            return Result.of(current == state ? Status.ALREADY_MEMBER : Status.TARGET_IN_CLAN, state.snapshot());
        }
        if (role == null || role == ClanRole.LEADER) {
            return Result.of(Status.NOT_ALLOWED, state.snapshot());
        }
        Result admitted = admit(state, player, name, maxSize, now);
        if (admitted.ok() && role != ClanRole.MEMBER) {
            state.setRole(player, role);
            return Result.ok(admitted.before(), state.snapshot());
        }
        return admitted;
    }

    public synchronized Result removeMember(UUID clanId, UUID player) {
        State state = clans.get(clanId);
        if (state == null) {
            return Result.of(Status.NO_CLAN);
        }
        if (!state.members.containsKey(player)) {
            return Result.of(Status.NOT_MEMBER, state.snapshot());
        }
        if (state.leader.equals(player)) {
            return state.members.size() > 1 ? Result.of(Status.TRANSFER_FIRST, state.snapshot()) : dissolve(state);
        }
        return remove(state, player);
    }

    public synchronized Result setRole(UUID clanId, UUID player, ClanRole role) {
        State state = clans.get(clanId);
        if (state == null) {
            return Result.of(Status.NO_CLAN);
        }
        if (!state.members.containsKey(player) || role == null) {
            return Result.of(Status.NOT_MEMBER, state.snapshot());
        }
        ClanRole current = state.role(player);
        if (current == role) {
            return Result.of(Status.ALREADY_ROLE, state.snapshot());
        }
        if (current == ClanRole.LEADER) {
            return Result.of(Status.TRANSFER_FIRST, state.snapshot());
        }
        Clan before = state.snapshot();
        if (role == ClanRole.LEADER) {
            state.setRole(state.leader, ClanRole.CO_LEADER);
            state.leader = player;
        }
        state.setRole(player, role);
        return Result.ok(before, state.snapshot());
    }

    public synchronized Result setColor(UUID clanId, String color) {
        State state = clans.get(clanId);
        return state == null ? Result.of(Status.NO_CLAN) : recolor(state, color);
    }

    public synchronized Result setAccess(UUID clanId, ClanAccess access) {
        State state = clans.get(clanId);
        return state == null ? Result.of(Status.NO_CLAN) : reopen(state, access);
    }

    public synchronized Result setData(UUID clanId, String key, String value) {
        State state = clans.get(clanId);
        if (state == null) {
            return Result.of(Status.NO_CLAN);
        }
        if (!ClanData.validKey(key) || !ClanData.validValue(value)) {
            return Result.of(Status.INVALID, state.snapshot());
        }
        String next = value == null || value.isEmpty() ? null : value;
        if (Objects.equals(state.data.get(key), next)) {
            return Result.of(Status.UNCHANGED, state.snapshot());
        }
        if (next != null && !state.data.containsKey(key) && state.data.size() >= ClanData.MAX_KEYS) {
            return Result.of(Status.FULL, state.snapshot());
        }
        Clan before = state.snapshot();
        if (next == null) {
            state.data.remove(key);
        } else {
            state.data.put(key, next);
        }
        return Result.ok(before, state.snapshot());
    }

    private Result recolor(State state, String color) {
        String next = color == null ? "" : color;
        if (next.equals(state.color)) {
            return Result.of(Status.UNCHANGED, state.snapshot());
        }
        Clan before = state.snapshot();
        state.color = next;
        return Result.ok(before, state.snapshot());
    }

    private Result reopen(State state, ClanAccess access) {
        if (access == null || access == state.access) {
            return Result.of(Status.UNCHANGED, state.snapshot());
        }
        Clan before = state.snapshot();
        state.access = access;
        return Result.ok(before, state.snapshot());
    }

    private Result relabel(State state, String name, String tag) {
        String nextName = name == null || name.isBlank() ? state.name : name.strip();
        String nextTag = tag == null || tag.isBlank() ? state.tag : tag.strip();
        if (nextName.equals(state.name) && nextTag.equals(state.tag)) {
            return Result.of(Status.UNCHANGED, state.snapshot());
        }
        if (taken(nextName, state)) {
            return Result.of(Status.NAME_TAKEN, state.snapshot());
        }
        if (taken(nextTag, state)) {
            return Result.of(Status.TAG_TAKEN, state.snapshot());
        }
        Clan before = state.snapshot();
        unindex(state);
        state.name = nextName;
        state.tag = nextTag;
        index(state);
        return Result.ok(before, state.snapshot());
    }

    private boolean taken(String value, State self) {
        State byNameOwner = byName.get(key(value));
        State byTagOwner = byTag.get(key(value));
        return byNameOwner != null && byNameOwner != self || byTagOwner != null && byTagOwner != self;
    }

    public synchronized Optional<Clan> refreshName(UUID player, String name) {
        State state = byMember.get(player);
        if (state == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        ClanMember member = state.members.get(player);
        if (member.name().equals(name)) {
            return Optional.empty();
        }
        state.members.put(player, new ClanMember(player, name, member.role(), member.joined()));
        return Optional.of(state.snapshot());
    }

    private State member(UUID actor, UUID target) {
        State state = byMember.get(actor);
        if (state == null || actor.equals(target) || !state.members.containsKey(target)) {
            return null;
        }
        return state;
    }

    private Result failure(UUID actor, UUID target) {
        if (!byMember.containsKey(actor)) {
            return Result.of(Status.NOT_IN_CLAN);
        }
        if (actor.equals(target)) {
            return Result.of(Status.SELF);
        }
        return Result.of(Status.NOT_MEMBER);
    }

    private State inviting(UUID player, UUID clanId, long now) {
        if (clanId != null) {
            State state = clans.get(clanId);
            if (state == null) {
                return null;
            }
            state.prune(now);
            return state.invites.containsKey(player) ? state : null;
        }
        State chosen = null;
        long newest = Long.MIN_VALUE;
        for (State state : clans.values()) {
            state.prune(now);
            Long expires = state.invites.get(player);
            if (expires != null && expires > newest) {
                newest = expires;
                chosen = state;
            }
        }
        return chosen;
    }

    private Result remove(State state, UUID player) {
        Clan before = state.snapshot();
        state.members.remove(player);
        byMember.remove(player);
        return Result.ok(before, state.snapshot());
    }

    private Result dissolve(State state) {
        Clan snapshot = state.snapshot();
        unindex(state);
        return new Result(Status.OK, snapshot, snapshot, false, true);
    }

    private void index(State state) {
        clans.put(state.id, state);
        byName.put(key(state.name), state);
        byTag.put(key(state.tag), state);
        for (UUID member : state.members.keySet()) {
            byMember.put(member, state);
        }
    }

    private void unindex(State state) {
        clans.remove(state.id);
        byName.remove(key(state.name), state);
        byTag.remove(key(state.tag), state);
        byMember.values().removeIf(state::equals);
    }

    private static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
