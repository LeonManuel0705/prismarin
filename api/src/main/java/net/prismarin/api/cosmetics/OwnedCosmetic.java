package net.prismarin.api.cosmetics;

import java.util.Objects;

public record OwnedCosmetic(String id, CosmeticSource source, long at) {

    public OwnedCosmetic {
        Objects.requireNonNull(id, "id");
        source = source == null ? CosmeticSource.PLUGIN : source;
    }
}
