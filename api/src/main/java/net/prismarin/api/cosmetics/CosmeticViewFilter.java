package net.prismarin.api.cosmetics;

import java.util.UUID;

@FunctionalInterface
public interface CosmeticViewFilter {

    boolean shows(UUID viewer, UUID wearer, CosmeticSlot slot);
}
