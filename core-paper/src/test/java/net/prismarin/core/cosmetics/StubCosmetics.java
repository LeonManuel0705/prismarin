package net.prismarin.core.cosmetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticEffectKind;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticRenderer;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticService;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.cosmetics.CosmeticSpot;
import net.prismarin.api.cosmetics.CosmeticViewFilter;
import net.prismarin.api.cosmetics.OwnedCosmetic;
import net.prismarin.api.cosmetics.PlayerCosmetics;
import net.prismarin.api.event.Subscription;

final class StubCosmetics implements CosmeticService {

    final Map<String, Cosmetic> cosmetics = new LinkedHashMap<>();
    final List<CosmeticSlot> slots = new ArrayList<>();
    final List<CosmeticRarity> rarities = new ArrayList<>();
    final Map<UUID, PlayerCosmetics> states = new HashMap<>();
    final Set<String> permitted = ConcurrentHashMap.newKeySet();
    final List<String> unlocks = new ArrayList<>();

    StubCosmetics add(Cosmetic cosmetic) {
        cosmetics.put(cosmetic.id(), cosmetic);
        if (!slots.contains(cosmetic.slot())) {
            slots.add(cosmetic.slot());
        }
        return this;
    }

    @Override
    public List<Cosmetic> cosmetics() {
        return List.copyOf(cosmetics.values());
    }

    @Override
    public Optional<Cosmetic> cosmetic(String id) {
        return Optional.ofNullable(cosmetics.get(id));
    }

    @Override
    public List<CosmeticSlot> slots() {
        return List.copyOf(slots);
    }

    @Override
    public List<CosmeticRarity> rarities() {
        return List.copyOf(rarities);
    }

    @Override
    public Optional<CosmeticRarity> rarity(String id) {
        return rarities.stream().filter(rarity -> rarity.id().equals(id)).findFirst();
    }

    @Override
    public CompletableFuture<PlayerCosmetics> load(UUID player) {
        return CompletableFuture.completedFuture(state(player));
    }

    @Override
    public Optional<PlayerCosmetics> cached(UUID player) {
        return Optional.ofNullable(states.get(player));
    }

    @Override
    public boolean owns(UUID player, String id) {
        return state(player).owns(id) || permitted.contains(id);
    }

    @Override
    public Optional<Cosmetic> equipped(UUID player, CosmeticSlot slot) {
        return state(player).equipped(slot).flatMap(this::cosmetic);
    }

    @Override
    public CompletableFuture<CosmeticResult> purchase(UUID player, String id) {
        return CompletableFuture.completedFuture(CosmeticResult.of(CosmeticResult.Status.NOT_FOR_SALE));
    }

    @Override
    public CompletableFuture<CosmeticResult> unlock(UUID player, String id, CosmeticSource source) {
        unlocks.add(player + " " + id + " " + source.id());
        Cosmetic cosmetic = cosmetics.get(id);
        if (cosmetic == null) {
            return CompletableFuture.completedFuture(CosmeticResult.of(CosmeticResult.Status.UNKNOWN));
        }
        PlayerCosmetics state = state(player);
        if (state.owns(id)) {
            return CompletableFuture.completedFuture(CosmeticResult.of(CosmeticResult.Status.ALREADY_OWNED,
                    cosmetic));
        }
        Map<String, OwnedCosmetic> owned = new LinkedHashMap<>(state.owned());
        owned.put(id, new OwnedCosmetic(id, source, 0L));
        states.put(player, new PlayerCosmetics(player, owned, state.equipped(), state.hidesOthers()));
        return CompletableFuture.completedFuture(CosmeticResult.of(CosmeticResult.Status.DONE, cosmetic));
    }

    @Override
    public CompletableFuture<CosmeticResult> revoke(UUID player, String id) {
        return CompletableFuture.completedFuture(CosmeticResult.of(CosmeticResult.Status.NOT_OWNED));
    }

    @Override
    public CompletableFuture<CosmeticResult> equip(UUID player, String id) {
        PlayerCosmetics state = state(player);
        Cosmetic cosmetic = cosmetics.get(id);
        Map<CosmeticSlot, String> equipped = new LinkedHashMap<>(state.equipped());
        equipped.put(cosmetic.slot(), id);
        states.put(player, new PlayerCosmetics(player, state.owned(), equipped, state.hidesOthers()));
        return CompletableFuture.completedFuture(CosmeticResult.of(CosmeticResult.Status.DONE, cosmetic));
    }

    @Override
    public CompletableFuture<CosmeticResult> unequip(UUID player, CosmeticSlot slot) {
        return CompletableFuture.completedFuture(CosmeticResult.of(CosmeticResult.Status.NOT_EQUIPPED));
    }

    @Override
    public CompletableFuture<Boolean> hideOthers(UUID player, boolean hide) {
        PlayerCosmetics state = state(player);
        states.put(player, new PlayerCosmetics(player, state.owned(), state.equipped(), hide));
        return CompletableFuture.completedFuture(hide);
    }

    @Override
    public Subscription register(Cosmetic cosmetic) {
        add(cosmetic);
        return () -> cosmetics.remove(cosmetic.id());
    }

    @Override
    public Subscription registerShape(String shape, CosmeticRenderer renderer) {
        return () -> {
        };
    }

    @Override
    public Subscription addViewFilter(CosmeticViewFilter filter) {
        return () -> {
        };
    }

    @Override
    public boolean active(CosmeticSlot slot) {
        return true;
    }

    @Override
    public void enable(CosmeticSlot slot) {
    }

    @Override
    public void disable(CosmeticSlot slot) {
    }

    @Override
    public void reset(CosmeticSlot slot) {
    }

    @Override
    public boolean playEffect(UUID player, CosmeticSlot slot, CosmeticSpot spot) {
        return false;
    }

    @Override
    public boolean playEffect(UUID player, CosmeticSlot slot) {
        return false;
    }

    @Override
    public boolean playEffect(UUID player, Cosmetic cosmetic, CosmeticSpot spot) {
        return false;
    }

    @Override
    public int stopEffects(UUID player) {
        return 0;
    }

    @Override
    public boolean trail(UUID player, UUID projectile) {
        return false;
    }

    @Override
    public boolean effectEntity(UUID entity) {
        return false;
    }

    @Override
    public Subscription registerEffectKind(String kind, CosmeticEffectKind effect) {
        return () -> {
        };
    }

    @Override
    public boolean automatic(CosmeticSlot slot) {
        return false;
    }

    @Override
    public void setAutomatic(CosmeticSlot slot, boolean automatic) {
    }

    @Override
    public void resetAutomatic(CosmeticSlot slot) {
    }

    private PlayerCosmetics state(UUID player) {
        return states.getOrDefault(player, PlayerCosmetics.none(player));
    }
}
