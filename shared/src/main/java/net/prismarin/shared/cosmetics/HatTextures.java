package net.prismarin.shared.cosmetics;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.prismarin.shared.skin.SkinTextures;

public final class HatTextures {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{16,128}");

    private HatTextures() {
        throw new AssertionError();
    }

    public static Optional<String> hash(String texture) {
        if (texture == null || texture.isBlank()) {
            return Optional.empty();
        }
        String value = texture.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        if (HASH.matcher(lower).matches()) {
            return Optional.of(lower);
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return SkinTextures.hash(value);
        }
        return SkinTextures.parse(value).map(SkinTextures.Skin::hash);
    }
}
