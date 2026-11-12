package net.prismarin.api.cosmetics;

import java.util.Optional;
import java.util.UUID;

public final class CosmeticApi {

    private static volatile CosmeticService service;

    private CosmeticApi() {
        throw new AssertionError();
    }

    public static Optional<CosmeticService> service() {
        return Optional.ofNullable(service);
    }

    public static boolean effectEntity(UUID entity) {
        CosmeticService current = service;
        return current != null && entity != null && current.effectEntity(entity);
    }

    public static void register(CosmeticService cosmetics) {
        service = cosmetics;
    }

    public static void unregister() {
        service = null;
    }
}
