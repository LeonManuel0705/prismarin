package net.prismarin.api.cosmetics;

import java.util.Locale;

public record CosmeticHat(String material, String texture) implements CosmeticLook {

    public static final String PLAYER_HEAD = "player_head";

    public CosmeticHat {
        material = material == null ? "" : material.strip().toLowerCase(Locale.ROOT);
        if (material.startsWith("minecraft:")) {
            material = material.substring("minecraft:".length());
        }
        texture = texture == null ? "" : texture.strip();
        if (material.isEmpty() && texture.isEmpty()) {
            throw new IllegalArgumentException("a hat needs an item or a head texture");
        }
        if (material.isEmpty()) {
            material = PLAYER_HEAD;
        }
    }

    public static CosmeticHat item(String material) {
        return new CosmeticHat(material, "");
    }

    public static CosmeticHat head(String texture) {
        return new CosmeticHat(PLAYER_HEAD, texture);
    }

    public boolean head() {
        return !texture.isEmpty();
    }
}
