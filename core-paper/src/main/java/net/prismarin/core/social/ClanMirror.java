package net.prismarin.core.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanRights;
import net.prismarin.shared.social.ClanCodec;
import net.prismarin.shared.social.ClanTagFormat;

@Singleton
public final class ClanMirror {

    public static final String TYPE = "clan-sync";

    private final Map<UUID, Clan> clans = new HashMap<>();
    private final Map<UUID, UUID> byMember = new HashMap<>();
    private volatile ClanTagFormat display = ClanTagFormat.DEFAULT;
    private volatile ClanRights rights = ClanRights.DEFAULT;
    private volatile Runnable listener = () -> {
    };

    public synchronized Optional<Clan> clanOf(UUID player) {
        UUID id = byMember.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(clans.get(id));
    }

    public synchronized Optional<Clan> clan(UUID clanId) {
        return Optional.ofNullable(clans.get(clanId));
    }

    public synchronized int size() {
        return clans.size();
    }

    public ClanTagFormat display() {
        return display;
    }

    public ClanRights rights() {
        return rights;
    }

    public void display(ClanTagFormat format) {
        if (format != null && !format.equals(display)) {
            display = format;
            listener.run();
        }
    }

    public void onChange(Runnable change) {
        this.listener = change == null ? () -> {
        } : change;
    }

    public void receive(JsonObject message, Predicate<UUID> here) {
        if (message.has("display")) {
            display = ClanCodec.decodeDisplay(message.get("display"));
        }
        if (message.has("rights")) {
            rights = ClanCodec.decodeRights(message.get("rights"));
        }
        UUID id = uuid(message.get("id"));
        if (id != null) {
            Clan clan = ClanCodec.decode(message.get("clan")).orElse(null);
            apply(id, clan, removed(message.get("removed")), flag(message.get("dissolved")) || clan == null, here);
        }
        listener.run();
    }

    public synchronized void apply(UUID id, Clan clan, Set<UUID> removed, boolean dissolved, Predicate<UUID> here) {
        for (UUID member : removed) {
            byMember.remove(member, id);
        }
        byMember.values().removeIf(id::equals);
        if (dissolved || clan == null || clan.memberIds().stream().noneMatch(here)) {
            clans.remove(id);
            return;
        }
        clans.put(id, clan);
        for (UUID member : clan.memberIds()) {
            UUID previous = byMember.put(member, id);
            if (previous != null && !previous.equals(id) && byMember.values().stream().noneMatch(previous::equals)) {
                clans.remove(previous);
            }
        }
    }

    public synchronized void forget(UUID player, Predicate<UUID> here) {
        UUID id = byMember.get(player);
        if (id == null) {
            return;
        }
        Clan clan = clans.get(id);
        if (clan == null || clan.memberIds().stream().noneMatch(member -> !member.equals(player) && here.test(member))) {
            clans.remove(id);
            byMember.values().removeIf(id::equals);
        }
    }

    private static Set<UUID> removed(JsonElement element) {
        Set<UUID> removed = new LinkedHashSet<>();
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(entry -> {
                UUID uuid = uuid(entry);
                if (uuid != null) {
                    removed.add(uuid);
                }
            });
        }
        return removed;
    }

    private static boolean flag(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static UUID uuid(JsonElement element) {
        try {
            String raw = element != null && element.isJsonPrimitive() ? element.getAsString() : "";
            return raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
