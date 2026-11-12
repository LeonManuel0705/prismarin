package net.prismarin.shared.cosmetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticPrice;
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.event.Subscription;

public final class CosmeticCatalog {

    private static final CosmeticRarity FALLBACK_RARITY = CosmeticRarity.of(CosmeticRarity.COMMON, "Common",
            0xA3A3A3, 0);

    private volatile Map<String, Cosmetic> configured = Map.of();
    private volatile Map<CosmeticSlot, CosmeticCategory> categories = Map.of();
    private volatile Map<String, CosmeticRarity> rarities = Map.of();
    private final Map<String, Cosmetic> registered = new LinkedHashMap<>();

    public synchronized void load(List<Cosmetic> cosmetics, List<CosmeticCategory> categoryList,
                                  List<CosmeticRarity> rarityList) {
        Map<String, Cosmetic> byId = new LinkedHashMap<>();
        for (Cosmetic cosmetic : cosmetics) {
            byId.putIfAbsent(cosmetic.id(), cosmetic);
        }
        Map<CosmeticSlot, CosmeticCategory> bySlot = new LinkedHashMap<>();
        for (CosmeticCategory category : categoryList) {
            bySlot.putIfAbsent(category.slot(), category);
        }
        Map<String, CosmeticRarity> byRarity = new LinkedHashMap<>();
        rarityList.stream().sorted(Comparator.comparingInt(CosmeticRarity::order))
                .forEach(rarity -> byRarity.putIfAbsent(rarity.id(), rarity));
        configured = Map.copyOf(byId);
        categories = Map.copyOf(bySlot);
        rarities = Map.copyOf(byRarity);
    }

    public synchronized Subscription register(Cosmetic cosmetic) {
        Objects.requireNonNull(cosmetic, "cosmetic");
        if (configured.containsKey(cosmetic.id()) || registered.containsKey(cosmetic.id())) {
            throw new IllegalArgumentException("the cosmetic " + cosmetic.id() + " already exists");
        }
        registered.put(cosmetic.id(), cosmetic);
        return () -> unregister(cosmetic);
    }

    public Optional<Cosmetic> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Cosmetic found = configured.get(id);
        if (found != null) {
            return Optional.of(found);
        }
        synchronized (this) {
            return Optional.ofNullable(registered.get(id));
        }
    }

    public List<Cosmetic> all() {
        List<Cosmetic> all = new ArrayList<>(configured.values());
        synchronized (this) {
            registered.values().stream().filter(cosmetic -> !configured.containsKey(cosmetic.id())).forEach(all::add);
        }
        all.sort(order());
        return List.copyOf(all);
    }

    public List<Cosmetic> of(CosmeticSlot slot) {
        return all().stream().filter(cosmetic -> cosmetic.slot().equals(slot)).toList();
    }

    public List<Cosmetic> shown() {
        return all().stream().filter(cosmetic -> enabled(cosmetic.slot())).toList();
    }

    public CosmeticCategory category(CosmeticSlot slot) {
        CosmeticCategory category = categories.get(slot);
        return category != null ? category : CosmeticCategory.fallback(slot);
    }

    public boolean enabled(CosmeticSlot slot) {
        return category(slot).enabled();
    }

    public List<CosmeticCategory> categories() {
        Set<CosmeticSlot> slots = new LinkedHashSet<>(categories.keySet());
        all().forEach(cosmetic -> slots.add(cosmetic.slot()));
        List<CosmeticSlot> used = all().stream().map(Cosmetic::slot).distinct().toList();
        return slots.stream()
                .map(this::category)
                .filter(CosmeticCategory::enabled)
                .filter(category -> used.contains(category.slot()))
                .sorted(Comparator.comparingInt(CosmeticCategory::order)
                        .thenComparing(category -> category.slot().id()))
                .toList();
    }

    public List<CosmeticRarity> rarities() {
        return rarities.values().stream().sorted(Comparator.comparingInt(CosmeticRarity::order)).toList();
    }

    public Optional<CosmeticRarity> rarity(String id) {
        return Optional.ofNullable(id == null ? null : rarities.get(id));
    }

    public CosmeticRarity rarityOf(Cosmetic cosmetic) {
        CosmeticRarity rarity = rarities.get(cosmetic.rarity());
        if (rarity != null) {
            return rarity;
        }
        return rarities().stream().findFirst().orElse(FALLBACK_RARITY);
    }

    private Comparator<Cosmetic> order() {
        return Comparator.comparingInt(Cosmetic::order)
                .thenComparingInt(cosmetic -> rarityOf(cosmetic).order())
                .thenComparingInt(cosmetic -> cosmetic.purchasable() ? 0 : 1)
                .thenComparing(cosmetic -> cosmetic.priceTag().map(CosmeticPrice::currency).orElse(""))
                .thenComparingLong(cosmetic -> cosmetic.priceTag().map(CosmeticPrice::amount).orElse(0L))
                .thenComparing(Cosmetic::id);
    }

    private synchronized void unregister(Cosmetic cosmetic) {
        registered.remove(cosmetic.id(), cosmetic);
    }
}
