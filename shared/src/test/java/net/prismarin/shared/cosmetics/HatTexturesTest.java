package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HatTexturesTest {

    private static final String HASH = "5e7899b4806858697e283f084d9173fe487886453774626b24bd8cfecc77b3f";

    @Test
    void readsATextureValueAUrlOrAHash() {
        String value = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":"
                + "\"http://textures.minecraft.net/texture/" + HASH + "\"}}}").getBytes(StandardCharsets.UTF_8));

        assertEquals(Optional.of(HASH), HatTextures.hash(value));
        assertEquals(Optional.of(HASH), HatTextures.hash("https://textures.minecraft.net/texture/" + HASH));
        assertEquals(Optional.of(HASH), HatTextures.hash(" " + HASH.toUpperCase() + " "));
    }

    @Test
    void refusesEverythingElse() {
        assertEquals(Optional.empty(), HatTextures.hash(""));
        assertEquals(Optional.empty(), HatTextures.hash(null));
        assertEquals(Optional.empty(), HatTextures.hash("https://example.com/texture/" + HASH));
        assertEquals(Optional.empty(), HatTextures.hash("not a texture"));
        assertEquals(Optional.empty(), HatTextures.hash(Base64.getEncoder()
                .encodeToString("{\"textures\":{}}".getBytes(StandardCharsets.UTF_8))));
    }
}
