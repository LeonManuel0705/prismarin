package net.prismarin.core.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyLeftEvent;
import net.prismarin.shared.social.PartyChanges;
import net.prismarin.shared.social.PartyCodec;

@Singleton
public final class PartyMirror {

    public static final String TYPE = "party-sync";

    private final EventBus events;
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, UUID> byMember = new HashMap<>();

    @Inject
    public PartyMirror(EventBus events) {
        this.events = events;
    }

    public synchronized Optional<Party> partyOf(UUID player) {
        UUID id = byMember.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(parties.get(id));
    }

    public synchronized Optional<Party> party(UUID partyId) {
        return Optional.ofNullable(parties.get(partyId));
    }

    public synchronized int size() {
        return parties.size();
    }

    public void receive(JsonObject message, Predicate<UUID> here) {
        Party before = PartyCodec.decode(message.get("before")).orElse(null);
        Party after = PartyCodec.decode(message.get("after")).orElse(null);
        boolean dissolved = flag(message.get("dissolved"));
        apply(before, after, dissolved, here);
        if ("sync".equals(text(message.get("cause")))) {
            return;
        }
        PartyLeftEvent.Reason reason = PartyLeftEvent.Reason.byId(text(message.get("reason"))).orElse(null);
        UUID actor = uuid(message.get("actor"));
        List<PrismarinEvent> changes = PartyChanges.diff(before, after, dissolved, reason, actor);
        changes.forEach(events::publish);
    }

    public synchronized void apply(Party before, Party after, boolean dissolved, Predicate<UUID> here) {
        UUID id = after != null ? after.id() : before != null ? before.id() : null;
        if (id == null) {
            return;
        }
        byMember.values().removeIf(id::equals);
        Party live = dissolved ? null : after;
        if (live == null || live.memberIds().stream().noneMatch(here)) {
            parties.remove(id);
            return;
        }
        parties.put(id, live);
        for (UUID member : live.memberIds()) {
            byMember.put(member, id);
        }
    }

    public synchronized void forget(UUID player, Predicate<UUID> here) {
        UUID id = byMember.get(player);
        if (id == null) {
            return;
        }
        Party party = parties.get(id);
        if (party == null || party.memberIds().stream().noneMatch(member -> !member.equals(player) && here.test(member))) {
            parties.remove(id);
            byMember.values().removeIf(id::equals);
        }
    }

    private static boolean flag(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static String text(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static UUID uuid(JsonElement element) {
        try {
            String raw = text(element);
            return raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
