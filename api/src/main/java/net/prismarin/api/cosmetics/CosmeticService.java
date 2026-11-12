package net.prismarin.api.cosmetics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.event.Subscription;

public interface CosmeticService {

    String PERMISSION_ALL = "prismarin.cosmetics.all";

    List<Cosmetic> cosmetics();

    Optional<Cosmetic> cosmetic(String id);

    default List<Cosmetic> cosmetics(CosmeticSlot slot) {
        return cosmetics().stream().filter(cosmetic -> cosmetic.slot().equals(slot)).toList();
    }

    List<CosmeticSlot> slots();

    List<CosmeticRarity> rarities();

    Optional<CosmeticRarity> rarity(String id);

    CompletableFuture<PlayerCosmetics> load(UUID player);

    Optional<PlayerCosmetics> cached(UUID player);

    boolean owns(UUID player, String id);

    Optional<Cosmetic> equipped(UUID player, CosmeticSlot slot);

    CompletableFuture<CosmeticResult> purchase(UUID player, String id);

    CompletableFuture<CosmeticResult> unlock(UUID player, String id, CosmeticSource source);

    default CompletableFuture<CosmeticResult> unlock(UUID player, String id) {
        return unlock(player, id, CosmeticSource.PLUGIN);
    }

    CompletableFuture<CosmeticResult> revoke(UUID player, String id);

    CompletableFuture<CosmeticResult> equip(UUID player, String id);

    CompletableFuture<CosmeticResult> unequip(UUID player, CosmeticSlot slot);

    CompletableFuture<Boolean> hideOthers(UUID player, boolean hide);

    Subscription register(Cosmetic cosmetic);

    Subscription registerShape(String shape, CosmeticRenderer renderer);

    Subscription addViewFilter(CosmeticViewFilter filter);

    Subscription registerEffectKind(String kind, CosmeticEffectKind effect);

    boolean active(CosmeticSlot slot);

    void enable(CosmeticSlot slot);

    void disable(CosmeticSlot slot);

    void reset(CosmeticSlot slot);

    boolean playEffect(UUID player, CosmeticSlot slot, CosmeticSpot spot);

    boolean playEffect(UUID player, CosmeticSlot slot);

    boolean playEffect(UUID player, Cosmetic cosmetic, CosmeticSpot spot);

    int stopEffects(UUID player);

    boolean trail(UUID player, UUID projectile);

    boolean effectEntity(UUID entity);

    boolean automatic(CosmeticSlot slot);

    void setAutomatic(CosmeticSlot slot, boolean automatic);

    void resetAutomatic(CosmeticSlot slot);
}
