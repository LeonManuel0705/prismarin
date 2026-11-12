package net.prismarin.core.cosmetics;

import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticService;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.reward.RewardContext;
import net.prismarin.api.reward.RewardHandler;
import net.prismarin.api.reward.RewardHandlers;

public final class CosmeticReward implements RewardHandler {

    public static final String ID = "cosmetic";

    private final Supplier<CosmeticService> service;
    private final BiConsumer<UUID, CosmeticResult> tell;

    public CosmeticReward(Supplier<CosmeticService> service, BiConsumer<UUID, CosmeticResult> tell) {
        this.service = service;
        this.tell = tell;
    }

    public Subscription register(RewardHandlers handlers) {
        return handlers.register(ID, this);
    }

    @Override
    public void give(RewardContext context, String value) {
        String id = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        UUID player = context.player();
        service.get().unlock(player, id, CosmeticSource.REWARD).thenAccept(result -> tell.accept(player, result));
    }
}
