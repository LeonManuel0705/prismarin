package net.prismarin.shared.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkinTexturesTest {

    static final String HASH = "a3f1c0ffee5e7f0e2d9b8c7a6b5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d";

    @Test
    void theTexturesPropertyNamesTheSkin() {
        String value = SkinTextures.encode("http://textures.minecraft.net/texture/" + HASH, false);
        SkinTextures.Skin skin = SkinTextures.parse(value).orElseThrow();
        assertEquals(HASH, skin.hash());
        assertFalse(skin.slim());
        assertEquals(URI.create("https://textures.minecraft.net/texture/" + HASH), skin.uri());
        assertTrue(SkinTextures.parse(SkinTextures.encode("https://textures.minecraft.net/texture/"
                + HASH.toUpperCase(Locale.ROOT), true)).orElseThrow().slim());
    }

    @Test
    void onlyThePublicTextureServerIsAccepted() {
        for (String url : new String[]{"https://evil.example/texture/" + HASH,
                "https://textures.minecraft.net.evil.example/texture/" + HASH,
                "https://textures.minecraft.net/texture/" + HASH + "?player=Lea",
                "https://user@textures.minecraft.net/texture/" + HASH,
                "https://textures.minecraft.net:8443/texture/" + HASH,
                "https://textures.minecraft.net/other/" + HASH,
                "https://textures.minecraft.net/texture/../" + HASH,
                "file:///etc/passwd",
                "ftp://textures.minecraft.net/texture/" + HASH}) {
            assertEquals(Optional.empty(), SkinTextures.parse(SkinTextures.encode(url, false)), url);
        }
        assertTrue(SkinTextures.allowed(SkinTextures.uri(HASH)));
        assertFalse(SkinTextures.allowed(URI.create("https://example.com/texture/" + HASH)));
        assertThrows(IllegalArgumentException.class, () -> SkinTextures.uri("../../x"));
    }

    @Test
    void brokenPropertiesHaveNoSkin() {
        assertEquals(Optional.empty(), SkinTextures.parse(null));
        assertEquals(Optional.empty(), SkinTextures.parse(""));
        assertEquals(Optional.empty(), SkinTextures.parse("%%% not base64 %%%"));
        assertEquals(Optional.empty(), SkinTextures.parse(Base64.getEncoder().encodeToString(
                "{\"textures\":{}}".getBytes(StandardCharsets.UTF_8))));
        assertEquals(Optional.empty(), SkinTextures.parse(Base64.getEncoder().encodeToString(
                "[1,2]".getBytes(StandardCharsets.UTF_8))));
        assertEquals(Optional.empty(), SkinTextures.parse("x".repeat(9_000)));
    }
}
