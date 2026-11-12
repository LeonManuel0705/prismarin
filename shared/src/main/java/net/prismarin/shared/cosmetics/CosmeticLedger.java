package net.prismarin.shared.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.cosmetics.OwnedCosmetic;
import net.prismarin.api.cosmetics.PlayerCosmetics;
import net.prismarin.shared.storage.DocumentStore;

public final class CosmeticLedger {

    public static final String COLLECTION = "cosmetics";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Change(boolean changed, String previous, CosmeticSlot slot, PlayerCosmetics state) {

        public Optional<String> previousId() {
            return Optional.ofNullable(previous);
        }
    }

    private static final class Stored {

        String player;
        String name;
        Map<String, Owned> owned;
        Map<String, String> equipped;
        boolean hidden;
    }

    private static final class Owned {

        String source;
        long at;
    }

    private record Step(boolean changed, String previous, CosmeticSlot slot) {

        static final Step NONE = new Step(false, null, null);
    }

    private final DocumentStore documents;
    private final Clock clock;

    public CosmeticLedger(DocumentStore documents, Clock clock) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PlayerCosmetics state(UUID player) {
        return state(decode(documents.find(COLLECTION, player.toString()).orElse(null), player), player);
    }

    public Change grant(UUID player, String name, String id, CosmeticSource source) {
        return change(player, name, stored -> {
            if (stored.owned.containsKey(id)) {
                return Step.NONE;
            }
            Owned owned = new Owned();
            owned.source = (source == null ? CosmeticSource.PLUGIN : source).id();
            owned.at = clock.millis();
            stored.owned.put(id, owned);
            return new Step(true, null, null);
        });
    }

    public Change revoke(UUID player, String id) {
        return change(player, null, stored -> {
            if (stored.owned.remove(id) == null) {
                return Step.NONE;
            }
            CosmeticSlot slot = null;
            for (Map.Entry<String, String> entry : Map.copyOf(stored.equipped).entrySet()) {
                if (entry.getValue().equals(id)) {
                    stored.equipped.remove(entry.getKey());
                    slot = CosmeticSlot.parse(entry.getKey()).orElse(null);
                }
            }
            return new Step(true, slot == null ? null : id, slot);
        });
    }

    public Change equip(UUID player, Cosmetic cosmetic) {
        String key = cosmetic.slot().id();
        return change(player, null, stored -> {
            String previous = stored.equipped.get(key);
            if (cosmetic.id().equals(previous)) {
                return new Step(false, previous, cosmetic.slot());
            }
            stored.equipped.put(key, cosmetic.id());
            return new Step(true, previous, cosmetic.slot());
        });
    }

    public Change unequip(UUID player, CosmeticSlot slot) {
        return change(player, null, stored -> {
            String previous = stored.equipped.remove(slot.id());
            return previous == null ? Step.NONE : new Step(true, previous, slot);
        });
    }

    public Change hide(UUID player, boolean hidden) {
        return change(player, null, stored -> {
            if (stored.hidden == hidden) {
                return Step.NONE;
            }
            stored.hidden = hidden;
            return new Step(true, null, null);
        });
    }

    private Change change(UUID player, String name, Function<Stored, Step> edit) {
        Objects.requireNonNull(player, "player");
        AtomicReference<Step> step = new AtomicReference<>(Step.NONE);
        AtomicReference<PlayerCosmetics> after = new AtomicReference<>();
        documents.update(COLLECTION, player.toString(), current -> {
            Stored stored = decode(current, player);
            Step result = edit.apply(stored);
            step.set(result);
            if (result.changed() && name != null && !name.isBlank()) {
                stored.name = name;
            }
            after.set(state(stored, player));
            return result.changed() ? GSON.toJson(stored) : null;
        });
        Step result = step.get();
        PlayerCosmetics state = after.get() == null ? state(player) : after.get();
        return new Change(result.changed(), result.previous(), result.slot(), state);
    }

    private static PlayerCosmetics state(Stored stored, UUID player) {
        Map<String, OwnedCosmetic> owned = new LinkedHashMap<>();
        stored.owned.forEach((id, entry) -> {
            if (Cosmetic.validId(id)) {
                CosmeticSource source = CosmeticSource.byId(entry == null ? null : entry.source)
                        .orElse(CosmeticSource.PLUGIN);
                owned.put(id, new OwnedCosmetic(id, source, entry == null ? 0L : entry.at));
            }
        });
        Map<CosmeticSlot, String> equipped = new LinkedHashMap<>();
        stored.equipped.forEach((slot, id) -> CosmeticSlot.parse(slot).ifPresent(parsed -> {
            if (Cosmetic.validId(id)) {
                equipped.put(parsed, id);
            }
        }));
        return new PlayerCosmetics(player, owned, equipped, stored.hidden);
    }

    private static Stored decode(String json, UUID player) {
        Stored stored = null;
        if (json != null && !json.isBlank()) {
            try {
                stored = GSON.fromJson(json, Stored.class);
            } catch (JsonParseException e) {
                stored = null;
            }
        }
        if (stored == null) {
            stored = new Stored();
        }
        stored.player = player.toString();
        stored.name = stored.name == null ? "" : stored.name;
        stored.owned = stored.owned == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored.owned);
        stored.equipped = stored.equipped == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored.equipped);
        stored.equipped.values().removeIf(id -> id == null);
        return stored;
    }
}
